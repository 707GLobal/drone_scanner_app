package com.global_707.drone_scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 设置页统一外壳：**必须**用 `colors.background` 铺底。
 *
 * 之前「日志设置」页漏了这一层背景，深色模式下会露出 XML 主题的窗口底色（发灰），
 * 与其它纯黑设置页不一致。新增设置页一律套这个壳，避免再漏。
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        HeaderBar(title = title, onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            content = content,
        )
    }
}
