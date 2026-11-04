package com.global_707.drone_scanner.ui.map

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.airspace.AirspaceLayers
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceState
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.launch

/**
 * 叠加图层菜单行。
 *
 * 设计规范（所有图层通用）：
 *  - 行首色块 = 图例（颜色取图层注册表 style.strokeColor，不再单独列图例区）；
 *  - 副标题只显示**当前选中的数据包名称**（演示数据时显示「演示数据」+ 演示徽标）；
 *  - 右侧 = 开关（有数据时）或动作按钮（导入）。
 */
data class LayerMenuOverlayRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val swatch: Color,
    val demo: Boolean,
    val visible: Boolean,
    val hasData: Boolean,
    val actionLabel: String?,
)

/** 图层菜单完整模型（底图单选 + 叠加层多选 + 图层设置入口） */
data class LayerMenuModel(
    val satellite: Boolean,
    val standardLabel: String,
    val satelliteLabel: String,
    val baseGroupLabel: String,
    val overlayGroupLabel: String,
    val rows: List<LayerMenuOverlayRow>,
    val settingsLabel: String,
    val onSelectBaseMap: (Boolean) -> Unit,
    val onToggleOverlay: (String) -> Unit,
    val onOverlayAction: (String) -> Unit,
    val onOpenLayerSettings: () -> Unit,
)

/**
 * 组装图层菜单模型（三处地图页共用）。
 *
 * 同时负责：进入页面/演示模式变化时刷新一次安装态（纯本地，不联网）；
 * 未安装数据时提供"导入离线包"的文件选择器。
 */
@Composable
fun rememberLayerMenu(context: Context, onOpenLayerSettings: () -> Unit): LayerMenuModel {
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = AirspaceRepository.importAndRefresh(appContext, uri)
            importing = false
            result.fold(
                onSuccess = { pack ->
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.airspace_import_success, pack.name),
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onFailure = { error ->
                    Toast.makeText(
                        appContext,
                        appContext.getString(
                            R.string.airspace_import_failed,
                            error.message ?: appContext.getString(R.string.airspace_import_failed_unknown),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    // 演示模式开关会改变"是否注入演示空域"，需重新解析数据源
    LaunchedEffect(AppPrefs.demoMode) { AirspaceRepository.refresh(appContext) }

    val rows = AirspaceLayers.all.map { spec ->
        val catalog = AirspaceState.catalogOf(spec.id)
        val visible = spec.id in AppPrefs.airspaceVisible
        val subtitle = when {
            importing -> stringResource(R.string.airspace_status_importing)
            catalog == null -> stringResource(R.string.airspace_pack_none)
            catalog.isDemo -> stringResource(R.string.airspace_pack_demo)
            else -> catalog.packName
        }
        LayerMenuOverlayRow(
            id = spec.id,
            title = stringResource(layerTitleRes(spec.id)),
            subtitle = subtitle,
            swatch = Color(spec.style.strokeColor),
            demo = catalog?.isDemo == true,
            visible = visible,
            hasData = catalog != null,
            actionLabel = if (catalog == null && !importing) {
                stringResource(R.string.airspace_action_install)
            } else {
                null
            },
        )
    }

    fun requestImport() = importLauncher.launch(arrayOf("*/*"))

    return LayerMenuModel(
        satellite = AppPrefs.satelliteMap,
        standardLabel = stringResource(R.string.map_standard),
        satelliteLabel = stringResource(R.string.map_satellite),
        baseGroupLabel = stringResource(R.string.map_layer_group_base),
        overlayGroupLabel = stringResource(R.string.map_layer_group_overlay),
        rows = rows,
        settingsLabel = stringResource(R.string.layer_settings),
        onSelectBaseMap = { value -> AppPrefs.saveSatelliteMap(value) },
        onToggleOverlay = { id ->
            if (AirspaceState.catalogOf(id) == null) {
                requestImport()
            } else {
                AppPrefs.saveAirspaceVisible(id, id !in AppPrefs.airspaceVisible)
            }
        },
        onOverlayAction = { requestImport() },
        onOpenLayerSettings = onOpenLayerSettings,
    )
}

/** 图层 id → 中文标题（新增图层时在这里加一条） */
internal fun layerTitleRes(id: String): Int = when (id) {
    AirspaceLayers.SUITABLE -> R.string.airspace_layer_suitable
    else -> R.string.airspace_layer_other
}

// ---------------- 菜单面板 ----------------

/**
 * 图层菜单面板：
 *  - 底图（单选，选完关闭）
 *  - 叠加图层（独立开关，开关后不关闭，就地看状态）
 *  - 底部「图层设置」入口
 *
 * 顶部不再显示「图层」标题，也不再单独列图例（图例并入每行行首的色块）。
 */
@Composable
fun LayerMenuPanel(model: LayerMenuModel, onDismiss: () -> Unit) {
    val colors = LocalDroneColors.current
    Column(
        Modifier
            .width(mapMenuWidth())
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card.copy(alpha = 0.82f))
            .border(1.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp),
    ) {
        MenuGroupLabel(text = model.baseGroupLabel)
        BaseMapRow(
            icon = AppIcons.Map,
            label = model.standardLabel,
            selected = !model.satellite,
        ) {
            model.onSelectBaseMap(false)
            onDismiss()
        }
        BaseMapRow(
            icon = AppIcons.Satellite,
            label = model.satelliteLabel,
            selected = model.satellite,
        ) {
            model.onSelectBaseMap(true)
            onDismiss()
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            thickness = 1.dp,
            color = colors.border,
        )

        MenuGroupLabel(text = model.overlayGroupLabel)
        model.rows.forEach { row ->
            OverlayRow(
                row = row,
                onToggle = { model.onToggleOverlay(row.id) },
                onAction = { model.onOverlayAction(row.id) },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            thickness = 1.dp,
            color = colors.border,
        )
        MapMenuActionRow(
            icon = AppIcons.Layers,
            label = model.settingsLabel,
            onClick = {
                model.onOpenLayerSettings()
                onDismiss()
            },
        )
    }
}

@Composable
private fun MenuGroupLabel(text: String) {
    val colors = LocalDroneColors.current
    Text(
        text = text,
        style = DroneTypography.label,
        color = colors.mutedForeground,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun BaseMapRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDroneColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.mutedForeground,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = DroneTypography.body,
            color = colors.foreground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun OverlayRow(
    row: LayerMenuOverlayRow,
    onToggle: () -> Unit,
    onAction: () -> Unit,
) {
    val colors = LocalDroneColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 行首色块 = 图例：关闭时降透明度，仍能看出图层配色
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(row.swatch.copy(alpha = if (row.visible) 1f else 0.35f))
                .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = DroneTypography.body,
                    color = if (row.hasData) colors.foreground else colors.mutedForeground,
                )
                if (row.demo) {
                    Spacer(Modifier.width(6.dp))
                    DemoBadge()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.subtitle,
                style = DroneTypography.label,
                color = colors.mutedForeground,
            )
        }
        val actionLabel = row.actionLabel
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = DroneTypography.label,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else {
            Switch(
                checked = row.visible,
                onCheckedChange = { onToggle() },
                enabled = row.hasData,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    uncheckedTrackColor = colors.muted,
                    uncheckedThumbColor = colors.card,
                ),
            )
        }
    }
}

/** 「演示」徽标（演示数据必须与真实数据区分） */
@Composable
private fun DemoBadge() {
    val colors = LocalDroneColors.current
    Text(
        text = stringResource(R.string.airspace_demo_badge),
        style = DroneTypography.micro,
        color = colors.warning,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.warning.copy(alpha = 0.16f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
