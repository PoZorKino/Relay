package app.relay.chat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatSummary(val id: String, val title: String, val updatedAt: Long)

data class StoredTool(val id: String, val name: String, val detail: String, val anchor: Int, val output: String?, val isError: Boolean)

/** One stored turn; mirrors UiMessage's persistent fields. */
data class StoredMessage(
    val user: Boolean,
    val text: String,
    val display: String,
    val tokens: Int?,
    val seconds: Double?,
    val connectionName: String?,
    val model: String?,
    val error: String?,
    val tools: List<StoredTool> = emptyList(),
)

/**
 * Chat history: files/chats/<id>.json per conversation plus index.json (id, title,
 * updatedAt) so the drawer can list chats without reading every file.
 */
class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val prefs = context.getSharedPreferences("relay", Context.MODE_PRIVATE)

    fun list(): List<ChatSummary> = runCatching {
        val arr = JSONArray(index.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ChatSummary(o.getString("id"), o.getString("title"), o.getLong("updatedAt"))
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun load(id: String): List<StoredMessage>? = runCatching {
        val arr = JSONObject(File(dir, "$id.json").readText()).getJSONArray("messages")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            StoredMessage(
                user = o.getString("role") == "user",
                text = o.getString("text"),
                display = o.optString("display", o.getString("text")),
                tokens = o.optInt("tokens", -1).takeIf { t -> t >= 0 },
                seconds = o.optDouble("seconds").takeUnless { s -> s.isNaN() },
                connectionName = o.optString("conn").ifEmpty { null },
                model = o.optString("model").ifEmpty { null },
                error = o.optString("error").ifEmpty { null },
                tools = o.optJSONArray("tools")?.let { ta ->
                    (0 until ta.length()).map { j ->
                        val t = ta.getJSONObject(j)
                        StoredTool(
                            t.getString("id"), t.getString("name"), t.optString("detail"), t.optInt("anchor"),
                            if (t.has("output")) t.getString("output") else null, t.optBoolean("error"),
                        )
                    }
                } ?: emptyList(),
            )
        }
    }.getOrNull()

    fun save(id: String, title: String, messages: List<StoredMessage>) {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(
                JSONObject()
                    .put("role", if (m.user) "user" else "assistant")
                    .put("text", m.text)
                    .put("display", m.display)
                    .apply {
                        m.tokens?.let { put("tokens", it) }
                        m.seconds?.let { put("seconds", it) }
                        m.connectionName?.let { put("conn", it) }
                        m.model?.let { put("model", it) }
                        m.error?.let { put("error", it) }
                        if (m.tools.isNotEmpty()) put("tools", JSONArray().apply {
                            m.tools.forEach { t ->
                                put(JSONObject().put("id", t.id).put("name", t.name).put("detail", t.detail)
                                    .put("anchor", t.anchor).put("error", t.isError)
                                    .apply { t.output?.let { o -> put("output", o) } })
                            }
                        })
                    }
            )
        }
        File(dir, "$id.json").writeText(JSONObject().put("id", id).put("messages", arr).toString())
        writeIndex(list().filterNot { it.id == id } + ChatSummary(id, title, System.currentTimeMillis()))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        writeIndex(list().filterNot { it.id == id })
        if (lastChatId == id) lastChatId = null
    }

    private fun writeIndex(chats: List<ChatSummary>) {
        val arr = JSONArray()
        chats.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("updatedAt", it.updatedAt)) }
        // Write-then-rename so a crash mid-write can't leave a truncated index.
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(index)
    }

    /** The chat to reopen on launch. */
    var lastChatId: String?
        get() = prefs.getString("lastChat", null)
        set(v) = prefs.edit().putString("lastChat", v).apply()
}
