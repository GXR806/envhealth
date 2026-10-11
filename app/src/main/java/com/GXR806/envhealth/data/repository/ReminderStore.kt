package com.GXR806.envhealth.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.GXR806.envhealth.domain.analysis.ReminderPolicy
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** 与设置共享同一 DataStore；提醒发送/响应由应用级维护锁串行协调。
 * notify 与 DataStore 无跨系统事务：先保存节流 claim 再发送；未知结果保留 claim 防重复。
 * 进程死亡可能损失一次通知。只有明确未发送才回滚，分母仅为成功发送并落库的事件。
 * 不存原始音频或图像。构造参数便于仪器测试使用独立临时 DataStore。
 */
class ReminderStore(private val store: DataStore<Preferences>) {
    data class State(
        val events: List<ReminderPolicy.Event>,
        val lastSent: Map<ReminderPolicy.Type, Long>,
        val quietUntil: Map<ReminderPolicy.Type, Long>
    )
    private val eventsKey = stringPreferencesKey("reminder_events_v1")
    private fun sentKey(type: ReminderPolicy.Type) = longPreferencesKey("reminder_sent_${type.name}")
    private fun quietKey(type: ReminderPolicy.Type) = longPreferencesKey("reminder_quiet_${type.name}")

    suspend fun read(): State {
        val p = store.data.first()
        return State(decode(p[eventsKey]), ReminderPolicy.Type.entries.mapNotNull { t -> p[sentKey(t)]?.let { t to it } }.toMap(),
            ReminderPolicy.Type.entries.mapNotNull { t -> p[quietKey(t)]?.let { t to it } }.toMap())
    }

    /** claim 只占节流，不加入响应率分母。未知 notify 结果保留 claim，至多一次。 */
    suspend fun claimSend(event: ReminderPolicy.Event) {
        store.edit { it[sentKey(event.type)] = event.sentAt }
    }
    suspend fun rollbackSend(event: ReminderPolicy.Event, previous: Long?) {
        store.edit { p ->
            if (p[sentKey(event.type)] == event.sentAt) {
                if (previous == null) p.remove(sentKey(event.type)) else p[sentKey(event.type)] = previous
            }
        }
    }

    /** 分母只记录通知 sink 真正接受发送的事件，未授权/关闭频道不调用本方法。 */
    suspend fun recordSent(event: ReminderPolicy.Event) {
        store.edit { p ->
            val events = decode(p[eventsKey])
            check(events.none { it.id == event.id }) { "重复提醒事件" }
            p[eventsKey] = encode(prune(events + event, event.sentAt))
            p[sentKey(event.type)] = event.sentAt
        }
    }

    /** 执行副作用前预留动作，采用至多一次，杀进程可能有预留无副作用，但不重放降音量。
     * effective 必须在执行成功、读回音量确实更低后才能写 true。
     */
    suspend fun claimAction(id: String, type: ReminderPolicy.Type, action: ReminderPolicy.Action, now: Long): ReminderPolicy.Event? {
        var claimed: ReminderPolicy.Event? = null
        store.edit { p ->
            val events = decode(p[eventsKey]).toMutableList()
            val index = events.indexOfFirst { it.id == id }
            if (index >= 0 && ReminderPolicy.validAction(events[index], type, now)) {
                val updated = events[index].copy(action = action, actionAt = now, effective = false)
                events[index] = updated
                p[eventsKey] = encode(events)
                if (action == ReminderPolicy.Action.MUTE) p[quietKey(type)] = now + ReminderPolicy.QUIET_MS
                // 稍后提醒不修改 lastSent，周期 Worker 在 24h 到期后重新检查风险。
                claimed = updated
            }
        }
        return claimed
    }

    suspend fun markEffective(id: String) {
        store.edit { p ->
            val events = decode(p[eventsKey]).map {
                if (it.id == id && it.action == ReminderPolicy.Action.LOWER_VOLUME) it.copy(effective = true) else it
            }
            p[eventsKey] = encode(events)
        }
    }

    suspend fun pruneExpired(now: Long) {
        store.edit { p -> p[eventsKey] = encode(prune(decode(p[eventsKey]), now)) }
    }

    private fun prune(events: List<ReminderPolicy.Event>, now: Long) = events.filter {
        // 时钟后退保留未来事件，避免失去节流/重放记录。
        it.sentAt > now || now - it.sentAt <= ReminderPolicy.HISTORY_MS
    }

    private fun encode(events: List<ReminderPolicy.Event>): String = JSONArray().apply {
        events.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("type", e.type.name); put("sentAt", e.sentAt)
            put("silent", e.silent); put("baselineVersion", e.baselineVersion)
            put("action", e.action?.name ?: JSONObject.NULL)
            put("actionAt", e.actionAt ?: JSONObject.NULL); put("effective", e.effective)
        }) }
    }.toString()

    private fun decode(raw: String?): List<ReminderPolicy.Event> {
        if (raw == null) return emptyList()
        // 数据损坏时抛出而非悄悄重置节流，调用方应 fail closed 并展示错误。
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val e = array.getJSONObject(i)
            ReminderPolicy.Event(e.getString("id"), ReminderPolicy.Type.valueOf(e.getString("type")),
                e.getLong("sentAt"), e.getBoolean("silent"), e.getLong("baselineVersion"),
                if (e.isNull("action")) null else ReminderPolicy.Action.valueOf(e.getString("action")),
                if (e.isNull("actionAt")) null else e.getLong("actionAt"), e.getBoolean("effective"))
        }
    }
}
