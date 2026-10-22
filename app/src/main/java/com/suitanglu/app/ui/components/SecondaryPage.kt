package com.suitanglu.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 二级页统一容器：左上返回 + 19sp 标题 + 可滚动内容 + 可选底部操作条。
 *
 * 之前 AI 配置、数据与存储、课程表各自手写 Scaffold + TopAppBar，
 * 标题字号和顶部留白都不一致；保存按钮跟在长表单末尾，改一半要滚到最底才能存。
 * 有底部操作条时，主操作始终在拇指区。
 *
 * [unsavedChanges] 为 true 时，左上返回和系统返回都会先问一句——
 * 之前 AI 配置填完 Key 直接按返回就全丢了，且没有任何提示。
 *
 * @param bottomBar 传 null 表示没有常驻操作（例如只读页）
 * @param unsavedChanges 当前页面是否存在未保存的编辑
 */
@Composable
fun SecondaryPage(
    title: String,
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    unsavedChanges: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showDiscard by remember { mutableStateOf(false) }
    val guardedBack: () -> Unit = {
        if (unsavedChanges) showDiscard = true else onBack()
    }
    BackHandler(enabled = unsavedChanges) { showDiscard = true }

    Scaffold(
        // 键盘弹出时把整个 Scaffold 压到键盘上方：底部保存键不再被输入法盖住，
        // 滚动区域也随之变矮，用户能滚到正在填的那一格
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SecondaryTopBar(title = title, onBack = guardedBack) },
        bottomBar = {
            if (bottomBar != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.navigationBarsPadding()) { bottomBar() }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }

    if (showDiscard) {
        AppAlertDialog(
            title = "放弃修改？",
            message = "这一页还有没保存的改动，返回后会丢失。",
            confirmLabel = "放弃",
            destructive = true,
            dismissLabel = "继续编辑",
            onConfirm = {
                showDiscard = false
                onBack()
            },
            onDismissRequest = { showDiscard = false },
        )
    }
}

/** 底部操作条里的主按钮行：主操作占满，次操作用描边平分 */
@Composable
fun BottomActionRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 二级页顶栏：返回 + 19sp 标题（可选右侧动作）。
 * 全应用只有三种标题层级：首页 23sp、二级页 19sp、录音页 19sp，
 * 原来每个页面各自用 TopAppBar / LargeTopAppBar，字号从 19 到 28 都有。
 */
@Composable
fun SecondaryTopBar(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions?.let { Row(verticalAlignment = Alignment.CenterVertically, content = it) }
    }
}
