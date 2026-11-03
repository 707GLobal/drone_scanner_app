package com.global_707.drone_scanner.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.airspace.AirspaceLayerSpec
import com.global_707.drone_scanner.data.airspace.AirspaceLayers
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceState
import com.global_707.drone_scanner.data.airspace.PackSummary
import com.global_707.drone_scanner.ui.components.SectionCard
import com.global_707.drone_scanner.ui.map.layerTitleRes
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 图层设置（与搜索/显示/日志设置并列的独立页面）。
 *
 * 结构（对所有图层统一，后续接入禁飞区/限飞区自动套用）：
 *  1. 顶部一句总说明（每个图层可独立选择数据包、数据只存本机）；
 *  2. 每个图层一个区块：色块图例 + 图层名 + 数据包下拉 +「导入」「删除」；
 *  3. 导入的文件只绑定到当前图层（若包里不含该图层会提示），删除会移除整个数据包
 *     并清掉所有指向它的图层选择。
 *
 * 只显示数据包名称与大小，不展示描述/版本等冗余信息。
 */
@Composable
fun LayerSettingsScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    /** 本次导入要绑定的图层（每个图层的「导入」按钮都会设置它） */
    var importTargetLayer by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<PackSummary?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val layerId = importTargetLayer
        scope.launch {
            importing = true
            val result = AirspaceRepository.importAndRefresh(appContext, uri)
            importing = false
            result.fold(
                onSuccess = { pack ->
                    if (layerId != null && layerId !in pack.layerIds) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.layer_settings_import_not_for_layer, pack.name),
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        if (layerId != null) {
                            AppPrefs.savePackForLayer(layerId, pack.id)
                            AirspaceRepository.refresh(appContext)
                        }
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.airspace_import_success, pack.name),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
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

    SettingsScaffold(title = stringResource(R.string.layer_settings), onBack = onBack) {
        // 说明放最前面（用户要求：先说明"每个图层可独立选择"，再看具体配置）
        Text(
            text = stringResource(R.string.layer_settings_intro),
            style = DroneTypography.label,
            color = colors.mutedForeground,
        )

        Spacer(Modifier.height(16.dp))

        AirspaceLayers.all.forEachIndexed { index, spec ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            LayerPackSection(
                spec = spec,
                packs = AirspaceState.packs,
                selectedPackId = AppPrefs.airspacePackByLayer[spec.id],
                busy = importing,
                onSelect = { packId ->
                    AppPrefs.savePackForLayer(spec.id, packId)
                    scope.launch { AirspaceRepository.refresh(appContext) }
                },
                onImport = {
                    importTargetLayer = spec.id
                    importLauncher.launch(arrayOf("*/*"))
                },
                onDelete = { pendingDelete = it },
            )
        }
    }

    pendingDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.layer_settings_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.layer_settings_delete_message,
                        pack.name,
                        formatPackSize(pack.sizeBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            AirspaceRepository.deletePack(appContext, pack.id)
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.layer_settings_deleted),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.layer_settings_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.layer_settings_delete_cancel))
                }
            },
        )
    }
}

/**
 * 单个图层的配置区块：色块 + 图层名 + 数据包下拉 +「导入」「删除」。
 *
 * 这是"每个图层一套"的统一模板：将来新增空域图层时不需要改这个页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerPackSection(
    spec: AirspaceLayerSpec,
    packs: List<PackSummary>,
    selectedPackId: String?,
    busy: Boolean,
    onSelect: (String?) -> Unit,
    onImport: () -> Unit,
    onDelete: (PackSummary) -> Unit,
) {
    val colors = LocalDroneColors.current
    val available = packs.filter { spec.id in it.layerIds }
    val selected = available.firstOrNull { it.id == selectedPackId }
    var expanded by remember { mutableStateOf(false) }

    SectionCard {
        // 标题：色块（与主页图层菜单同一套图例样式）+ 图层名
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.Color(spec.style.strokeColor))
                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(layerTitleRes(spec.id)),
                style = DroneTypography.body,
                color = colors.foreground,
            )
        }

        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.name ?: stringResource(R.string.layer_settings_none),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.layer_settings_pack_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary,
                    cursorColor = colors.primary,
                ),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.layer_settings_none)) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                available.forEach { pack ->
                    DropdownMenuItem(
                        text = {
                            Text("${pack.name} · ${formatPackSize(pack.sizeBytes)}")
                        },
                        onClick = {
                            onSelect(pack.id)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (selected != null) {
                stringResource(R.string.layer_settings_size, formatPackSize(selected.sizeBytes))
            } else if (available.isEmpty()) {
                stringResource(R.string.layer_settings_no_pack)
            } else {
                stringResource(R.string.layer_settings_none)
            },
            style = DroneTypography.label,
            color = colors.mutedForeground,
        )

        Spacer(Modifier.height(8.dp))

        // 每个图层自己的「导入」「删除」入口
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onImport, enabled = !busy) {
                Text(
                    text = stringResource(
                        if (busy) R.string.airspace_status_importing else R.string.layer_settings_import,
                    ),
                    style = DroneTypography.body,
                    color = colors.primary,
                )
            }
            TextButton(
                onClick = { selected?.let(onDelete) },
                enabled = selected != null && !busy,
            ) {
                Text(
                    text = stringResource(R.string.layer_settings_delete),
                    style = DroneTypography.body,
                    color = if (selected != null) colors.danger else colors.mutedForeground,
                )
            }
        }
    }
}

/** 数据包大小格式化：<1 MB 用 KB，否则 MB */
internal fun formatPackSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
        String.format(Locale.US, "%.0f KB", kb)
    } else {
        String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }
}
