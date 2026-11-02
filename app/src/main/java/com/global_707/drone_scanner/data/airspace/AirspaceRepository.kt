package com.global_707.drone_scanner.data.airspace

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.global_707.drone_scanner.data.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 空域图层仓库：负责"安装态 → 数据源 → 元信息"的组装与瓦片缓存。
 *
 * 设计要点：
 *  - 数据源按 layerId 一一对应（将来加禁飞区/限飞区各是独立的数据源实例）；
 *  - 安装态指纹（[AirspacePack.Installed.signature]）变化时清空瓦片缓存，避免残留旧数据；
 *  - [tileNow] 是同步接口，供腾讯 `TileProvider` 调用；只查内存缓存 + 读本地文件，不做网络。
 */
object AirspaceRepository {

    private const val TILE_CACHE_BYTES = 8 * 1024 * 1024

    private val tileCache = object : LruCache<String, ByteArray>(TILE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    @Volatile
    private var installed: AirspacePack.Installed? = null

    @Volatile
    private var sources: Map<String, AirspaceSource> = emptyMap()

    private val mutex = Mutex()

    /**
     * 重新读取安装态并发布到 [AirspaceState]（启动、导入、删除后调用）。
     * 失败只会把状态置为 ERROR，不会抛出到 UI。
     */
    suspend fun refresh(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val outcome = withContext(Dispatchers.IO) {
            runCatching { AirspacePack.installed(app) }
        }
        val newInstalled = outcome.getOrNull()
        if (outcome.isFailure || newInstalled == null) {
            installed = null
            sources = emptyMap()
            tileCache.evictAll()
            val error = outcome.exceptionOrNull()
            AirspaceState.publish(emptyMap(), error?.message)
            error?.let { AppLogger.e("Airspace", "refresh_failed msg=${it.message}", it) }
            return@withLock
        }
        if (installed?.signature != newInstalled.signature) tileCache.evictAll()
        installed = newInstalled
        val catalogs = buildCatalogs(newInstalled)
        sources = catalogs.mapValues { (_, catalog) -> LocalPackSource(app, newInstalled, catalog) }
        AirspaceState.publish(catalogs, null)
        AppLogger.i(
            "Airspace",
            "refresh_ok version=${newInstalled.manifest.version} layers=${catalogs.keys} source=${newInstalled.source}",
        )
    }

    /**
     * 导入离线包并立即刷新（UI 只调这一个方法）。
     * 失败时原数据包保持不变，由 UI 提示错误原因。
     */
    suspend fun importAndRefresh(context: Context, uri: Uri): Result<AirspaceManifest> {
        val app = context.applicationContext
        val result = withContext(Dispatchers.IO) {
            runCatching { AirspacePack.importZip(app, uri) }
        }
        if (result.isSuccess) refresh(app)
        return result
    }

    /** 移除用户导入的数据包并刷新状态 */
    suspend fun removeAndRefresh(context: Context): Boolean {
        val app = context.applicationContext
        val removed = withContext(Dispatchers.IO) { AirspacePack.remove(app) }
        invalidate()
        refresh(app)
        return removed
    }

    fun source(layerId: String): AirspaceSource? = sources[layerId]

    /** 同步取瓦片（内存缓存 → 本地文件）。返回 null 表示该瓦片无数据。 */
    fun tileNow(source: AirspaceSource, z: Int, x: Int, y: Int): ByteArray? {
        val key = "${source.layerId}/$z/$x/$y"
        tileCache.get(key)?.let { return it }
        val bytes = source.tile(z, x, y) ?: return null
        if (bytes.isNotEmpty()) tileCache.put(key, bytes)
        return bytes
    }

    /** 清空缓存与数据源（移除数据包时调用） */
    fun invalidate() {
        installed = null
        sources = emptyMap()
        tileCache.evictAll()
    }

    private fun buildCatalogs(installedPack: AirspacePack.Installed): Map<String, AirspaceCatalog> {
        val manifest = installedPack.manifest
        val catalogs = LinkedHashMap<String, AirspaceCatalog>()
        manifest.layers.forEach { layer ->
            val spec = AirspaceLayers.spec(layer.id)
            if (spec == null) {
                // 数据包里有未注册的图层：只记日志、不显示（不猜中文标题与配色）
                AppLogger.w("Airspace", "unknown_layer id=${layer.id} version=${manifest.version}")
                return@forEach
            }
            catalogs[layer.id] = AirspaceCatalog(
                layerId = layer.id,
                version = manifest.version,
                zooms = layer.zooms,
                tileCrs = manifest.crs,
                tileSize = manifest.tileSize,
                style = manifest.style ?: spec.style,
                attribution = manifest.attribution,
                source = installedPack.source,
                generatedAtMillis = manifest.generatedAtMillis,
                dir = layer.dir,
                bbox = manifest.bbox,
            )
        }
        return catalogs
    }
}
