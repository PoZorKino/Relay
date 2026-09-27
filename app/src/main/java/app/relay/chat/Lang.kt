package app.relay.chat

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import app.relay.chat.data.ApiFormat
import app.relay.chat.data.Plan
import java.util.Locale

/**
 * App language: follows the system unless the user picked one in Connections. Applied by
 * wrapping the activity's base context (MainActivity.attachBaseContext); works on every
 * API level without AppCompat.
 */
object Lang {
    /** Tag → name shown in the picker ("" = follow the system; its label is localized). */
    val options = listOf("" to null, "en" to "English", "ru" to "Русский", "uk" to "Українська")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("relay", Context.MODE_PRIVATE)

    fun current(ctx: Context): String = prefs(ctx).getString("lang", "") ?: ""

    fun set(ctx: Context, tag: String) = prefs(ctx).edit().putString("lang", tag).commit()

    fun wrap(ctx: Context): Context {
        val tag = current(ctx)
        if (tag.isEmpty()) return ctx
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(ctx.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return ctx.createConfigurationContext(config)
    }
}

fun Resources.plural(id: Int, n: Int): String = getQuantityString(id, n, n)

fun planTitle(res: Resources, plan: Plan): String =
    res.getString(if (plan == Plan.Claude) R.string.plan_claude else R.string.plan_codex)

fun formatLabel(res: Resources, f: ApiFormat): String = res.getString(
    when (f) {
        ApiFormat.OpenAI -> R.string.format_openai
        ApiFormat.Anthropic -> R.string.format_anthropic
        ApiFormat.Gemini -> R.string.format_gemini
    }
)

fun compatLabel(res: Resources, f: ApiFormat): String = res.getString(
    when (f) {
        ApiFormat.OpenAI -> R.string.compat_openai
        ApiFormat.Anthropic -> R.string.compat_anthropic
        ApiFormat.Gemini -> R.string.compat_gemini
    }
)

/**
 * Errors are produced (and persisted, e.g. a connection's lastError) in English; this
 * translates the known short forms at display time. "Short — server detail" keeps the
 * server's own detail text as is.
 */
fun localizeError(res: Resources, message: String): String {
    val short = message.substringBefore(" — ")
    val rest = message.substringAfter(" — ", "")
    val code = Regex("""\((\d{3})\)$""").find(short)?.groupValues?.get(1)
    val loc = when {
        short.startsWith("Key rejected") && code != null -> res.getString(R.string.err_key_rejected, code)
        short == "Not found (404)" -> res.getString(R.string.err_not_found)
        short == "Rate limited (429)" -> res.getString(R.string.err_rate_limited)
        Regex("""HTTP \d+""").matches(short) -> res.getString(R.string.err_http, short.removePrefix("HTTP "))
        short == "Unknown host" -> res.getString(R.string.err_unknown_host)
        short == "Can't reach server" -> res.getString(R.string.err_cant_reach)
        short == "Timed out" -> res.getString(R.string.err_timed_out)
        short == "Network error" -> res.getString(R.string.err_network)
        short == "Invalid base URL" -> res.getString(R.string.err_invalid_url)
        short == "Unexpected response" -> res.getString(R.string.err_unexpected)
        short == "Signed out" -> res.getString(R.string.err_signed_out)
        short == "Server error" -> res.getString(R.string.err_server)
        short == "Couldn't load models" -> res.getString(R.string.err_load_models)
        short == "Sign-in didn't complete." -> res.getString(R.string.sign_in_incomplete)
        short == "Failed" -> res.getString(R.string.failed)
        short.endsWith(" error") && Plan.entries.any { short == "${it.cli} error" } ->
            res.getString(R.string.err_cli, short.removeSuffix(" error"))
        else -> short
    }
    return if (rest.isEmpty()) loc else "$loc — $rest"
}
