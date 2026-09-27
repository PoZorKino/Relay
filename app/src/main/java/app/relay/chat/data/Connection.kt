package app.relay.chat.data

import java.net.URI

enum class ApiFormat(val label: String, val compatLabel: String, val defaultBaseUrl: String) {
    OpenAI("OpenAI-style", "OpenAI-compatible", "https://api.openai.com/v1"),
    Anthropic("Anthropic-style", "Anthropic-compatible", "https://api.anthropic.com/v1"),
    Gemini("Gemini-style", "Gemini-compatible", "https://generativelanguage.googleapis.com/v1beta"),
}

/**
 * A subscription reached through its vendor's official CLI running on-device
 * (see runtime/PlanService). [binPath] is inside the bundled Alpine rootfs.
 */
enum class Plan(val connectionId: String, val title: String, val cli: String, val letter: String, val binPath: String, val models: List<String>) {
    // Current model IDs, then Claude Code's aliases (which track the latest of each family).
    // Which ones work depends on the plan; an unavailable one fails with the CLI's message.
    Claude(
        "plan-claude", "Claude plan", "Claude Code", "C", "/root/.local/bin/claude",
        listOf(
            "claude-sonnet-5", "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-haiku-4-5",
            "claude-opus-4-8", "claude-sonnet-4-6", "opus", "sonnet", "haiku",
        ),
    ),
    // Fallback only: after sign-in the list comes from the installed CLI (`codex debug models`).
    // Lineup per learn.chatgpt.com/docs/models (Sep 2026).
    Codex(
        "plan-codex", "ChatGPT plan", "Codex", "G", "/usr/local/bin/codex",
        listOf("gpt-6-astra", "gpt-6-sol", "gpt-6-luna", "gpt-5.5"),
    ),
}

data class Connection(
    val id: String,
    val name: String,
    val format: ApiFormat,
    val baseUrl: String,
    val apiKey: String,
    /** Raw "Header: value" lines from the Advanced section. */
    val headers: String = "",
    val timeoutSeconds: Int = 60,
    /** Comma/newline separated ids used when the server has no /models endpoint. */
    val manualModels: String = "",
    /** Last list fetched from the server (or the manual list). */
    val models: List<String> = emptyList(),
    /** Short human error from the last failed call, e.g. "Key rejected (401)". */
    val lastError: String? = null,
    /** Non-null for a subscription connection (no base URL / key; runs the CLI). */
    val plan: Plan? = null,
) {
    val usesDefaultBaseUrl: Boolean
        get() = baseUrl.trimEnd('/') == format.defaultBaseUrl

    val isLocal: Boolean
        get() {
            val host = runCatching { URI(baseUrl.trim()).host }.getOrNull()?.lowercase() ?: return false
            if (host == "localhost" || host.endsWith(".local") || host.startsWith("127.") || host.startsWith("10.") ||
                host.startsWith("192.168.") || host == "10.0.2.2"
            ) return true
            val m = Regex("""^172\.(\d+)\.""").find(host) ?: return false
            return m.groupValues[1].toInt() in 16..31
        }

    val maskedKey: String
        get() = if (apiKey.length <= 6) "••••" else apiKey.take(3) + "••••" + apiKey.takeLast(3)

    val manualModelList: List<String>
        get() = manualModels.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    val headerPairs: List<Pair<String, String>>
        get() = headers.lines().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.filter { it.first.isNotEmpty() }
}

/** Currently selected model, addressed by the connection that serves it. */
data class ModelRef(val connectionId: String, val model: String)
