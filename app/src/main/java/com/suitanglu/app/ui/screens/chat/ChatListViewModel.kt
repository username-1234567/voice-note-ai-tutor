package com.suitanglu.app.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.RecordApp
import com.suitanglu.app.data.db.ChatSession
import com.suitanglu.app.data.db.DirectMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatListViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as RecordApp).container

    val sessions: StateFlow<List<ChatSession>> = container.db.chatSessionDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 点"新对话"就落库：先有 id 才能进对话页，空会话用户随时可以删 */
    fun create(onCreated: (Long) -> Unit) {
        container.appScope.launch {
            val id = container.db.chatSessionDao().insert(ChatSession(title = "新对话"))
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun delete(id: Long) {
        container.appScope.launch {
            withContext(Dispatchers.IO) {
                // 图片是私有目录里的实体文件，只删数据库记录会一直占着空间
                container.db.directMessageDao().getBySession(id).forEach { m ->
                    DirectMessage.splitPaths(m.imagePaths).forEach { p ->
                        runCatching { File(p).delete() }
                    }
                }
                container.db.directMessageDao().deleteBySession(id)
                container.db.chatSessionDao().delete(id)
            }
        }
    }
}
