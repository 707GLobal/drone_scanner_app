package com.global_707.drone_scanner.data.airspace

import com.global_707.drone_scanner.util.GeoPoint
import com.global_707.drone_scanner.util.GeoRect

/**
 * 适飞空域图层的领域模型（与数据来源、地图 SDK 都无关）。
 *
 * 当前只有"适飞空域"一层，其余空域（禁飞区 / 限飞区 / 临时空域）后续按同一模式
 * 在 [AirspaceLayers.all] 里追加，菜单会自动多出一行，无需改布局。
 */

/** 空域类别；本期只使用 [SUITABLE]，其余为后续扩展预留 */
enum class AirspaceKind { SUITABLE, RESTRICTED, NO_FLY, OTHER }

/** 数据源形态：内置（随 APK）或用户导入（本机私有目录） */
enum class AirspaceSourceKind { BUNDLED, IMPORTED }

/** 瓦片内容所在的坐标网格（决定绘制时是否需要做网格重映射） */
enum class TileCrs {
    /** 已按 GCJ-02 网格出图：腾讯地图直接按同一瓦片号读取，零偏移 */
    GCJ02,

    /** 原始 WGS-84 网格：需要换算瓦片号，残余偏移约等于 GCJ 偏移（见 TileMath.gcjTileToWgsTile） */
    WGS84,
}

/** 图层配色（透明浅蓝）；栅格包的最终颜色由生成器决定，这里用于菜单图例 */
data class AirspaceStyle(
    val fillColor: Int,
    val strokeColor: Int,
    val strokeWidthDp: Float,
)

/** 已安装图层的元信息（manifest 解析后的运行时视图） */
data class AirspaceCatalog(
    val layerId: String,
    val version: String,
    val zooms: IntRange,
    val tileCrs: TileCrs,
    val tileSize: Int,
    val style: AirspaceStyle,
    val attribution: String?,
    val source: AirspaceSourceKind,
    val generatedAtMillis: Long?,
    /** 图层数据在包内的相对目录（例如 "suitable"） */
    val dir: String,
    /** 整个包的覆盖范围（WGS-84）；离线包用来提示"是否覆盖当前区域" */
    val bbox: GeoRect?,
)

/** 几何：多边形（rings[0] 为外环，其余为内环洞）或折线；坐标一律 WGS-84 */
sealed interface AirspaceGeometry {
    data class Polygon(val rings: List<List<GeoPoint>>) : AirspaceGeometry
    data class Line(val points: List<GeoPoint>) : AirspaceGeometry
}

/** 单个空域要素（矢量源使用；栅格包不产生要素） */
data class AirspaceFeature(
    val id: String?,
    val kind: AirspaceKind,
    val geometry: AirspaceGeometry,
)

/** 空域数据错误（UI 只按 [Kind] 选文案，不展示原始异常） */
class AirspaceException(
    val kind: Kind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** 未安装数据包 */
        NOT_INSTALLED,

        /** manifest.json 缺失 / 字段非法 */
        MANIFEST_INVALID,

        /** 读文件失败 */
        IO,

        /** 数据格式暂不支持（例如矢量包尚未实现） */
        UNSUPPORTED,
    }
}

/**
 * 叠加层定义（静态注册，不是从数据里动态生成）。
 *
 * 只有注册过的层才会出现在菜单里：数据包里出现未注册的 layerKey 时只记日志、不显示，
 * 避免出现"没有中文标题/没有配色"的野生图层。
 */
data class AirspaceLayerSpec(
    val id: String,
    val kind: AirspaceKind,
    /** 默认是否开启；为空域合规起见默认关闭 */
    val defaultVisible: Boolean,
    val style: AirspaceStyle,
)

object AirspaceLayers {
    const val SUITABLE = "suitable"

    val all: List<AirspaceLayerSpec> = listOf(
        AirspaceLayerSpec(
            id = SUITABLE,
            kind = AirspaceKind.SUITABLE,
            defaultVisible = false,
            // 透明浅蓝：填充 20% 不透明度，描边 80%
            style = AirspaceStyle(
                fillColor = 0x337FC4FF,
                strokeColor = 0xCC5AA9E6.toInt(),
                strokeWidthDp = 2f,
            ),
        ),
    )

    fun spec(id: String): AirspaceLayerSpec? = all.firstOrNull { it.id == id }
}
