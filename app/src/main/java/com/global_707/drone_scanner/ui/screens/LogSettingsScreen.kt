package com.global_707.drone_scanner.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppLogger
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.LogFileRef
import com.global_707.drone_scanner.data.LogFileStore
import com.global_707.drone_scanner.data.LogMode
import com.global_707.drone_scanner.ui.components.SectionCard
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日志设置页：
 *  - 记录方式：自动记录 / 手动记录 / 关闭（切换时会先停止并保存当前日志）
 *  - 手动控制：开始 / 终止 + 实时时长（HH:mm:ss）
 *  - 保存位置：默认应用专属目录 或 SAF 自定义目录（可恢复默认）
 *  - 日志文件：总大小、查看日志、清理日志（带确认）
 *
 * 页面只做 UI 与编排；文件读写、统计、清理全部交给 AppLogger / LogFileStore 在 IO 线程完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSettingsScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current

    // 统计信息的刷新信号（清理后 +1 触发重新统计）
    var refreshTick by remember { mutableIntStateOf(0) }

    // 日志（列表 + 总大小）后台统计，避免主线程 IO
    val logs by produceState(initialValue = emptyList<LogFileRef>(), refreshTick, AppPrefs.logTreeUri) {
        value = withContext(Dispatchers.IO) { LogFileStore.listLogs(context) }
    }
    val totalBytes = logs.sumOf { it.sizeBytes }

    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // SAF 选择目录：持久化授权后立即建目录，失败则回退原位置并提示
    val pickDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!ok) {
            toast(context, context.getString(R.string.log_location_pick_failed))
            return@rememberLauncherForActivityResult
        }
        // 切换位置：先停当前记录（保存），再校验新目录可写性
        val wasRecording = AppLogger.isRecording
        if (wasRecording) AppLogger.stopManual()
        val writable = LogFileStore.canWriteCustom(context, uri)
        if (!writable) {
            toast(context, context.getString(R.string.log_location_pick_failed))
            if (wasRecording) AppLogger.startManual(context)
            return@rememberLauncherForActivityResult
        }
        AppPrefs.saveLogTreeUri(uri.toString())
        if (wasRecording) AppLogger.startManual(context)
        refreshTick++
    }

    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize(),
    ) {
        HeaderBar(title = stringResource(R.string.log_settings), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // ---- 记录方式 ----
            GroupTitle(title = stringResource(R.string.log_mode_title))
            Spacer(Modifier.height(12.dp))
            SectionCard {
                LogModeSelector { mode ->
                    AppPrefs.saveLogMode(mode)
                    AppLogger.applyMode(context, mode)
                    refreshTick++
                }
                Spacer(Modifier.height(10.dp))
                // 说明文案随记录方式淡入淡出（M3 原生动画）
                AnimatedContent(
                    targetState = AppPrefs.logMode,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "logModeDesc",
                ) { mode ->
                    Text(
                        text = stringResource(
                            when (mode) {
                                LogMode.AUTO -> R.string.log_mode_auto_desc
                                LogMode.MANUAL -> R.string.log_mode_manual_desc
                                else -> R.string.log_mode_off_desc
                            },
                        ),
                        style = DroneTypography.label,
                        color = colors.mutedForeground,
                    )
                }
            }

            // ---- 手动控制（仅手动模式展开，原生展开/收起动画）----
            AnimatedVisibility(
                visible = AppPrefs.logMode == LogMode.MANUAL,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    SectionCard {
                        Text(
                            text = stringResource(R.string.log_manual_title),
                            style = DroneTypography.body,
                            color = colors.foreground,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    AppLogger.startManual(context)
                                    refreshTick++
                                },
                                enabled = !AppLogger.isRecording,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.log_btn_start))
                            }
                            OutlinedButton(
                                onClick = {
                                    AppLogger.stopManual()
                                    refreshTick++
                                },
                                enabled = AppLogger.isRecording,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.log_btn_stop))
                            }
                        }
                        // 未开始记录时不显示任何时长信息；记录中/停止后（有时长）才显示
                        if (AppLogger.isRecording || AppLogger.elapsedMs > 0L) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(
                                    if (AppLogger.isRecording) {
                                        R.string.log_state_recording
                                    } else {
                                        R.string.log_state_stopped
                                    },
                                ),
                                style = DroneTypography.label,
                                color = if (AppLogger.isRecording) {
                                    colors.success
                                } else {
                                    colors.mutedForeground
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.log_duration,
                                    AppLogger.formatDuration(AppLogger.elapsedMs),
                                ),
                                style = DroneTypography.body,
                                color = colors.foreground,
                            )
                            if (AppLogger.isRecording) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = AppLogger.currentFileName()?.let {
                                        stringResource(R.string.log_current_file, it)
                                    } ?: stringResource(R.string.log_current_file_none),
                                    style = DroneTypography.caption,
                                    color = colors.mutedForeground,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 保存位置 ----
            GroupTitle(title = stringResource(R.string.log_location_title))
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text(
                    text = stringResource(R.string.log_location_current, LogFileStore.displayPath(context)),
                    style = DroneTypography.label,
                    color = colors.foreground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.log_default_path,
                        LogFileStore.defaultPath(context),
                    ),
                    style = DroneTypography.caption,
                    color = colors.mutedForeground,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { pickDirLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.log_location_pick))
                    }
                    OutlinedButton(
                        onClick = {
                            AppPrefs.saveLogTreeUri(null)
                            refreshTick++
                        },
                        enabled = AppPrefs.logTreeUri != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.log_location_reset))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 日志文件 ----
            GroupTitle(title = stringResource(R.string.log_files_title))
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text(
                    text = stringResource(R.string.log_total_size, AppLogger.formatSize(totalBytes)),
                    style = DroneTypography.body,
                    color = colors.foreground,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { openLogsDirectory(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.log_view))
                    }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        enabled = logs.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.log_clear))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.log_clear_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.log_clear_confirm_message,
                        logs.size,
                        AppLogger.formatSize(totalBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        // 删除是 ContentResolver IO（可能涉及多个文件），交给 AppLogger 在 IO 线程做，
                        // 完成后回主线程刷新统计
                        scope.launch {
                            val deleted = AppLogger.clearLogs(context)
                            toast(
                                context,
                                context.getString(
                                    if (deleted > 0) R.string.log_clear_done else R.string.log_clear_none,
                                    deleted,
                                ),
                            )
                            refreshTick++
                        }
                    },
                ) {
                    Text(stringResource(R.string.log_clear_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.log_clear_confirm_cancel))
                }
            },
        )
    }
}

/** 记录方式分段控件（与「扫描模式」滑块同一套 M3 原生组件） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogModeSelector(onSelect: (Int) -> Unit) {
    val colors = LocalDroneColors.current
    val options = listOf(
        LogMode.AUTO to stringResource(R.string.log_mode_auto),
        LogMode.MANUAL to stringResource(R.string.log_mode_manual),
        LogMode.OFF to stringResource(R.string.log_mode_off),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = AppPrefs.logMode == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primary,
                    activeContentColor = colors.primaryForeground,
                    inactiveContainerColor = colors.card,
                    inactiveContentColor = colors.foreground,
                    activeBorderColor = colors.primary,
                    inactiveBorderColor = colors.border,
                ),
            ) {
                Text(text = label, style = DroneTypography.label)
            }
        }
    }
}

/**
 * 查看日志：让系统「文件」直接打开日志文件夹浏览。
 *
 * 两个必须同时满足的条件（均为实测得出，缺一不可）：
 *  1. **目录必须是文件管理器允许浏览的位置**——公开目录（`Download/RIDScannerLogs`）可以；
 *     应用私有目录（`Android/data` 及其子目录）在 Android 11+ 会被忽略并停在「下载」。
 *     位置由 [LogFileStore.directoryViewUri] 保证（见其 KDoc）。
 *  2. **必须指名系统「文件」（`com.android.documentsui`）**——否则 WPS Office、网盘等声明了
 *     通用 `ACTION_VIEW` 的应用会抢答，弹出「打开方式」选择器。
 */
private fun openLogsDirectory(context: Context) {
    val dirUri = LogFileStore.directoryViewUri(context)
    if (dirUri == null) {
        toast(context, context.getString(R.string.log_view_failed))
        return
    }
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(dirUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (runCatching { context.startActivity(Intent(view).setPackage(DOCUMENTS_UI_PACKAGE)) }.isSuccess) {
        return
    }
    if (runCatching { context.startActivity(view) }.isSuccess) return
    toast(context, context.getString(R.string.log_view_failed))
}

/** 系统「文件」（DocumentsUI）包名：`ACTION_VIEW + vnd.android.document/directory` 的标准处理者 */
private const val DOCUMENTS_UI_PACKAGE = "com.android.documentsui"

private fun toast(context: Context, text: String) {
    runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
}
