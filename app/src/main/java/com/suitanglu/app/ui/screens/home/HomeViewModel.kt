package com.suitanglu.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.Recording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeItem(
    val recording: Recording,
    val markerTotal: Int,
    val markerSuccess: Int,
    val markerFailed: Int,
    val subtitle: String,
    /** 小写化的可搜索文本：标题 + 课程名 + 标记文字 + AI 回复前 500 字 */
    val searchText: String,
)

data class HomeSection(val label: String, val items: List<HomeItem>)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppGraph.get(app)
    private val repo = container.recordingRepository

    /** 未筛选的全部录音，按天分区 */
    private val allSections: StateFlow<List<HomeSection>> =
        combine(
            container.db.recordingDao().observeActive(),
            container.db.markerDao().observeAll(),
            // 多轮追问也要能搜到：原来的索引只拼了 userText 和首轮 aiResponse，
            // 你在追问里问过、AI 答过的内容，搜"关键词"是查不出来的
            container.db.markerTurnDao().observeAll(),
        ) { recs, markers, turns ->
            val byRecording = markers.groupBy { it.recordingId }
            val turnsByMarker = turns.groupBy { it.markerId }
            val items = recs.map { rec ->
                val ms = byRecording[rec.id].orEmpty()
                val success = ms.count { it.aiStatus == Marker.AI_SUCCESS }
                val failed = ms.count { it.aiStatus == Marker.AI_FAILED }
                HomeItem(
                    recording = rec,
                    markerTotal = ms.size,
                    markerSuccess = success,
                    markerFailed = failed,
                    subtitle = repo.recordingSubtitle(rec, ms),
                    searchText = buildString {
                        append(rec.title).append(' ')
                        rec.courseName?.let { append(it).append(' ') }
                        ms.forEach { m ->
                            m.userText?.let { append(it).append(' ') }
                            m.aiResponse?.let { append(it).append(' ') }
                            // 全量进索引，不截断：宁可多占点内存，也不要"明明说过却搜不到"。
                            // 真到数据量撑不住时再上 FTS，那时检索走 SQL，内存里就不用留这份副本了。
                            turnsByMarker[m.id]?.forEach { t ->
                                t.question?.let { append(it).append(' ') }
                                t.answer?.let { append(it).append(' ') }
                            }
                        }
                    }.lowercase(),
                )
            }
            items.groupBy { Formatters.dateLabel(it.recording.startTime) }
                .map { (label, list) -> HomeSection(label, list) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _courseFilter = MutableStateFlow<String?>(null)

    private val _query = MutableStateFlow("")

    /** 搜索框内容 */
    val searchQuery: StateFlow<String> = _query

    /** null = 全部；否则只显示该课程 */
    val courseFilter: StateFlow<String?> = _courseFilter

    private val courseFiltered: StateFlow<List<HomeSection>> =
        combine(allSections, _courseFilter) { sections, course ->
            if (course == null) {
                sections
            } else {
                sections.mapNotNull { section ->
                    val items = section.items.filter { it.recording.courseName == course }
                    if (items.isEmpty()) null else section.copy(items = items)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 筛选器选项：只列出真正出现过的课程名（按名称排序）。
     * 依据的是录音命中的课程名，而不是用户改过的展示标题——改标题后仍能按课程筛。
     */
    val courses: StateFlow<List<String>> = allSections
        .map { sections ->
            sections.asSequence()
                .flatMap { it.items.asSequence() }
                .mapNotNull { it.recording.courseName?.takeIf(String::isNotBlank) }
                .distinct()
                .sorted()
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sections: StateFlow<List<HomeSection>> = combine(courseFiltered, _query) { sections, rawQuery ->
        val needle = rawQuery.trim().lowercase()
        if (needle.isEmpty()) {
            sections
        } else {
            // 命中标题、课程名或任一标记的文字/讲解
            sections.mapNotNull { section ->
                val items = section.items.filter { it.searchText.contains(needle) }
                if (items.isEmpty()) null else section.copy(items = items)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCourseFilter(course: String?) {
        _courseFilter.value = course
    }

    /**
     * 一条录音都没有（未筛选）。空状态要把搜索框、筛选器这些"没有内容可作用"的控件藏起来。
     * 不能用 sections.isEmpty() 判断：搜索无结果时它也是空的，那时搜索框必须留着让人清空。
     */
    val noRecordingsAtAll: StateFlow<Boolean> = allSections
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setQuery(query: String) {
        _query.value = query
    }

    val firstDeleteTipShown: StateFlow<Boolean> = container.settings.generalConfig
        .map { it.firstDeleteTipShown }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun rename(id: Long, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.rename(id, name)) }
    }

    fun deleteToRecycle(id: Long) {
        viewModelScope.launch { repo.deleteToRecycle(id) }
    }

    fun deleteToRecycleMany(ids: Collection<Long>) {
        viewModelScope.launch { ids.forEach { repo.deleteToRecycle(it) } }
    }

    /** 多选时批量仅删音频：保留标记、图片与 AI 回复 */
    fun deleteAudioOnlyMany(ids: Collection<Long>) {
        viewModelScope.launch {
            ids.forEach { runCatching { repo.deleteAudioOnly(it) } }
        }
    }

    fun deleteAudioOnly(id: Long) {
        viewModelScope.launch { repo.deleteAudioOnly(id) }
    }

    fun markDeleteTipShown() {
        viewModelScope.launch {
            val g = container.settings.generalConfigNow()
            container.settings.setGeneralConfig(g.copy(firstDeleteTipShown = true))
        }
    }
}
