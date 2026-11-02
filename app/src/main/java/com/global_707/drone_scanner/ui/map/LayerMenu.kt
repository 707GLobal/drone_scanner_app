package com.global_707.drone_scanner.ui.map

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.airspace.AirspaceLayerStatus
import com.global_707.drone_scanner.data.airspace.AirspaceLayers
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceSourceKind
import com.global_707.drone_scanner.data.airspace.AirspaceState
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.launch

/** 叠加层菜单行（已把状态、版本、动作解析成文案） */
data class LayerMenuOverlayRow(
    val id: String,
    val title: String,
    val status: String,
    val detail: String?,
    val visible: Boolean,
    val enabled: Boolean,
    /** 需要一个动作按钮（如"导入"/"重试"）时非空；此时不显示开关 */
    val actionLabel: String?,
)

/** 图层菜单的完整模型（底图单选 + 叠加层多选），由 [rememberLayerMenu] 组装 */
data class LayerMenuModel(
    val satellite: Boolean,
    val standardLabel: String,
    val satelliteLabel: String,
    val baseGroupLabel: String,
    val overlayGroupLabel: String,
    val rows: List<LayerMenuOverlayRow>,
    val legendLabel: String?,
    val legendColor: Color?,
    val onSelectBaseMap: (Boolean) -> Unit,
    val onToggleOverlay: (String) -> Unit,
    val onOverlayAction: (String) -> Unit,
)

/**
 * 组装图层菜单模型（三处地图页共用）。
 *
 * 同时负责：
 *  - 进入页面时刷新一次安装态（读本地 manifest，不联网）；
 *  - 提供"导入离线包"的文件选择器（未安装数据时，点开关或动作按钮都会拉起它）。
 */
@Composable
fun rememberLayerMenu(context: Context): LayerMenuModel {
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
                onSuccess = { manifest ->
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.airspace_import_success, manifest.version),
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

    LaunchedEffect(Unit) { AirspaceRepository.refresh(appContext) }

    val satellite = AppPrefs.satelliteMap
    val visibleLayers = AppPrefs.airspaceVisible

    val rows = AirspaceLayers.all.map { spec ->
        val catalog = AirspaceState.catalogOf(spec.id)
        val status = AirspaceState.statusOf(spec.id)
        val installed = catalog != null
        val visible = spec.id in visibleLayers
        val sourceLabel = when (catalog?.source) {
            AirspaceSourceKind.BUNDLED -> stringResource(R.string.airspace_source_bundled)
            AirspaceSourceKind.IMPORTED -> stringResource(R.string.airspace_source_imported)
            null -> ""
        }
        val statusText = when {
            importing -> stringResource(R.string.airspace_status_importing)
            !installed && AirspaceState.errorMessage != null -> stringResource(R.string.airspace_status_error)
            !installed -> stringResource(R.string.airspace_status_missing)
            status == AirspaceLayerStatus.ZOOM_OUT -> stringResource(
                R.string.airspace_status_zoom_out,
                catalog!!.zooms.first,
                catalog.zooms.last,
            )
            else -> stringResource(
                R.string.airspace_status_ready,
                catalog!!.version,
                sourceLabel,
            )
        }
        val detail = catalog?.let {
            stringResource(R.string.airspace_detail_zooms, it.zooms.first, it.zooms.last)
        }
        val actionLabel = when {
            !installed -> stringResource(R.string.airspace_action_import)
            status == AirspaceLayerStatus.ERROR -> stringResource(R.string.airspace_action_retry)
            else -> null
        }
        LayerMenuOverlayRow(
            id = spec.id,
            title = stringResource(layerTitleRes(spec.id)),
            status = statusText,
            detail = detail,
            visible = visible,
            enabled = installed,
            actionLabel = actionLabel,
        )
    }

    val legendSpec = AirspaceLayers.all.firstOrNull { it.id in visibleLayers }

    fun requestImport() = importLauncher.launch(arrayOf("*/*"))

    return LayerMenuModel(
        satellite = satellite,
        standardLabel = stringResource(R.string.map_standard),
        satelliteLabel = stringResource(R.string.map_satellite),
        baseGroupLabel = stringResource(R.string.map_layer_group_base),
        overlayGroupLabel = stringResource(R.string.map_layer_group_overlay),
        rows = rows,
        legendLabel = legendSpec?.let { stringResource(layerTitleRes(it.id)) },
        legendColor = legendSpec?.let { Color(it.style.strokeColor) },
        onSelectBaseMap = { value -> AppPrefs.saveSatelliteMap(value) },
        onToggleOverlay = { id ->
            if (AirspaceState.catalogOf(id) == null) {
                requestImport()
            } else {
                AppPrefs.saveAirspaceVisible(id, id !in AppPrefs.airspaceVisible)
            }
        },
        onOverlayAction = { id ->
            if (AirspaceState.catalogOf(id) == null) {
                requestImport()
            } else {
                scope.launch { AirspaceRepository.refresh(appContext) }
            }
        },
    )
}

/** 图层 id → 中文标题（新增图层时在这里加一条） */
private fun layerTitleRes(id: String): Int = when (id) {
    AirspaceLayers.SUITABLE -> R.string.airspace_layer_suitable
    else -> R.string.airspace_layer_other
}

// ---------------- 菜单面板 ----------------

/**
 * 图层菜单面板：底图（单选）+ 叠加图层（独立开关）+ 图例。
 * 由 [MapControls] 放在 Popup 里显示。
 */
@Composable
fun LayerMenuPanel(model: LayerMenuModel, onDismiss: () -> Unit) {
    val colors = LocalDroneColors.current
    Column(
        Modifier
            .width(252.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.map_layers),
            style = DroneTypography.captionMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.mutedForeground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

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

        val legendLabel = model.legendLabel
        val legendColor = model.legendColor
        if (legendLabel != null && legendColor != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                thickness = 1.dp,
                color = colors.border,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.map_layer_legend),
                    style = DroneTypography.label,
                    color = colors.mutedForeground,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(legendColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = legendLabel,
                    style = DroneTypography.label,
                    color = colors.foreground,
                )
            }
        }
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
        Icon(
            imageVector = AppIcons.Zone,
            contentDescription = null,
            tint = if (row.enabled) colors.primary else colors.mutedForeground,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = DroneTypography.body,
                color = if (row.enabled) colors.foreground else colors.mutedForeground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.status,
                style = DroneTypography.label,
                color = colors.mutedForeground,
            )
            if (row.detail != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = row.detail,
                    style = DroneTypography.micro,
                    color = colors.mutedForeground.copy(alpha = 0.8f),
                )
            }
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
                enabled = row.enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    uncheckedTrackColor = colors.muted,
                    uncheckedThumbColor = colors.card,
                ),
            )
        }
    }
}
