package com.suitanglu.app.ui.screens.recycle

import android.app.Application
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.db.Recording
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.suitanglu.app.ui.components.AppAlertDialog
import com.suitanglu.app.ui.components.SecondaryTopBar

class RecycleBinViewModel(app: Application) : AndroidViewModel(app) {
    private val container = AppGraph.get(app)

    val deleted: StateFlow<List<Recording>> = container.db.recordingDao().observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(id: Long) = viewModelScope.launch { container.recordingRepository.restore(id) }
    fun deleteForever(id: Long) = viewModelScope.launch { container.recordingRepository.deleteForever(id) }
    suspend fun clearAll() {
        deleted.value.forEach { container.recordingRepository.deleteForever(it.id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: RecycleBinViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val items by vm.deleted.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Recording?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { SecondaryTopBar(title = "回收站", onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                // 空状态：说清这里会出现什么、为什么现在没有
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("回收站是空的", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "删掉的录音会先放到这里，30 天内都能恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${items.size} 条已删除录音", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { confirmClear = true }) { Text("清空回收站") }
                }
                LazyColumn {
                    items(items, key = { it.id }) { rec ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(rec.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "删除于 ${rec.deletedAt?.let { Formatters.dateTime(it) } ?: "-"} · ${Formatters.duration(rec.durationMs)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        vm.restore(rec.id)
                                        scope.launch { snackbar.showSnackbar("已恢复「${rec.title}」") }
                                    }) {
                                        Icon(Icons.Filled.Restore, contentDescription = null, Modifier.width(16.dp).height(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("恢复")
                                    }
                                    TextButton(onClick = { confirmDelete = rec }) {
                                        Icon(Icons.Filled.DeleteForever, contentDescription = null, Modifier.width(16.dp).height(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("彻底删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AppAlertDialog(
            title = "清空回收站",
            message = "将彻底删除全部 ${items.size} 条录音及其图片、AI 回复，无法恢复。",
            confirmLabel = "全部删除",
            destructive = true,
            showIcon = true,
            onConfirm = {
                scope.launch {
                    vm.clearAll()
                    snackbar.showSnackbar("回收站已清空")
                }
                confirmClear = false
            },
            onDismissRequest = { confirmClear = false },
        )
    }

    confirmDelete?.let { rec ->
        AppAlertDialog(
            title = "彻底删除",
            message = "「${rec.title}」及其图片、AI 回复将被彻底删除，无法恢复。",
            confirmLabel = "彻底删除",
            destructive = true,
            showIcon = true,
            onConfirm = {
                vm.deleteForever(rec.id)
                confirmDelete = null
            },
            onDismissRequest = { confirmDelete = null },
        )
    }
}
