package com.suitanglu.app.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.suitanglu.app.data.AiConfig
import com.suitanglu.app.data.GeneralConfig
import com.suitanglu.app.data.SettingsRepository
import com.suitanglu.app.data.db.AppDatabase
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.MarkerImage
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class ManifestFileEntry(val path: String, val hash: String, val size: Long)

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String,
    val files: List<ManifestFileEntry> = emptyList(),
)

@Serializable
data class SettingsJson(
    val courseNames: Map<String, String> = emptyMap(), // "day_slot" -> name
    val slotTimes: Map<String, SlotRange> = emptyMap(), // "day_index" -> 起止分钟
    val ai: AiConfig = AiConfig(),
    val general: GeneralConfig = GeneralConfig(),
)

@Serializable
data class SlotRange(val startMinute: Int, val endMinute: Int)

@Serializable
data class SingleRecordingJson(
    val rec: Recording,
    val markers: List<Marker> = emptyList(),
)

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun progressSink(onProgress: (String) -> Unit) = onProgress

    /** 导出为 zip：manifest.json + database/app.db + recordings/ + images/ + settings.json（含 API Key，不加密） */
    suspend fun exportTo(uri: Uri, onProgress: (String) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        try {
            onProgress("正在整理数据…")
            db.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use { it.moveToFirst() }

            val dbFile = context.getDatabasePath("app.db")
            val recordings = db.recordingDao().getAll()
            val images = db.markerImageDao().getAll()
            val general = settings.generalConfigNow()
            val ai = settings.aiConfigNow()
            val courses = mutableMapOf<String, String>()
            for (d in 1..7) for (s in 1..5) courses["${d}_$s"] = settings.courseNameNow(d, s)
            val slotTimes = mutableMapOf<String, SlotRange>()
            for (d in 1..7) {
                for (s in settings.slotsNow(d)) {
                    slotTimes["${d}_${s.index}"] = SlotRange(s.startMinute, s.endMinute)
                }
            }

            val entries = mutableListOf<ManifestFileEntry>()
            context.contentResolver.openOutputStream(uri)?.use { rawOut ->
                ZipOutputStream(rawOut.buffered()).use { zip ->
                    fun putFile(entryPath: String, file: File) {
                        if (!file.exists()) return
                        zip.putNextEntry(ZipEntry(entryPath))
                        file.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                        entries.add(ManifestFileEntry(entryPath, HashUtil.sha256(file), file.length()))
                    }

                    fun writeManifest(files: List<ManifestFileEntry>) {
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(
                            json.encodeToString(
                                BackupManifest.serializer(),
                                BackupManifest(
                                    exportedAt = System.currentTimeMillis(),
                                    appVersion = appVersion(),
                                    files = files,
                                ),
                            ).toByteArray(),
                        )
                        zip.closeEntry()
                    }

                    onProgress("正在写入录音文件…")
                    var i = 0
                    for (rec in recordings) {
                        val f = File(rec.filePath)
                        if (f.exists() && rec.filePath.isNotBlank()) {
                            val name = "recordings/${f.name}"
                            putFile(name, f)
                        }
                        i++
                        if (i % 3 == 0) onProgress("录音 $i/${recordings.size} …")
                    }

                    onProgress("正在写入图片…")
                    i = 0
                    for (img in images) {
                        val f = File(img.filePath)
                        if (f.exists()) putFile("images/${f.name}", f)
                        i++
                        if (i % 10 == 0) onProgress("图片 $i/${images.size} …")
                    }

                    onProgress("正在写入设置…")
                    zip.putNextEntry(ZipEntry("settings.json"))
                    zip.write(
                        json.encodeToString(
                            SettingsJson.serializer(),
                            SettingsJson(courseNames = courses, slotTimes = slotTimes, ai = ai, general = general),
                        ).toByteArray(),
                    )
                    zip.closeEntry()

                    // 数据库放在最后写，最小化导出与进行中写入的偏差窗口；
                    // 连同 wal 一起导出，保证导出后的写入不丢失
                    onProgress("正在写入数据库…")
                    zip.putNextEntry(ZipEntry("database/app.db"))
                    dbFile.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                    val walFile = File(dbFile.absolutePath + "-wal")
                    if (walFile.exists() && walFile.length() > 0) {
                        putFile("database/app.db-wal", walFile)
                    }

                    // 文件清单收集完毕后写入 manifest
                    writeManifest(entries.toList())
                }
            } ?: return@withContext Result.failure(IllegalStateException("无法写入目标文件"))
            onProgress("导出完成")
            Result.success(entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 导入（合并模式）：相同 Hash 跳过；Hash 不同新增并重命名 `_导入_N` */
    suspend fun importFrom(uri: Uri, onProgress: (String) -> Unit): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        val uniqueName = "import_${System.currentTimeMillis()}.db"
        try {
            tempDir.mkdirs()
            onProgress("正在解压备份…")
            context.contentResolver.openInputStream(uri)?.use { rawIn ->
                ZipInputStream(rawIn.buffered()).use { zip ->
                    while (true) {
                        val entry: ZipEntry = zip.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val target = File(tempDir, entry.name).canonicalFile
                            if (!target.path.startsWith(tempDir.canonicalPath)) continue // 防 zip 路径穿越
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("无法读取备份文件"))

            val importedDbFile = File(tempDir, "database/app.db")
            if (!importedDbFile.exists()) return@withContext Result.failure(IllegalStateException("备份中缺少数据库文件"))

            // 备份可能带出未 checkpoint 的 wal：先打开再关闭，让 SQLite 把 wal 合并进主库
            if (File(tempDir, "database/app.db-wal").exists()) {
                runCatching {
                    val raw = SQLiteDatabase.openDatabase(
                        importedDbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE,
                    )
                    raw.close()
                }
            }

            // 应用 settings.json（覆盖当前设置）
            val settingsFile = File(tempDir, "settings.json")
            if (settingsFile.exists()) {
                onProgress("正在恢复设置…")
                runCatching {
                    val s = json.decodeFromString(SettingsJson.serializer(), settingsFile.readText())
                    settings.setAllCourseNames(
                        s.courseNames.mapKeys { (k, _) ->
                            val p = k.split("_")
                            (p[0].toIntOrNull() ?: 0) to (p[1].toIntOrNull() ?: 0)
                        },
                    )
                    for ((k, v) in s.slotTimes) {
                        val p = k.split("_")
                        val day = p.getOrNull(0)?.toIntOrNull() ?: continue
                        val idx = p.getOrNull(1)?.toIntOrNull() ?: continue
                        if (day in 1..7 && idx in 1..5) {
                            settings.setSlotTime(day, idx, v.startMinute, v.endMinute)
                        }
                    }
                    // 备份里的 Key 为空时保留本地：用户可能是先导出、后配置的 Key，
                    // 直接覆盖会把已经填好的 Key 清空，下次录音才发现 AI 全失败
                    val localAi = settings.aiConfigNow()
                    val restoredAi = if (s.ai.apiKey.isBlank()) s.ai.copy(apiKey = localAi.apiKey) else s.ai
                    settings.setAiConfig(restoredAi)
                    settings.setGeneralConfig(s.general)
                }
            }

            onProgress("正在打开备份数据库…")
            val importedDb = openImportedDb(importedDbFile, uniqueName)
            val summary = try {
                merge(importedDb, tempDir, onProgress)
            } finally {
                importedDb.close()
            }
            onProgress("导入完成")
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
            // createFromFile 会把备份库复制进应用数据库目录，用完必须删除，否则每次导入泄漏一份
            listOf("", "-wal", "-shm").forEach { suffix ->
                File(context.getDatabasePath(uniqueName).absolutePath + suffix).delete()
            }
        }
    }

    private fun openImportedDb(file: File, name: String): AppDatabase {
        // 用不冲突的名字打开；createFromFilePath 会把备份库复制到应用数据库目录。
        // 必须带上迁移：备份可能来自旧版本，缺迁移会直接抛异常，表现为"导入失败"
        return androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, name)
            .createFromFile(file)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun merge(importedDb: AppDatabase, tempDir: File, onProgress: (String) -> Unit): ImportSummary {
        val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
        var skipped = 0
        var added = 0
        var renamedFiles = 0

        val importedRecordings = importedDb.recordingDao().getAll()
        val existingHashes = db.recordingDao().getAll().mapNotNull { it.fileHash }.toHashSet()
        // 无文件的录音没有 Hash，用元数据去重，避免每次导入都重复新增
        val existingMeta = db.recordingDao().getAll()
            .map { Triple(it.startTime, it.title, it.durationMs) }.toHashSet()

        for ((index, rec) in importedRecordings.withIndex()) {
            onProgress("合并录音 ${index + 1}/${importedRecordings.size} …")
            val recHash = rec.fileHash ?: rec.filePath.takeIf { it.isNotBlank() }
                ?.let { p -> File(tempDir, "recordings/${File(p).name}").takeIf { it.exists() }?.let { HashUtil.sha256(it) } }

            // 已有相同 Hash 的录音 → 跳过
            if (recHash != null && recHash in existingHashes) {
                skipped++
                continue
            }
            if (recHash == null && Triple(rec.startTime, rec.title, rec.durationMs) in existingMeta) {
                skipped++
                continue
            }

            db.withTransaction {
                // 复制录音文件
                var newPath = ""
                val srcRec = rec.filePath.takeIf { it.isNotBlank() }?.let { File(tempDir, "recordings/${File(it).name}") }
                if (srcRec != null && srcRec.exists()) {
                    val localHash = HashUtil.sha256(srcRec)
                    val same = db.recordingDao().getByHash(localHash)
                    newPath = if (same != null) {
                        same.filePath // 已有相同文件，指向本地
                    } else {
                        copyWithRename(srcRec, recordingsDir, rec.startTime)?.also { renamedFiles++ } ?: ""
                    }
                }

                val newRecId = db.recordingDao().insert(
                    rec.copy(id = 0, filePath = newPath, fileHash = recHash),
                )
                existingHashes.add(recHash ?: "")
                existingMeta.add(Triple(rec.startTime, rec.title, rec.durationMs))

                // 合并标记
                val idMap = HashMap<Long, Long>()
                for (marker in importedDb.markerDao().getByRecording(rec.id)) {
                    val newMarkerId = db.markerDao().insert(
                        marker.copy(id = 0, recordingId = newRecId, updatedAt = System.currentTimeMillis()),
                    )
                    idMap[marker.id] = newMarkerId
                    val images = importedDb.markerImageDao().getByMarker(marker.id)
                    val newImages = mutableListOf<MarkerImage>()
                    for (img in images) {
                        val srcImg = File(tempDir, "images/${File(img.filePath).name}")
                        var imgPath = ""
                        if (srcImg.exists()) {
                            imgPath = copyWithRename(srcImg, imagesDir, marker.createdAt) ?: ""
                        }
                        newImages.add(img.copy(id = 0, markerId = newMarkerId, filePath = imgPath))
                    }
                    if (newImages.isNotEmpty()) db.markerImageDao().insertAll(newImages)
                }

                for (event in importedDb.pauseEventDao().getAll().filter { it.recordingId == rec.id }) {
                    db.pauseEventDao().insert(event.copy(id = 0, recordingId = newRecId))
                }

                // 多轮追问：之前漏了这张表，导入后只剩首轮 aiResponse，
                // 所有追问和 AI 的回答都会消失
                for (turn in importedDb.markerTurnDao().getByRecording(rec.id)) {
                    val newMarkerId = idMap[turn.markerId] ?: continue
                    db.markerTurnDao().insert(turn.copy(id = 0, markerId = newMarkerId))
                }

                // 整节汇总与整节对话（v3 新增）
                importedDb.recordingDigestDao().get(rec.id)?.let { digest ->
                    db.recordingDigestDao().upsert(digest.copy(recordingId = newRecId))
                }
                for (chat in importedDb.recordingChatDao().getAll(rec.id)) {
                    db.recordingChatDao().insert(chat.copy(id = 0, recordingId = newRecId))
                }
            }
            added++
        }
        return ImportSummary(total = importedRecordings.size, added = added, skipped = skipped, renamedFiles = renamedFiles)
    }

    /** 复制文件到目标目录：名称冲突且 Hash 不同时追加 `_导入_N` */
    private fun copyWithRename(src: File, targetDir: File, seedTime: Long): String? {
        var name = src.name
        var i = 1
        while (true) {
            val target = File(targetDir, name)
            if (!target.exists()) {
                src.copyTo(target)
                return target.absolutePath
            }
            if (HashUtil.sha256(target) == HashUtil.sha256(src)) {
                return target.absolutePath // 相同文件，无需复制
            }
            val dot = src.name.lastIndexOf('.')
            val base = if (dot > 0) src.name.substring(0, dot) else src.name
            val ext = if (dot > 0) src.name.substring(dot) else ""
            name = "${base}_导入_$i$ext"
            i++
        }
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"

    /** 导出单条录音（音频 + 图片 + 元数据），用于手工备份单条 */
    suspend fun exportSingle(recordingId: Long, uri: Uri, onProgress: (String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val rec = db.recordingDao().getById(recordingId)
                    ?: return@withContext Result.failure(IllegalStateException("录音不存在"))
                val markers = db.markerDao().getByRecording(recordingId)
                context.contentResolver.openOutputStream(uri)?.use { rawOut ->
                    ZipOutputStream(rawOut.buffered()).use { zip ->
                        val audio = File(rec.filePath)
                        if (audio.exists()) {
                            zip.putNextEntry(ZipEntry("recordings/${audio.name}"))
                            audio.inputStream().buffered().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                        for (m in markers) {
                            for (img in db.markerImageDao().getByMarker(m.id)) {
                                val f = File(img.filePath)
                                if (f.exists()) {
                                    zip.putNextEntry(ZipEntry("images/${f.name}"))
                                    f.inputStream().buffered().use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        }
                        val meta = SingleRecordingJson(rec = rec, markers = markers)
                        zip.putNextEntry(ZipEntry("recording.json"))
                        zip.write(json.encodeToString(SingleRecordingJson.serializer(), meta).toByteArray())
                        zip.closeEntry()
                    }
                } ?: return@withContext Result.failure(IllegalStateException("无法写入目标文件"))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

data class ImportSummary(val total: Int, val added: Int, val skipped: Int, val renamedFiles: Int) {
    fun message(): String = "共 $total 条录音：新增 $added 条，Hash 相同跳过 $skipped 条（重命名文件 $renamedFiles 个）"
}
