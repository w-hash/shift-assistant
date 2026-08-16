package com.lh.shiftassistant.backup

import com.lh.shiftassistant.data.ShiftRepository
import kotlinx.serialization.json.Json

/** 备份导出/导入(JSON 字符串 <-> 数据库快照) */
class BackupManager(private val repository: ShiftRepository) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 导出为 JSON 文本 */
    suspend fun export(): String {
        val data = repository.exportSnapshot()
        return json.encodeToString(BackupData.serializer(), data)
    }

    /** 导入并整体还原;失败抛出异常(带原因) */
    suspend fun import(text: String) {
        val data = json.decodeFromString(BackupData.serializer(), text)
        repository.restoreSnapshot(data)
    }
}
