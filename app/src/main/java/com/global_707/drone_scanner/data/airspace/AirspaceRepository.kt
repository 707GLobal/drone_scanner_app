package com.global_707.drone_scanner.data.airspace

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.global_707.drone_scanner.data.AppLogger
import com.global_707.drone_scanner.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 空域图层仓库：把"离线包 + 每层选包偏好"解析成每个图层的数据源与元信息。
 *
 * 规则（与产品约定一致）：
 *  - **每个图层独立选择数据包**（`AppPrefs.airspacePackByLayer`）；
 *  - 该层未选包时自动选"第一个包含该层的包"（导入即用），之后尊重用户选择；
 *  - 没有可用包且处于演示模式时，退化为 [DemoAirspaceSource]（菜单显示「演示」）；
 *  - 瓦片缓存键包含 `packId/layerId`，换包/删包即失效。
 */
object AirspaceRepository {

    private const val TILE_CACHE_BYTES = 12 * 1024 * 1024

    private val tileCache = object : LruCache<String, ByteArray>(TILE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    @Volatile
    private var packs: List<PackStore.Pack> = emptyList()

    @Volatile
    private var sources: Map<String, AirspaceSource> = emptyMap()

    private val mutex = Mutex()

    /** 重新读取安装态与选包，并发布到 [AirspaceState]（启动、导入、删除、切换演示模式后调用） */
    suspend fun refresh(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                PackStore.migrateLegacy(app)
                PackStore.list(app)
            }
        }
        val loaded = outcome.getOrElse { error ->
            packs = emptyList()
            sources = emptyMap()
            tileCache.evictAll()
            AirspaceState.publish(emptyMap(), emptyList(), error.message)
            AppLogger.e("Airspace", "refresh_failed msg=${error.message}", error)
            return@withLock
        }
        tileCache.evictAll()
        packs = loaded

        val catalogs = LinkedHashMap<String, AirspaceCatalog>()
        val newSources = LinkedHashMap<String, AirspaceSource>()
        AirspaceLayers.all.forEach { spec ->
            val pack = resolvePack(spec)
            if (pack != null) {
                val layer = pack.manifest.layer(spec.id) ?: return@forEach
                val catalog = catalogOf(spec, pack, layer)
                catalogs[spec.id] = catalog
                newSources[spec.id] = LocalPackSource(app, pack, catalog)
            } else if (AppPrefs.demoMode) {
                val catalog = demoCatalog(spec)
                catalogs[spec.id] = catalog
                newSources[spec.id] = DemoAirspaceSource(catalog)
            }
        }
        sources = newSources
        AirspaceState.publish(
            catalogs = catalogs,
            packs = loaded.map { pack ->
                PackSummary(
                    id = pack.id,
                    name = pack.name,
                    version = pack.manifest.version,
                    sizeBytes = pack.sizeBytes,
                    layerIds = pack.layerIds,
                )
            },
            errorMessage = null,
        )
        AppLogger.i(
            "Airspace",
            "refresh_ok packs=${loaded.size} layers=${catalogs.keys} demo=${AppPrefs.demoMode}",
        )
    }

    /** 当前全部有效数据包（供图层设置页展示） */
    fun packs(): List<PackStore.Pack> = packs

    fun source(layerId: String): AirspaceSource? = sources[layerId]

    /** 同步取瓦片（内存缓存 → 本地文件）；返回 null 表示该瓦片无数据 */
    fun tileNow(source: AirspaceSource, z: Int, x: Int, y: Int): ByteArray? {
        val packId = source.catalog()?.packId ?: return source.tile(z, x, y)
        val key = "${source.layerId}/$packId/$z/$x/$y"
        tileCache.get(key)?.let { return it }
        val bytes = source.tile(z, x, y) ?: return null
        if (bytes.isNotEmpty()) tileCache.put(key, bytes)
        return bytes
    }

    /** 导入数据包并刷新；返回导入后的包信息 */
    suspend fun importAndRefresh(context: Context, uri: Uri): Result<PackStore.Pack> {
        val app = context.applicationContext
        val result = withContext(Dispatchers.IO) { runCatching { PackStore.importZip(app, uri) } }
        if (result.isSuccess) {
            // 导入后：没有任何选包的图层自动选上这个包（若它包含该层）
            val pack = result.getOrThrow()
            AirspaceLayers.all.forEach { spec ->
                if (AppPrefs.airspacePackByLayer[spec.id] == null && pack.hasLayer(spec.id)) {
                    AppPrefs.savePackForLayer(spec.id, pack.id)
                }
            }
            refresh(app)
        }
        return result
    }

    /** 删除数据包：清掉所有指向它的图层选择，然后刷新 */
    suspend fun deletePack(context: Context, packId: String): Boolean {
        val app = context.applicationContext
        val removed = withContext(Dispatchers.IO) { PackStore.delete(app, packId) }
        AirspaceLayers.all.forEach { spec ->
            if (AppPrefs.airspacePackByLayer[spec.id] == packId) {
                AppPrefs.savePackForLayer(spec.id, null)
            }
        }
        invalidate()
        refresh(app)
        return removed
    }

    fun invalidate() {
        packs = emptyList()
        sources = emptyMap()
        tileCache.evictAll()
    }

    // ---------- 内部 ----------

    private fun resolvePack(spec: AirspaceLayerSpec): PackStore.Pack? {
        val selectedId = AppPrefs.airspacePackByLayer[spec.id]
        packs.firstOrNull { it.id == selectedId && it.hasLayer(spec.id) }?.let { return it }
        val fallback = packs.firstOrNull { it.hasLayer(spec.id) } ?: return null
        // 自动选择第一个包含该层的包（只在"未选/选中包不含该层"时发生）
        AppPrefs.savePackForLayer(spec.id, fallback.id)
        return fallback
    }

    private fun catalogOf(
        spec: AirspaceLayerSpec,
        pack: PackStore.Pack,
        layer: ManifestLayer,
    ): AirspaceCatalog {
        val manifest = pack.manifest
        if (manifest.crs != TileCrs.GCJ02) {
            AppLogger.w(
                "Airspace",
                "pack_crs_not_gcj id=${pack.id} crs=${manifest.crs}（缩放时可能出现整体偏移）",
            )
        }
        return AirspaceCatalog(
            layerId = spec.id,
            packId = pack.id,
            packName = pack.name,
            version = manifest.version,
            zooms = layer.zooms,
            tileCrs = manifest.crs,
            tileSize = manifest.tileSize,
            // 图例与矢量渲染以图层注册的样式为准，避免图例与实际颜色不一致
            style = spec.style,
            attribution = manifest.attribution,
            generatedAtMillis = manifest.generatedAtMillis,
            dir = layer.dir,
            bbox = manifest.bbox,
        )
    }

    private fun demoCatalog(spec: AirspaceLayerSpec) = AirspaceCatalog(
        layerId = spec.id,
        packId = "demo",
        packName = "演示数据",
        version = "demo",
        zooms = 3..20,
        tileCrs = TileCrs.GCJ02,
        tileSize = 256,
        style = spec.style,
        attribution = null,
        generatedAtMillis = null,
        dir = "",
        bbox = null,
        isDemo = true,
    )
}
