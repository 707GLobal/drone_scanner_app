package com.global_707.drone_scanner.data.airspace

import android.content.Context
import android.net.Uri
import com.global_707.drone_scanner.data.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * 离线数据包的发现、读取、导入与移除。
 *
 * 位置优先级：
 *  1. 用户导入：`filesDir/airspace/`（通过系统文件选择器导入 zip，本机私有目录，无需存储权限）
 *  2. 内置包：`assets/airspace/`（可选，随 APK 发布；不内置时图层显示"未安装"）
 *
 * 运行期**不发任何网络请求**，也不含鉴权信息——这是本图层当前的设计前提。
 *
 * 包结构：
 * ```
 * airspace/
 *   manifest.json
 *   suitable/4/13/6.png
 * ```
 */
object AirspacePack {

    private const val ASSETS_ROOT = "airspace"
    private const val FILES_ROOT = "airspace"
    private const val MANIFEST_NAME = "manifest.json"

    /** 解压安全上限：条目数、单文件、总解压体积 */
    private const val MAX_ENTRIES = 50_000
    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 200L * 1024 * 1024

    /** 已安装的数据包 */
    data class Installed(
        val manifest: AirspaceManifest,
        val source: AirspaceSourceKind,
        /** 导入包的文件根目录；内置包为 null（改从 assets 读） */
        val filesRoot: File?,
        /** 安装态指纹：包内容变化时变化，用于让瓦片缓存与已建图层失效 */
        val signature: String,
    )

    fun filesRoot(context: Context): File = File(context.filesDir, FILES_ROOT)

    /**
     * 读取当前安装态；返回 null 表示未安装。
     * 包存在但 manifest 非法时抛 [AirspaceException]（让 UI 显示"数据损坏"）。
     *
     * **必须在 IO 线程调用**（会读文件）。
     */
    fun installed(context: Context): Installed? {
        val root = filesRoot(context)
        val manifestFile = File(root, MANIFEST_NAME)
        if (manifestFile.isFile) {
            val manifest = parseManifest(manifestFile.readText())
            return Installed(
                manifest = manifest,
                source = AirspaceSourceKind.IMPORTED,
                filesRoot = root,
                // 目录 mtime 在导入替换后一定变化；版本号变化也一定变化
                signature = "files:${manifest.version}:${root.lastModified()}",
            )
        }
        if (hasAssetsManifest(context)) {
            val manifest = parseManifest(readAssetText(context, "$ASSETS_ROOT/$MANIFEST_NAME"))
            return Installed(
                manifest = manifest,
                source = AirspaceSourceKind.BUNDLED,
                filesRoot = null,
                signature = "assets:${manifest.version}",
            )
        }
        return null
    }

    /** 读取一张瓦片；文件不存在返回 null（表示该区域无数据，不是错误） */
    fun openTile(
        context: Context,
        installed: Installed,
        layerDir: String,
        z: Int,
        x: Int,
        y: Int,
    ): ByteArray? {
        val relative = "$layerDir/$z/$x/$y.png"
        return try {
            val filesRoot = installed.filesRoot
            if (filesRoot != null) {
                val file = File(filesRoot, relative)
                if (!file.isFile) return null
                if (file.length() > MAX_ENTRY_BYTES) {
                    AppLogger.w("Airspace", "tile_too_large path=$relative size=${file.length()}")
                    return null
                }
                file.readBytes()
            } else {
                context.assets.open("$ASSETS_ROOT/$relative").use { it.readBytes() }
            }
        } catch (e: IOException) {
            AppLogger.w("Airspace", "tile_read_failed path=$relative msg=${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            AppLogger.w("Airspace", "tile_read_oom path=$relative")
            null
        }
    }

    /**
     * 导入 zip 数据包：解压到临时目录 → 校验 manifest → 原子替换 `filesDir/airspace/`。
     * 校验失败时保留原有数据包不动。
     */
    suspend fun importZip(context: Context, uri: Uri): AirspaceManifest = withContext(Dispatchers.IO) {
        val tempRoot = File(context.cacheDir, "airspace-import")
        tempRoot.deleteRecursively()
        if (!tempRoot.mkdirs()) {
            throw AirspaceException(AirspaceException.Kind.IO, "无法创建临时目录")
        }
        try {
            unzip(context, uri, tempRoot)
            val packRoot = findPackRoot(tempRoot)
                ?: throw AirspaceException(
                    AirspaceException.Kind.MANIFEST_INVALID,
                    "包内未找到 $MANIFEST_NAME",
                )
            val manifest = parseManifest(File(packRoot, MANIFEST_NAME).readText())
            // 声明了图层就必须真的有目录，避免"装上了但什么都不显示"
            manifest.layers.forEach { layer ->
                val dir = File(packRoot, layer.dir)
                if (!dir.isDirectory) {
                    throw AirspaceException(
                        AirspaceException.Kind.MANIFEST_INVALID,
                        "缺少图层目录：${layer.dir}",
                    )
                }
            }

            val target = filesRoot(context)
            val backup = File(context.filesDir, "$FILES_ROOT.bak")
            if (target.exists()) {
                backup.deleteRecursively()
                if (!target.renameTo(backup)) target.deleteRecursively()
            }
            val moved = packRoot.renameTo(target)
            if (!moved) {
                packRoot.copyRecursively(target, overwrite = true)
                packRoot.deleteRecursively()
            }
            backup.deleteRecursively()
            AppLogger.i(
                "Airspace",
                "pack_imported version=${manifest.version} layers=${manifest.layers.size}",
            )
            manifest
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    /** 移除用户导入的包（内置包不受影响）；返回是否确实删除了内容 */
    fun remove(context: Context): Boolean {
        val root = filesRoot(context)
        if (!root.exists()) return false
        val deleted = root.deleteRecursively()
        AppLogger.i("Airspace", "pack_removed ok=$deleted")
        return deleted
    }

    // ---------- 内部实现 ----------

    private fun parseManifest(json: String): AirspaceManifest =
        runCatching { AirspaceManifest.parse(json) }
            .getOrElse { e ->
                AppLogger.e("Airspace", "manifest_invalid msg=${e.message}")
                throw if (e is AirspaceException) e else {
                    AirspaceException(AirspaceException.Kind.MANIFEST_INVALID, e.message, e)
                }
            }

    private fun hasAssetsManifest(context: Context): Boolean =
        runCatching { context.assets.open("$ASSETS_ROOT/$MANIFEST_NAME").close() }.isSuccess

    private fun readAssetText(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    /** 允许 zip 里带一层根目录（例如 `airspace/manifest.json`） */
    private fun findPackRoot(tempRoot: File): File? {
        if (File(tempRoot, MANIFEST_NAME).isFile) return tempRoot
        val children = tempRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (children.size == 1) {
            val child = children.first()
            if (File(child, MANIFEST_NAME).isFile) return child
        }
        return null
    }

    private fun unzip(context: Context, uri: Uri, destination: File) {
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: IOException) {
            throw AirspaceException(AirspaceException.Kind.IO, "无法读取所选文件", e)
        } ?: throw AirspaceException(AirspaceException.Kind.IO, "无法读取所选文件")

        stream.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entryCount = 0
                var totalBytes = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        throw AirspaceException(AirspaceException.Kind.MANIFEST_INVALID, "包内文件过多")
                    }
                    val name = entry.name.replace('\\', '/').removePrefix("./")
                    if (entry.isDirectory) {
                        File(destination, name).mkdirs()
                        zip.closeEntry()
                        continue
                    }
                    if (!AirspaceManifest.isSafeRelativePath(name)) {
                        throw AirspaceException(
                            AirspaceException.Kind.MANIFEST_INVALID,
                            "包内路径非法：$name",
                        )
                    }
                    val outFile = File(destination, name)
                    outFile.parentFile?.mkdirs()
                    var entryBytes = 0L
                    outFile.outputStream().use { out ->
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > MAX_ENTRY_BYTES) {
                                throw AirspaceException(
                                    AirspaceException.Kind.MANIFEST_INVALID,
                                    "单个文件过大：$name",
                                )
                            }
                            if (totalBytes > MAX_TOTAL_BYTES) {
                                throw AirspaceException(
                                    AirspaceException.Kind.MANIFEST_INVALID,
                                    "解压后体积超过上限",
                                )
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }
}
