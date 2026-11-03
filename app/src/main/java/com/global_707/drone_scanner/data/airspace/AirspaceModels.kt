package com.global_707.drone_scanner.data.airspace

import com.global_707.drone_scanner.util.GeoPoint
import com.global_707.drone_scanner.util.GeoRect

/**
 * 适飞空域图层的领域模型（与数据来源、地图 SDK 都无关）。
 *
 * 当前只有"适飞空域"一层，其余空域（禁飞区 / 限飞区 / 临时空域）后续按同一模式
 * 在 [AirspaceLayers.all] 里追加：菜单行、图层设置页的选区、渲染与配色都会自动跟上。
 */

/** 空域类别；本期只使用 [SUITABLE] */
enum class AirspaceKind { SUITABLE, RESTRICTED, NO_FLY, OTHER }

/** 瓦片内容所在的坐标网格（决定绘制时是否需要做网格重映射） */
enum class TileCrs {
    /** 已按 GCJ-02 网格出图：腾讯地图直接按同一瓦片号读取，零偏移（**唯一推荐**） */
    GCJ02,

    /** 原始 WGS-84 网格：换算瓦片号会产生"随缩放级别变化"的偏移（仅兼容旧包） */
    WGS84,
}

/** 图层配色；栅格包的最终颜色由生成器烘焙，这里用于图例与矢量渲染 */
data class AirspaceStyle(
    val fillColor: Int,
    val strokeColor: Int,
    val strokeWidthDp: Float,
)

/** 某个图层当前使用的数据（来自某个离线包，或演示模式下的内置演示数据） */
data class AirspaceCatalog(
    val layerId: String,
    /** 数据包 id（文件目录名）；演示数据为 "demo" */
    val packId: String,
    /** 数据包显示名（manifest.name），用于菜单副标题 */
    val packName: String,
    val version: String,
    /** 原生缩放级别范围（包内实际瓦片级别；更高由运行期 overzoom 放大） */
    val zooms: IntRange,
    val tileCrs: TileCrs,
    val tileSize: Int,
    val style: AirspaceStyle,
    val attribution: String?,
    val generatedAtMillis: Long?,
    /** 图层数据在包内的相对目录 */
    val dir: String,
    /** 包覆盖范围（WGS-84） */
    val bbox: GeoRect?,
    /** 演示数据：没有栅格瓦片，只用矢量要素渲染 */
    val isDemo: Boolean = false,
)

/** 几何：多边形（rings[0] 外环，其余为内环洞）或折线；坐标一律 WGS-84 */
sealed interface AirspaceGeometry {
    data class Polygon(val rings: List<List<GeoPoint>>) : AirspaceGeometry
    data class Line(val points: List<GeoPoint>) : AirspaceGeometry
}

/** 单个空域要素（矢量渲染使用；栅格包不产生要素） */
data class AirspaceFeature(
    val id: String?,
    val kind: AirspaceKind,
    val geometry: AirspaceGeometry,
)

/** 已导入数据包的摘要（供「图层设置」页展示与选择） */
data class PackSummary(
    val id: String,
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val layerIds: List<String>,
)

/** 空域数据错误（UI 按 [Kind] 选文案，不展示原始异常） */
class AirspaceException(
    val kind: Kind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** 没有可用数据包（且非演示模式） */
        NOT_INSTALLED,

        /** manifest.json 缺失 / 字段非法 */
        MANIFEST_INVALID,

        /** 读文件失败 */
        IO,

        /** 数据格式暂不支持 */
        UNSUPPORTED,
    }
}

/**
 * 叠加层定义（静态注册）。
 *
 * 只有注册过的层才会出现在菜单与图层设置里：数据包里出现未注册的 layerKey 时只记日志、
 * 不显示，避免出现"没有中文标题/没有配色"的野生图层。
 */
data class AirspaceLayerSpec(
    val id: String,
    val kind: AirspaceKind,
    /** 默认是否开启；空域合规起见默认关闭 */
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
            // 透明浅青 #93F4F1：填充 30%（0x4D）、描边 85%（0xD9）
            style = AirspaceStyle(
                fillColor = 0x4D93F4F1,
                strokeColor = 0xD993F4F1.toInt(),
                strokeWidthDp = 2f,
            ),
        ),
    )

    fun spec(id: String): AirspaceLayerSpec? = all.firstOrNull { it.id == id }
}
