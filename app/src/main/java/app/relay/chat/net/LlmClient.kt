package app.relay.chat.net

import app.relay.chat.data.ApiFormat
import app.relay.chat.data.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class Role { User, Assistant }

data class ChatTurn(val role: Role, val text: String)

sealed interface StreamEvent {
    data class Text(val delta: String) : StreamEvent
    data class Usage(val outputTokens: Int) : StreamEvent
    /** A tool call began: [name] is the tool (Bash, WebSearch, …), [input] a one-line summary. */
    data class ToolStart(val id: String, val name: String, val input: String) : StreamEvent
    data class ToolEnd(val id: String, val output: String, val isError: Boolean) : StreamEvent
}

/** [short] is what the UI shows in a row ("Key rejected (401)"); [message] carries server detail. */
class ApiException(val short: String, val code: Int? = null, detail: String? = null) :
    IOException(if (detail.isNullOrBlank()) short else "$short — $detail")

data class ModelsResult(val models: List<String>, val status: Int, val millis: Long, val path: String)

class LlmClient {
    private val base = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    private fun http(c: Connection) = base.newBuilder()
        .readTimeout(c.timeoutSeconds.coerceIn(5, 600).toLong(), TimeUnit.SECONDS)
        .build()

    private fun root(c: Connection) = c.baseUrl.trim().trimEnd('/')

    private fun Request.Builder.auth(c: Connection): Request.Builder {
        val key = c.apiKey.trim()
        when (c.format) {
            ApiFormat.OpenAI -> if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            ApiFormat.Anthropic -> {
                if (key.isNotEmpty()) header("x-api-key", key)
                header("anthropic-version", "2023-06-01")
            }
            ApiFormat.Gemini -> if (key.isNotEmpty()) header("x-goog-api-key", key)
        }
        c.headerPairs.forEach { (k, v) -> header(k, v) }
        return this
    }

    private fun modelsUrl(c: Connection) = when (c.format) {
        ApiFormat.OpenAI -> "${root(c)}/models"
        ApiFormat.Anthropic -> "${root(c)}/models?limit=1000"
        ApiFormat.Gemini -> "${root(c)}/models?pageSize=1000"
    }

    suspend fun listModels(c: Connection): ModelsResult = withContext(Dispatchers.IO) {
        val url = modelsUrl(c)
        val req = try {
            Request.Builder().url(url).auth(c).get().build()
        } catch (e: IllegalArgumentException) {
            throw ApiException("Invalid base URL")
        }
        val started = System.nanoTime()
        execute(http(c).newCall(req)).use { resp ->
            val ms = (System.nanoTime() - started) / 1_000_000
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, body)
            val obj = runCatching { JSONObject(body) }.getOrElse { throw ApiException("Unexpected response", resp.code) }
            val ids = when (c.format) {
                ApiFormat.Gemini -> obj.optJSONArray("models").objects()
                    .filter { m ->
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        methods == null || (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                    }
                    .map { it.getString("name").removePrefix("models/") }
                else -> obj.optJSONArray("data").objects().map { it.getString("id") }
            }
            ModelsResult(ids.distinct(), resp.code, ms, URI(url).path)
        }
    }

    /** Streams a completion. Cancelling the collector cancels the HTTP call. */
    fun streamChat(c: Connection, model: String, turns: List<ChatTurn>): Flow<StreamEvent> = callbackFlow {
        val client = http(c)
        var current: Call? = null
        val job = launch(Dispatchers.IO) {
            try {
                var includeUsage = true
                while (true) {
                    val call = client.newCall(chatRequest(c, model, turns, includeUsage))
                    current = call
                    execute(call).use { resp ->
                        if (!resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            // Some OpenAI-compatible servers reject stream_options; retry once without it.
                            if (resp.code == 400 && includeUsage && c.format == ApiFormat.OpenAI) {
                                includeUsage = false
                                return@use
                            }
                            throw httpError(resp.code, body)
                        }
                        includeUsage = false
                        readStream(c.format, resp) { channel.trySendBlocking(it) }
                        close()
                        return@launch
                    }
                }
            } catch (e: Throwable) {
                close(if (e is IOException && e !is ApiException) networkError(e) else e)
            }
        }
        awaitClose {
            current?.cancel()
            job.cancel()
        }
    }

    private fun chatRequest(c: Connection, model: String, turns: List<ChatTurn>, includeUsage: Boolean): Request {
        val (url, body) = when (c.format) {
            ApiFormat.OpenAI -> "${root(c)}/chat/completions" to JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("messages", JSONArray().apply {
                    turns.forEach { put(JSONObject().put("role", if (it.role == Role.User) "user" else "assistant").put("content", it.text)) }
                })
                .apply { if (includeUsage) put("stream_options", JSONObject().put("include_usage", true)) }
            ApiFormat.Anthropic -> "${root(c)}/messages" to JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("max_tokens", 4096)
                .put("messages", JSONArray().apply {
                    turns.forEach { put(JSONObject().put("role", if (it.role == Role.User) "user" else "assistant").put("content", it.text)) }
                })
            ApiFormat.Gemini -> "${root(c)}/models/$model:streamGenerateContent?alt=sse" to JSONObject()
                .put("contents", JSONArray().apply {
                    turns.forEach {
                        put(
                            JSONObject()
                                .put("role", if (it.role == Role.User) "user" else "model")
                                .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
                        )
                    }
                })
        }
        return try {
            Request.Builder().url(url).auth(c).header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody(json)).build()
        } catch (e: IllegalArgumentException) {
            throw ApiException("Invalid base URL")
        }
    }

    private fun readStream(format: ApiFormat, resp: Response, emit: (StreamEvent) -> Unit) {
        val body = resp.body ?: return
        // Servers that ignore "stream": true answer with one JSON document.
        if (body.contentType()?.subtype == "json") {
            val o = JSONObject(body.string())
            handleChunk(format, o, emit)
            o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.Text(it)) }
            return
        }
        val source = body.source()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.substring(5).trim()
            if (data.isEmpty()) continue
            if (data == "[DONE]") break
            val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
            handleChunk(format, o, emit)
            if (o.optString("type") == "message_stop") break
        }
    }

    private fun handleChunk(format: ApiFormat, o: JSONObject, emit: (StreamEvent) -> Unit) {
        o.optJSONObject("error")?.let { throw ApiException("Server error", detail = it.optString("message")) }
        when (format) {
            ApiFormat.OpenAI -> {
                o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.let { d ->
                    if (!d.isNull("content")) d.optString("content").takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.Text(it)) }
                }
                o.optJSONObject("usage")?.optInt("completion_tokens", -1)?.takeIf { it >= 0 }?.let { emit(StreamEvent.Usage(it)) }
            }
            ApiFormat.Anthropic -> when (o.optString("type")) {
                "content_block_delta" -> o.optJSONObject("delta")?.optString("text")
                    ?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.Text(it)) }
                "message_delta" -> o.optJSONObject("usage")?.optInt("output_tokens", -1)
                    ?.takeIf { it >= 0 }?.let { emit(StreamEvent.Usage(it)) }
            }
            ApiFormat.Gemini -> {
                o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    .objects().forEach { p -> p.optString("text").takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.Text(it)) } }
                o.optJSONObject("usageMetadata")?.optInt("candidatesTokenCount", -1)
                    ?.takeIf { it >= 0 }?.let { emit(StreamEvent.Usage(it)) }
            }
        }
    }

    private fun execute(call: Call): Response = try {
        call.execute()
    } catch (e: IOException) {
        throw networkError(e)
    }

    private fun networkError(e: IOException): ApiException = when (e) {
        is ApiException -> e
        is UnknownHostException -> ApiException("Unknown host", detail = e.message)
        is ConnectException -> ApiException("Can't reach server", detail = e.message)
        is SocketTimeoutException -> ApiException("Timed out")
        else -> ApiException("Network error", detail = e.message)
    }

    private fun httpError(code: Int, body: String): ApiException {
        val detail = runCatching {
            val o = JSONObject(body)
            o.optJSONObject("error")?.optString("message") ?: o.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(200)
        val short = when (code) {
            401, 403 -> "Key rejected ($code)"
            404 -> "Not found (404)"
            429 -> "Rate limited (429)"
            else -> "HTTP $code"
        }
        return ApiException(short, code, detail.take(300))
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
