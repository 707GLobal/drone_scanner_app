package com.global_707.drone_scanner.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import com.global_707.drone_scanner.data.AppLogger
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceSource
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import kotlin.math.max
import kotlin.math.min

/**
 * 运行期瓦片放大（overzoom）：数据包只到原生最大级别 zN，更高级别用"父瓦片裁切 + 双线性放大"。
 *
 * 这是地图客户端的通用做法（MapLibre 的 `maxzoom`、Leaflet 的 `maxNativeZoom` 都是
 * "超过原生级别就放大最大级别的瓦片"），比"每个缩放级别都生成瓦片"省几个数量级的磁盘，
 * 也不会因为缺级别出现断层。
 *
 * 性能护栏：
 *  - 父瓦片 Bitmap 与放大结果都进 LRU（各 8 MB）；
 *  - 同时最多 2 个放大任务（信号量），避免快速缩放时 CPU 打满；
 *  - 最多放大 8 级（再放大只是模糊，成本相同，封顶避免无意义计算）。
 */
object TileOverzoom {

    private const val MAX_DZ = 8
    private const val CACHE_KB = 8 * 1024

    private val permits = Semaphore(2, true)

    private val parents = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val rendered = object : LruCache<String, ByteArray>(CACHE_KB) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * 取 `z/x/y`（z > 原生最大级别）的放大瓦片；返回 null 表示父瓦片不存在或渲染失败。
     */
    fun overzoom(
        source: AirspaceSource,
        nativeZoom: Int,
        z: Int,
        x: Int,
        y: Int,
        tileSize: Int,
    ): ByteArray? {
        val dz = min(z - nativeZoom, MAX_DZ)
        if (dz <= 0) return null

        val key = "${source.layerId}/${source.catalog()?.packId}/$z/$x/$y"
        rendered.get(key)?.let { return it }

        try {
            permits.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        }
        try {
            rendered.get(key)?.let { return it }
            val parent = parentBitmap(source, nativeZoom, x shr dz, y shr dz) ?: return null
            val factor = 1 shl dz
            val sub = max(1, tileSize / factor)
            val srcX = (x - ((x shr dz) shl dz)) * sub
            val srcY = (y - ((y shr dz) shl dz)) * sub
            val output = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawBitmap(
                parent,
                Rect(srcX, srcY, srcX + sub, srcY + sub),
                Rect(0, 0, tileSize, tileSize),
                paint,
            )
            val bytes = ByteArrayOutputStream().use { stream ->
                output.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
            output.recycle()
            if (bytes.isEmpty()) return null
            rendered.put(key, bytes)
            return bytes
        } catch (e: OutOfMemoryError) {
            AppLogger.w("Airspace", "overzoom_oom z=$z")
            rendered.evictAll()
            parents.evictAll()
            return null
        } catch (e: Exception) {
            AppLogger.w("Airspace", "overzoom_failed z=$z msg=${e.message}")
            return null
        } finally {
            permits.release()
        }
    }

    /** 清空缓存（换包/删包时调用） */
    fun clear() {
        parents.evictAll()
        rendered.evictAll()
    }

    private fun parentBitmap(
        source: AirspaceSource,
        nativeZoom: Int,
        x: Int,
        y: Int,
    ): Bitmap? {
        val key = "${source.layerId}/${source.catalog()?.packId}/$nativeZoom/$x/$y"
        parents.get(key)?.let { return it }
        val bytes = AirspaceRepository.tileNow(source, nativeZoom, x, y) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        parents.put(key, bitmap)
        return bitmap
    }
}
