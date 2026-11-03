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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
 * 图层设置（与搜索/显示/日志设置并列的独立页面）：
 *
 *  - **每个图层独立选择数据包**（下拉），只列出"包含该图层"的包；
 *  - 包列表只显示名称 + 大小（不展示描述/版本信息）；
 *  - 支持导入多个包、逐个删除（删除会用确认弹窗，并清掉指向它的选包）；
 *  - 行首色块与主页图层菜单使用同一套图例样式（颜色来自图层注册表）。
 */
@Composable
fun LayerSettingsScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PackSummary?>(null) }

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

    LaunchedEffect(Unit) { AirspaceRepository.refresh(appContext) }

    SettingsScaffold(title = stringResource(R.string.layer_settings), onBack = onBack) {
        AirspaceLayers.all.forEachIndexed { index, spec ->
            if (index > 0) Spacer(Modifier.height(20.dp))
            LayerPackSection(
                spec = spec,
                packs = AirspaceState.packs,
                selectedPackId = AppPrefs.airspacePackByLayer[spec.id],
                onSelect = { packId ->
                    AppPrefs.savePackForLayer(spec.id, packId)
                    scope.launch { AirspaceRepository.refresh(appContext) }
                },
                onDelete = { pendingDelete = it },
            )
        }

        Spacer(Modifier.height(20.dp))

        // 全局导入按钮（导入后按"包含哪些图层"自动分配给未选包的图层）
        TextButton(
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            enabled = !importing,
        ) {
            Icon(
                imageVector = com.global_707.drone_scanner.ui.theme.AppIcons.Layers,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (importing) R.string.airspace_status_importing else R.string.layer_settings_import,
                ),
                color = colors.primary,
                style = DroneTypography.body,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = colors.border,
        )
        Text(
            text = stringResource(R.string.layer_settings_hint),
            style = DroneTypography.label,
            color = colors.mutedForeground,
        )
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

/** 单个图层的数据包选择区（与图层菜单同一套色块图例） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerPackSection(
    spec: AirspaceLayerSpec,
    packs: List<PackSummary>,
    selectedPackId: String?,
    onSelect: (String?) -> Unit,
    onDelete: (PackSummary) -> Unit,
) {
    val colors = LocalDroneColors.current
    val available = packs.filter { spec.id in it.layerIds }
    val selectedName = available.firstOrNull { it.id == selectedPackId }?.name
    var expanded by remember { mutableStateOf(false) }

    SectionCard {
        // 标题行：色块（图例）+ 图层名
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
                value = selectedName ?: stringResource(R.string.layer_settings_none),
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
                        text = { Text(pack.name) },
                        onClick = {
                            onSelect(pack.id)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (available.isEmpty()) {
            Text(
                text = stringResource(R.string.layer_settings_no_pack),
                style = DroneTypography.label,
                color = colors.mutedForeground,
            )
        } else {
            available.forEachIndexed { index, pack ->
                if (index > 0) {
                    HorizontalDivider(thickness = 0.5.dp, color = colors.border)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pack.name,
                        style = DroneTypography.body,
                        color = colors.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatPackSize(pack.sizeBytes),
                        style = DroneTypography.label,
                        color = colors.mutedForeground,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onDelete(pack) }) {
                        Text(
                            text = stringResource(R.string.layer_settings_delete),
                            style = DroneTypography.label,
                            color = colors.danger,
                        )
                    }
                }
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
