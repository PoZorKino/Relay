package app.relay.chat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.relay.chat.R

enum class ThemeId(val label: String) { Classic("Classic"), Midnight("Midnight"), Terminal("Terminal"), Lilac("Lilac") }

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, vararg extra: FontVariation.Setting) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

private fun family(res: Int, weights: List<Int>, vararg extra: FontVariation.Setting) =
    FontFamily(weights.map { variable(res, it, *extra) })

@OptIn(ExperimentalTextApi::class)
private val DmSans = family(R.font.dm_sans, listOf(400, 500, 600, 700), FontVariation.Setting("opsz", 14f))
@OptIn(ExperimentalTextApi::class)
private val Bricolage = family(R.font.bricolage_grotesque, listOf(500, 700), FontVariation.Setting("opsz", 32f))
private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)
private val Figtree = family(R.font.figtree, listOf(400, 500, 600, 700))
@OptIn(ExperimentalTextApi::class)
private val Fraunces = family(R.font.fraunces, listOf(500, 700), FontVariation.Setting("opsz", 72f))
private val JetBrainsMono = family(R.font.jetbrains_mono, listOf(400, 500, 700))

/**
 * One theme's tokens, taken from the design's boards (Classic = boards 1-4, then the
 * Midnight / Terminal / Lilac rows). Tokens the theme boards don't show (sheet handle,
 * error tint, …) are derived to sit in the same family.
 */
@Immutable
data class RelayPalette(
    val id: ThemeId,
    val dark: Boolean,
    val ground: Color,        // page background
    val ink: Color,           // primary text
    val muted: Color,         // secondary text
    val chevron: Color,       // row chevrons, placeholders
    val line: Color,          // borders
    val rowLine: Color,       // dividers inside cards
    val chip: Color,          // endpoint chip, search field, segmented track
    val raised: Color,        // selected segment
    val inlineCode: Color,    // `inline code` background
    val tileNeutral: Color,   // neutral icon tiles (key, A/B)
    val handle: Color,        // bottom-sheet handle
    val surface: Color,       // cards and inputs
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,    // accent used as text/outline (Add button, links)
    val accentOnTint: Color,  // badge + icon-tile foreground
    val accentDeep: Color,    // text on the success card
    val accentTint: Color,
    val amber: Color,
    val amberTint: Color,
    val error: Color,
    val errorTint: Color,
    val codeBg: Color,
    val codeText: Color,
    val codeBorder: Color?,
    val strongBg: Color,      // "Sign in" button
    val strongFg: Color,
    val scrim: Color,
    val display: FontFamily,
    val body: FontFamily,
    val mono: FontFamily,
)

val ClassicPalette = RelayPalette(
    id = ThemeId.Classic, dark = false,
    ground = Color(0xFFF6F4EE), ink = Color(0xFF1B1A17), muted = Color(0xFF5E5A52), chevron = Color(0xFF8A857A),
    line = Color(0xFFE3DFD4), rowLine = Color(0xFFEFEBE2), chip = Color(0xFFECE8DE), raised = Color.White,
    inlineCode = Color(0xFFECE8DE), tileNeutral = Color(0xFFECE8DE), handle = Color(0xFFD3CEC2), surface = Color.White,
    accent = Color(0xFF1F6F63), onAccent = Color.White, accentText = Color(0xFF1F6F63), accentOnTint = Color(0xFF1F6F63),
    accentDeep = Color(0xFF15524A), accentTint = Color(0xFFDCEDE9),
    amber = Color(0xFF8A5A12), amberTint = Color(0xFFF4E6CF), error = Color(0xFFA1401F), errorTint = Color(0xFFF6E3DA),
    codeBg = Color(0xFF1B1A17), codeText = Color(0xFFEDEAE2), codeBorder = null,
    strongBg = Color(0xFF1B1A17), strongFg = Color.White,
    // Over the ground this composites to exactly #8C877C, the backdrop on the picker board.
    scrim = Color(0x80221A0A),
    display = Bricolage, body = DmSans, mono = PlexMono,
)

val MidnightPalette = RelayPalette(
    id = ThemeId.Midnight, dark = true,
    ground = Color(0xFF111318), ink = Color(0xFFECEEF2), muted = Color(0xFF9BA1AD), chevron = Color(0xFF6E7482),
    line = Color(0xFF2A2E38), rowLine = Color(0xFF232730), chip = Color(0xFF1A1D24), raised = Color(0xFF2A2E38),
    inlineCode = Color(0xFF232730), tileNeutral = Color(0xFF232730), handle = Color(0xFF3A3F4A), surface = Color(0xFF1A1D24),
    accent = Color(0xFF7AA2FF), onAccent = Color(0xFF0E1320), accentText = Color(0xFFA9C1FF), accentOnTint = Color(0xFFA9C1FF),
    accentDeep = Color(0xFFA9C1FF), accentTint = Color(0xFF1F2A44),
    amber = Color(0xFFE8B86A), amberTint = Color(0xFF3A2E1A), error = Color(0xFFFF8A7A), errorTint = Color(0xFF3A1F1C),
    codeBg = Color(0xFF0B0D11), codeText = Color(0xFFD6DAE2), codeBorder = Color(0xFF232730),
    strongBg = Color(0xFFECEEF2), strongFg = Color(0xFF111318),
    scrim = Color(0x99000000),
    display = Bricolage, body = DmSans, mono = PlexMono,
)

val TerminalPalette = RelayPalette(
    id = ThemeId.Terminal, dark = true,
    ground = Color(0xFF0A0C0A), ink = Color(0xFFD8F5D0), muted = Color(0xFF7FA377), chevron = Color(0xFF4F6B4A),
    line = Color(0xFF1F2A1F), rowLine = Color(0xFF1F2A1F), chip = Color(0xFF111511), raised = Color(0xFF1F2A1F),
    inlineCode = Color(0xFF152015), tileNeutral = Color(0xFF152015), handle = Color(0xFF1F2A1F), surface = Color(0xFF111511),
    accent = Color(0xFF4BE37A), onAccent = Color(0xFF06120A), accentText = Color(0xFF4BE37A), accentOnTint = Color(0xFF4BE37A),
    accentDeep = Color(0xFF7CF0A0), accentTint = Color(0xFF0F1A12),
    amber = Color(0xFFF2C94C), amberTint = Color(0xFF221D0C), error = Color(0xFFFF6B5B), errorTint = Color(0xFF1C0F0D),
    codeBg = Color(0xFF111511), codeText = Color(0xFFB5F5C2), codeBorder = Color(0xFF1F2A1F),
    strongBg = Color(0xFF4BE37A), strongFg = Color(0xFF06120A),
    scrim = Color(0xB3000000),
    display = JetBrainsMono, body = JetBrainsMono, mono = JetBrainsMono,
)

val LilacPalette = RelayPalette(
    id = ThemeId.Lilac, dark = false,
    ground = Color(0xFFF3F0FA), ink = Color(0xFF1E1A2B), muted = Color(0xFF5F5873), chevron = Color(0xFF8C85A0),
    line = Color(0xFFE2DCF0), rowLine = Color(0xFFEFEBF7), chip = Color(0xFFE8E3F3), raised = Color.White,
    inlineCode = Color(0xFFE8E3F3), tileNeutral = Color(0xFFE8E3F3), handle = Color(0xFFD5CEE6), surface = Color.White,
    accent = Color(0xFF6B4FD8), onAccent = Color.White, accentText = Color(0xFF4A33A8), accentOnTint = Color(0xFF4A33A8),
    accentDeep = Color(0xFF36247F), accentTint = Color(0xFFE6E0FB),
    amber = Color(0xFF8A4B12), amberTint = Color(0xFFFBE7D4), error = Color(0xFFB42318), errorTint = Color(0xFFFDE8E6),
    codeBg = Color(0xFF1E1A2B), codeText = Color(0xFFEAE6F5), codeBorder = null,
    strongBg = Color(0xFF1E1A2B), strongFg = Color.White,
    scrim = Color(0x731E1A2B),
    display = Fraunces, body = Figtree, mono = PlexMono,
)

fun paletteFor(id: ThemeId) = when (id) {
    ThemeId.Classic -> ClassicPalette
    ThemeId.Midnight -> MidnightPalette
    ThemeId.Terminal -> TerminalPalette
    ThemeId.Lilac -> LilacPalette
}

/**
 * Current theme tokens. Backed by snapshot state, so any composable (or draw lambda)
 * that reads a token recomposes/redraws when the theme changes.
 */
object Relay {
    var palette by mutableStateOf(ClassicPalette)

    val theme get() = palette.id
    val isTerminal get() = palette.id == ThemeId.Terminal
    val isLilac get() = palette.id == ThemeId.Lilac

    val Ground get() = palette.ground
    val Ink get() = palette.ink
    val Muted get() = palette.muted
    val Chevron get() = palette.chevron
    val Line get() = palette.line
    val RowLine get() = palette.rowLine
    val Chip get() = palette.chip
    val Raised get() = palette.raised
    val InlineCode get() = palette.inlineCode
    val TileNeutral get() = palette.tileNeutral
    val Handle get() = palette.handle
    val Surface get() = palette.surface
    val Accent get() = palette.accent
    val OnAccent get() = palette.onAccent
    val AccentText get() = palette.accentText
    val AccentOnTint get() = palette.accentOnTint
    val AccentDeep get() = palette.accentDeep
    val AccentTint get() = palette.accentTint
    val Amber get() = palette.amber
    val AmberTint get() = palette.amberTint
    val Error get() = palette.error
    val ErrorTint get() = palette.errorTint
    val CodeBg get() = palette.codeBg
    val CodeText get() = palette.codeText
    val CodeBorder get() = palette.codeBorder
    val StrongBg get() = palette.strongBg
    val StrongFg get() = palette.strongFg
    val Scrim get() = palette.scrim
    val Display get() = palette.display
    val Body get() = palette.body
    val Mono get() = palette.mono

    /** Corner radius: Terminal squares everything off to 4dp (3dp for small controls). */
    fun r(dp: Dp): Dp = if (isTerminal) (if (dp <= 12.dp) 3.dp else 4.dp) else dp
    fun shape(dp: Dp) = RoundedCornerShape(r(dp))
    /** Fully round (pills, circles) except in Terminal. */
    val round get() = if (isTerminal) RoundedCornerShape(3.dp) else RoundedCornerShape(50)
}

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    val p = Relay.palette
    val scheme = if (p.dark) {
        darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.ground, onBackground = p.ink,
            surface = p.ground, onSurface = p.ink, error = p.error)
    } else {
        lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.ground, onBackground = p.ink,
            surface = p.ground, onSurface = p.ink, error = p.error)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
