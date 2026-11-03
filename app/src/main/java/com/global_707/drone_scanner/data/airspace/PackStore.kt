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
 * 多个离线数据包的存储与管理（**纯本地，无网络、无鉴权**）。
 *
 * 目录结构：
 * ```
 * filesDir/airspace/packs/<packId>/
 *   manifest.json
 *   suitable/3/13/6.png        ← 每个图层一个目录
 * ```
 * - 导入是"追加"，不覆盖已有包；包 id 由导入时间生成（稳定、可作缓存键）；
 * - 每个图层在「图层设置」里独立选择使用哪个包（见 AppPrefs.airspacePackByLayer）；
 * - 同一 `packId` 下所有图层共享目录，删除包会连带删除其中所有图层的数据。
 *
 * 旧版本（1.1.0）用的是"单包"目录 `filesDir/airspace/manifest.json`，
 * 启动时由 [migrateLegacy] 自动迁移为 `packs/legacy-<ts>/`。
 */
object PackStore {

    private const val ROOT = "airspace"
    private const val PACKS_DIR = "packs"
    private const val MANIFEST = "manifest.json"

    /** 解压安全上限 */
    private const val MAX_ENTRIES = 80_000
    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 300L * 1024 * 1024

    data class Pack(
        val id: String,
        val dir: File,
        val manifest: AirspaceManifest,
        val sizeBytes: Long,
    ) {
        val name: String get() = manifest.displayName
        val layerIds: List<String> get() = manifest.layers.map { it.id }
        fun hasLayer(layerId: String): Boolean = manifest.layer(layerId) != null
    }

    fun packsRoot(context: Context): File = File(context.filesDir, "$ROOT/$PACKS_DIR")

    /**
     * 旧版单包目录迁移：`filesDir/airspace/{manifest.json,<layer>/...}` → `packs/legacy-<ts>/`。
     * 幂等；没有旧包时什么也不做。须在 IO 线程调用。
     */
    fun migrateLegacy(context: Context) {
        val root = File(context.filesDir, ROOT)
        if (!File(root, MANIFEST).isFile) return
        val packs = packsRoot(context).apply { mkdirs() }
        val dest = File(packs, "legacy-${System.currentTimeMillis()}")
        if (!dest.mkdirs()) return
        var moved = 0
        root.listFiles()?.forEach { child ->
            if (child.name == PACKS_DIR) return@forEach
            if (child.renameTo(File(dest, child.name))) moved++
        }
        AppLogger.i("Airspace", "legacy_pack_migrated dir=${dest.name} entries=$moved")
    }

    /** 列出全部有效数据包（损坏的包只记日志并跳过）。须在 IO 线程调用。 */
    fun list(context: Context): List<Pack> {
        val root = packsRoot(context)
        val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val manifestFile = File(dir, MANIFEST)
            if (!manifestFile.isFile) {
                AppLogger.w("Airspace", "pack_skipped id=${dir.name} reason=no_manifest")
                return@mapNotNull null
            }
            runCatching { AirspaceManifest.parse(manifestFile.readText()) }
                .map { manifest -> Pack(dir.name, dir, manifest, dirSize(dir)) }
                .getOrElse { error ->
                    AppLogger.w("Airspace", "pack_skipped id=${dir.name} reason=${error.message}")
                    null
                }
        }.sortedBy { it.id }
    }

    /** 导入 zip：解压到临时目录 → 校验 manifest 与图层目录 → 移入 packs/<id>。 */
    suspend fun importZip(context: Context, uri: Uri): Pack = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val tempRoot = File(app.cacheDir, "airspace-import")
        tempRoot.deleteRecursively()
        if (!tempRoot.mkdirs()) throw AirspaceException(AirspaceException.Kind.IO, "无法创建临时目录")
        try {
            unzip(app, uri, tempRoot)
            val packRoot = findPackRoot(tempRoot)
                ?: throw AirspaceException(
                    AirspaceException.Kind.MANIFEST_INVALID,
                    "包内未找到 $MANIFEST",
                )
            val manifest = parseManifest(File(packRoot, MANIFEST).readText())
            manifest.layers.forEach { layer ->
                if (!File(packRoot, layer.dir).isDirectory) {
                    throw AirspaceException(
                        AirspaceException.Kind.MANIFEST_INVALID,
                        "缺少图层目录：${layer.dir}",
                    )
                }
            }
            val packs = packsRoot(app).apply { mkdirs() }
            var id = "p${System.currentTimeMillis()}"
            var target = File(packs, id)
            var suffix = 1
            while (target.exists()) {
                id = "p${System.currentTimeMillis()}-${suffix++}"
                target = File(packs, id)
            }
            val moved = packRoot.renameTo(target)
            if (!moved) {
                packRoot.copyRecursively(target, overwrite = true)
                packRoot.deleteRecursively()
            }
            AppLogger.i(
                "Airspace",
                "pack_imported id=$id name=${manifest.displayName} version=${manifest.version} layers=${manifest.layers.size}",
            )
            Pack(id, target, manifest, dirSize(target))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    fun delete(context: Context, packId: String): Boolean {
        val dir = File(packsRoot(context), packId)
        if (!dir.exists()) return false
        val deleted = dir.deleteRecursively()
        AppLogger.i("Airspace", "pack_deleted id=$packId ok=$deleted")
        return deleted
    }

    /** 读取瓦片；文件不存在返回 null（表示该区域无数据） */
    fun tileBytes(
        context: Context,
        pack: Pack,
        layerDir: String,
        z: Int,
        x: Int,
        y: Int,
    ): ByteArray? {
        val file = File(File(pack.dir, layerDir), "$z/$x/$y.png")
        if (!file.isFile) return null
        return try {
            if (file.length() > MAX_ENTRY_BYTES) {
                AppLogger.w("Airspace", "tile_too_large layer=$layerDir z=$z size=${file.length()}")
                null
            } else {
                file.readBytes()
            }
        } catch (e: IOException) {
            AppLogger.w("Airspace", "tile_read_failed layer=$layerDir z=$z msg=${e.message}")
            null
        }
    }

    /** 安装态指纹：包 id + 版本变化时用于失效瓦片缓存 */
    fun signature(context: Context): String =
        packsRoot(context).listFiles()?.joinToString("|") { it.name } ?: ""

    // ---------- 内部实现 ----------

    private fun parseManifest(json: String): AirspaceManifest =
        runCatching { AirspaceManifest.parse(json) }
            .getOrElse { e ->
                AppLogger.e("Airspace", "manifest_invalid msg=${e.message}")
                throw if (e is AirspaceException) e else {
                    AirspaceException(AirspaceException.Kind.MANIFEST_INVALID, e.message, e)
                }
            }

    private fun findPackRoot(tempRoot: File): File? {
        if (File(tempRoot, MANIFEST).isFile) return tempRoot
        val children = tempRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (children.size == 1) {
            val child = children.first()
            if (File(child, MANIFEST).isFile) return child
        }
        return null
    }

    private fun dirSize(dir: File): Long {
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
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
