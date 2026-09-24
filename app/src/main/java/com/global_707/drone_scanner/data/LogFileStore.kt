package com.global_707.drone_scanner.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日志写入目标 */
interface LogSink {
    fun write(text: String)
    fun flush()
    fun close()
}

/** 已打开的日志文件 */
class OpenedLog(val name: String, val sink: LogSink)

/** 日志文件的存储形态（决定读取/删除方式） */
enum class LogStorageKind { FILE, SAF, MEDIA }

/** 一个日志文件的引用（统计 / 清理用） */
class LogFileRef(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: LogStorageKind,
    val file: File?,
    val uri: Uri?,
)

/**
 * 日志文件存储。
 *
 * ## 为什么默认位置是公开目录，而不是应用私有目录
 *
 * 实测（Android 16 / MagicOS）结论：**Android 11+ 的文件管理器不允许浏览应用私有目录
 * （`Android/data` 及其子目录）**：
 * `ACTION_VIEW + vnd.android.document/directory` 会被系统「文件」忽略并停在它自己的默认位置
 * （下载）；SAF 选择器也会直接提示「无法使用此文件夹，为保护您的隐私，请选择其他文件夹」。
 * 因此把日志放在 `Android/data` 下，**无论怎么发意图都打不开文件夹**。
 *
 * 而公开目录 `Download/RIDScannerLogs/` 可以被系统「文件」正常导航（同样方式实测通过），
 * 且所有文件管理器、MTP 都能看到。所以：
 *
 *  - **API 29+**：默认写 `Download/RIDScannerLogs/`，通过 **MediaStore** 创建/读取/删除，
 *    **无需任何存储权限**（scoped storage 下也无法用 File API 写公开目录）
 *  - **API 26–28**：默认写 `getExternalFilesDir(null)/logs`（这些版本 `Android/data` 仍可被
 *    文件管理器浏览），无外部存储时回退 `filesDir/logs`
 *  - 用户仍可在设置里用 SAF 选自定义目录（会写入其下 `AppLogs/<包名>/`，避免污染用户目录）
 *
 * 文件命名：`log_yyyyMMdd_HHmmss_SSS.log`（不含冒号等不兼容字符）；同名冲突追加序号。
 * 注意：经 MediaStore 创建时，系统会按 MIME 类型再补一个扩展名（`text/plain` → 落盘
 * `log_...log.txt`），见 `isLogFileName`。
 */
object LogFileStore {

    private const val TAG = "LogFileStore"

    /** 日志文件命名规则（清理时据此判断"由本应用生成"） */
    const val FILE_PREFIX = "log_"
    const val FILE_SUFFIX = ".log"

    /** 自定义（SAF）目录下的一级子目录名，其下再按包名分目录 */
    const val SAF_ROOT_DIR = "AppLogs"

    /** 公开目录名：`Download/RIDScannerLogs` */
    const val PUBLIC_DIR_NAME = "RIDScannerLogs"

    /** 单文件上限（超过即轮转） */
    const val MAX_FILE_BYTES = 5 * 1024 * 1024L

    /** 系统 ExternalStorageProvider 的 authority（用于构造可被文件管理器导航的目录 URI） */
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** 外部存储主卷的文档 ID 前缀（ExternalStorageProvider 的约定） */
    private const val PRIMARY_VOLUME = "primary"

    /** MediaStore 在个别 ROM 上不可用时的降级标记 */
    @Volatile
    private var mediaStoreFailed = false

    // ---------------- 位置判定 ----------------

    /** 默认位置是否走公开目录（API 29+ 且 MediaStore 可用） */
    private fun usePublicDir(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !mediaStoreFailed

    /** 应用私有日志目录（API 26–28 的默认位置；公开目录不可用时的回退） */
    fun privateDir(context: Context): File? {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "logs")
        if (!dir.exists() && !dir.mkdirs()) return null
        return dir
    }

    /**
     * 是否为"本应用生成的日志文件"（统计与清理都以此为准）。
     *
     * **不能只判 `.log` 结尾**：走 MediaStore 创建的文件，系统会按 MIME 类型补扩展名——
     * 真机实测（Android 16）`text/plain` 的日志被存成
     * `log_20260924_142306_290.log.txt`。若按 `.log` 结尾过滤，列表恒为空，
     * 「日志总大小」永远 0 B、「清理日志」按钮恒被禁用（表现为清理功能失效）。
     * 故这里只要求：以 `log_` 开头、含 `.log`、且不是路径片段。
     */
    fun isLogFileName(name: String): Boolean =
        name.startsWith(FILE_PREFIX) &&
            name.contains(FILE_SUFFIX) &&
            !name.contains('/') &&
            !name.contains('\\')

    /** 展示用：默认位置 */
    fun defaultPath(context: Context): String =
        if (usePublicDir()) {
            "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIR_NAME"
        } else {
            privateDir(context)?.absolutePath ?: "—"
        }

    /** 展示用：当前生效的日志位置 */
    fun displayPath(context: Context): String {
        val tree = AppPrefs.logTreeUri
        if (tree.isNullOrBlank()) return defaultPath(context)
        val uri = runCatching { Uri.parse(tree) }.getOrNull() ?: return tree
        val rootName = queryString(context, uri, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return buildString {
            append(rootName ?: uri.toString())
            append('/').append(SAF_ROOT_DIR).append('/').append(context.packageName)
        }
    }

    /**
     * 日志**目录**的 URI，交给系统「文件」用 `ACTION_VIEW + 目录 MIME` 导航进去。
     *
     * 关键：**URI 必须来自能被文件管理器浏览的位置**（公开目录 / SAF 自定义目录）。
     * 应用私有目录（`Android/data`）在 Android 11+ 不可浏览，传了也会被忽略。
     */
    fun directoryViewUri(context: Context): Uri? {
        val tree = AppPrefs.logTreeUri
        if (!tree.isNullOrBlank()) {
            val treeUri = runCatching { Uri.parse(tree) }.getOrNull() ?: return null
            return ensureSafLogDir(context, treeUri)
        }
        return if (usePublicDir()) {
            DocumentsContract.buildDocumentUri(
                EXTERNAL_STORAGE_AUTHORITY,
                "$PRIMARY_VOLUME:${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIR_NAME",
            )
        } else {
            // API 26–28：Android/data 在这些版本仍可被文件管理器浏览
            val dir = privateDir(context) ?: return null
            val rel = dir.absolutePath.substringAfter("/storage/emulated/0/", "")
            if (rel.isEmpty()) return null
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "$PRIMARY_VOLUME:$rel")
        }
    }

    // ---------------- 打开新文件 ----------------

    fun openNew(context: Context, startedAtMs: Long): OpenedLog? {
        val tree = AppPrefs.logTreeUri
        if (!tree.isNullOrBlank()) {
            openNewInSaf(context, tree, startedAtMs)?.let { return it }
            // 自定义目录不可用时退回默认位置
        }
        if (usePublicDir()) {
            openNewInMediaStore(context, startedAtMs)?.let { return it }
            mediaStoreFailed = true
            Log.w(TAG, "MediaStore 创建失败，降级到应用私有目录")
        }
        return openNewInPrivateDir(context, startedAtMs)
    }

    private fun openNewInMediaStore(context: Context, startedAtMs: Long): OpenedLog? {
        val resolver = context.contentResolver
        val taken = listLogs(context).mapTo(HashSet()) { it.name }
        val name = uniqueName(startedAtMs) { taken.contains(it) }
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIR_NAME",
                )
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = resolver.openOutputStream(uri, "w") ?: return null
            OpenedLog(name, StreamSink(stream))
        } catch (e: Exception) {
            Log.w(TAG, "在公开目录创建日志失败: ${e.message}")
            null
        }
    }

    private fun openNewInPrivateDir(context: Context, startedAtMs: Long): OpenedLog? {
        val dir = privateDir(context) ?: return null
        val name = uniqueName(startedAtMs) { File(dir, it).exists() }
        return try {
            OpenedLog(name, StreamSink(FileOutputStream(File(dir, name), true)))
        } catch (e: Exception) {
            Log.w(TAG, "创建默认目录日志失败: ${e.message}")
            null
        }
    }

    private fun openNewInSaf(context: Context, tree: String, startedAtMs: Long): OpenedLog? {
        val resolver = context.contentResolver
        val treeUri = runCatching { Uri.parse(tree) }.getOrNull() ?: return null
        val dirUri = ensureSafLogDir(context, treeUri) ?: return null
        val name = uniqueName(startedAtMs) { safExists(context, dirUri, it) }
        return try {
            val uri = DocumentsContract.createDocument(resolver, dirUri, "text/plain", name)
                ?: return null
            val stream = resolver.openOutputStream(uri, "w") ?: return null
            OpenedLog(name, StreamSink(stream))
        } catch (e: Exception) {
            Log.w(TAG, "在自定义目录创建日志失败: ${e.message}")
            null
        }
    }

    /** 确保 `树根/AppLogs/<包名>/` 存在，返回该目录的文档 URI */
    private fun ensureSafLogDir(context: Context, treeUri: Uri): Uri? {
        val root = findOrCreateDir(context, treeUri, SAF_ROOT_DIR) ?: return null
        return findOrCreateDir(context, root, context.packageName)
    }

    /** 在 [parent] 下查找名为 [name] 的子目录，不存在则创建 */
    private fun findOrCreateDir(context: Context, parent: Uri, name: String): Uri? {
        val resolver = context.contentResolver
        try {
            val parentDocId = DocumentsContract.getDocumentId(parent)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentDocId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docName = cursor.getString(1)
                    val mime = cursor.getString(2)
                    if (docName == name && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
                    }
                }
            }
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(parent, parentDocId)
            return DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        } catch (e: Exception) {
            Log.w(TAG, "创建自定义目录子目录失败: ${e.message}")
            return null
        }
    }

    private fun safExists(context: Context, dirUri: Uri, name: String): Boolean = try {
        val docId = DocumentsContract.getDocumentId(dirUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, docId)
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(0) == name) found = true
            }
            found
        } ?: false
    } catch (_: Exception) {
        false
    }

    /** 生成不冲突的文件名：同名时在扩展名前追加 `_1`、`_2`… */
    private fun uniqueName(tsMs: Long, exists: (String) -> Boolean): String {
        val base = FILE_PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(tsMs))
        var name = "$base$FILE_SUFFIX"
        var index = 1
        while (exists(name)) {
            name = "${base}_$index$FILE_SUFFIX"
            index++
            if (index > 999) break
        }
        return name
    }

    /** 校验自定义目录是否真的可写（能解析/创建 `AppLogs/<包名>/`） */
    fun canWriteCustom(context: Context, treeUri: Uri): Boolean = try {
        ensureSafLogDir(context, treeUri) != null
    } catch (_: Exception) {
        false
    }

    // ---------------- 统计 / 清理 ----------------

    fun listLogs(context: Context): List<LogFileRef> {
        val tree = AppPrefs.logTreeUri
        if (!tree.isNullOrBlank()) return listSaf(context, tree)
        if (usePublicDir()) {
            val media = listMediaStore(context)
            if (media.isNotEmpty() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return media
        }
        return listPrivate(context)
    }

    private fun listMediaStore(context: Context): List<LogFileRef> {
        val out = ArrayList<LogFileRef>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        try {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIR_NAME%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    if (!isLogFileName(name)) continue
                    val size = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                    val modified = (if (cursor.isNull(3)) 0L else cursor.getLong(3)) * 1000
                    out.add(
                        LogFileRef(
                            name = name,
                            sizeBytes = size,
                            lastModified = modified,
                            kind = LogStorageKind.MEDIA,
                            file = null,
                            uri = ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                id,
                            ),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "查询公开目录日志失败: ${e.message}")
        }
        return out
    }

    private fun listPrivate(context: Context): List<LogFileRef> {
        val dir = privateDir(context) ?: return emptyList()
        val out = ArrayList<LogFileRef>()
        collectPrivate(dir, out)
        return out
    }

    private fun collectPrivate(dir: File, out: MutableList<LogFileRef>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectPrivate(child, out)
            } else if (isLogFileName(child.name)) {
                out.add(
                    LogFileRef(
                        name = child.name,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified(),
                        kind = LogStorageKind.FILE,
                        file = child,
                        uri = null,
                    ),
                )
            }
        }
    }

    private fun listSaf(context: Context, tree: String): List<LogFileRef> {
        val treeUri = runCatching { Uri.parse(tree) }.getOrNull() ?: return emptyList()
        val dir = ensureSafLogDir(context, treeUri) ?: return emptyList()
        val out = ArrayList<LogFileRef>()
        collectSaf(context, dir, out)
        return out
    }

    private fun collectSaf(context: Context, dir: Uri, out: MutableList<LogFileRef>) {
        val resolver = context.contentResolver
        try {
            val docId = DocumentsContract.getDocumentId(dir)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(dir, docId)
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    val size = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                    val modified = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val mime = cursor.getString(4)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(dir, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        collectSaf(context, childUri, out)
                    } else if (isLogFileName(name)) {
                        out.add(
                            LogFileRef(
                                name = name,
                                sizeBytes = size,
                                lastModified = modified,
                                kind = LogStorageKind.SAF,
                                file = null,
                                uri = childUri,
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "统计自定义目录日志失败: ${e.message}")
        }
    }

    fun totalBytes(context: Context): Long = listLogs(context).sumOf { it.sizeBytes }

    /**
     * 删除日志文件。只删除符合本应用命名规则（`log_*.log`）的文件；
     * 自定义目录下同样只动这些文件，避免误删用户其他文件。返回删除个数。
     */
    fun clear(context: Context): Int {
        var deleted = 0
        listLogs(context).forEach { ref ->
            val ok = try {
                when (ref.kind) {
                    LogStorageKind.FILE -> ref.file?.delete() ?: false
                    LogStorageKind.SAF -> ref.uri?.let {
                        DocumentsContract.deleteDocument(context.contentResolver, it)
                    } ?: false
                    LogStorageKind.MEDIA -> ref.uri?.let {
                        context.contentResolver.delete(it, null, null) > 0
                    } ?: false
                }
            } catch (e: Exception) {
                Log.w(TAG, "删除日志失败 ${ref.name}: ${e.message}")
                false
            }
            if (ok) deleted++
        }
        return deleted
    }

    // ---------------- 内部工具 ----------------

    private fun queryString(context: Context, uri: Uri, column: String): String? = try {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** 基于 OutputStream 的 Sink：线程安全交由 AppLogger 的锁保证 */
    private class StreamSink(stream: OutputStream) : LogSink {
        private val writer = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8))

        override fun write(text: String) {
            writer.write(text)
        }

        override fun flush() {
            writer.flush()
        }

        override fun close() {
            runCatching { writer.flush() }
            runCatching { writer.close() }
        }
    }
}
