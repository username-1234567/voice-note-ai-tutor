package com.suitanglu.app.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suitanglu.app.data.db.ChatSession
import com.suitanglu.app.ui.components.AppAlertDialog
import com.suitanglu.app.ui.theme.MotionSpec
import com.suitanglu.app.ui.theme.pressFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: ChatListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ChatListViewModel(context.applicationContext as android.app.Application) }
        },
    )
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            val source = remember { MutableInteractionSource() }
            ExtendedFloatingActionButton(
                onClick = { vm.create(onOpenSession) },
                interactionSource = source,
                modifier = Modifier.pressFeedback(source, MotionSpec.PRESS_SCALE_WIDE),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新对话") },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "还没有对话",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "随手问一个问题，或者拍张题发过去，答完会存成一条记录，下次能找回来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions, key = { it.id }) { s ->
                    val source = remember { MutableInteractionSource() }
                    Card(
                        onClick = { onOpenSession(s.id) },
                        interactionSource = source,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressFeedback(source, MotionSpec.PRESS_SCALE_WIDE),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.title.ifBlank { "新对话" },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        java.text.SimpleDateFormat(
                                            "MM-dd HH:mm",
                                            java.util.Locale.getDefault(),
                                        ).format(java.util.Date(s.updatedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    s.note?.takeIf { it.isNotBlank() }?.let {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { pendingDelete = s }) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "删除对话",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { s ->
        AppAlertDialog(
            title = "删除对话",
            message = "「${s.title.ifBlank { "新对话" }}」会连同其中的图片一起删除，无法恢复。",
            confirmLabel = "删除",
            dismissLabel = "取消",
            destructive = true,
            onConfirm = {
                vm.delete(s.id)
                pendingDelete = null
            },
            onDismissRequest = { pendingDelete = null },
        )
    }
}
