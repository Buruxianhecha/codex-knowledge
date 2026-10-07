package com.cleo.cleos.ui.settings

import com.cleo.cleos.data.db.CompanionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** An immutable selection captured when Save is pressed, including the TA it belongs to. */
internal data class ModelSelection(
    val companionId: Long,
    val baseUrl: String,
    val model: String,
    val spokenOn: Boolean,
    val spokenBaseUrl: String,
    val spokenModel: String,
) {
    fun normalized(): ModelSelection = copy(
        baseUrl = baseUrl.trim(), model = model.trim(),
        spokenBaseUrl = spokenBaseUrl.trim(), spokenModel = spokenModel.trim(),
    )

    fun validate() {
        require(companionId > 0) { "资料还没有加载好，请稍后再保存" }
        require(baseUrl.isNotBlank() && model.isNotBlank()) { "请先填写接口地址并选择模型" }
        require(!spokenOn || (spokenBaseUrl.isNotBlank() && spokenModel.isNotBlank())) {
            "请先填好电话和语音的接口地址、模型，或关闭单独模型开关"
        }
    }

    fun applyTo(ta: CompanionEntity): CompanionEntity {
        check(ta.id == companionId) { "当前 TA 已改变，请重新打开模型设置" }
        return ta.copy(
            apiBaseUrl = baseUrl, apiModel = model,
            spokenModelOn = spokenOn, spokenApiBaseUrl = spokenBaseUrl, spokenApiModel = spokenModel,
        )
    }

    fun matches(ta: CompanionEntity): Boolean =
        ta.id == companionId && ta.apiBaseUrl == baseUrl && ta.apiModel == model &&
            ta.spokenModelOn == spokenOn && ta.spokenApiBaseUrl == spokenBaseUrl && ta.spokenApiModel == spokenModel
}

/** Serialize automatic writes and explicit commits; returning from commit means readback passed. */
internal class ModelSettingsWriter(
    private val saveKey: suspend (String, String) -> Unit,
    private val update: suspend (Long, (CompanionEntity) -> CompanionEntity) -> Unit,
    private val read: suspend (Long) -> CompanionEntity?,
) {
    private val lock = Mutex()

    suspend fun serially(block: suspend () -> Unit) = lock.withLock { block() }

    suspend fun commit(selection: ModelSelection, pendingKeys: List<Pair<String, String>>) {
        selection.validate()
        lock.withLock {
            for ((address, key) in pendingKeys) saveKey(address, key)
            update(selection.companionId, selection::applyTo)
            val stored = read(selection.companionId)
                ?: error("当前 TA 已不存在，模型没有保存，请重新打开设置")
            check(selection.matches(stored)) { "模型尚未保存成功，请重试" }
        }
    }
}
