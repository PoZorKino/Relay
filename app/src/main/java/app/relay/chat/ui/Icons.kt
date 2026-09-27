package app.relay.chat.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Stroke icons ported from the design's inline SVGs (24x24 viewBox, round caps).
 * `<circle>`/`<rect>` elements are rewritten as equivalent path data, and arc flags are
 * written out (`0 0 1 3 3`, not `0 013 3`): Compose's parser doesn't split packed flags.
 */
object RelayIcons {
    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = addPathNodes(d), fill = SolidColor(androidx.compose.ui.graphics.Color.Black))
        }.build()

    private const val SERVER_BLOCK_TOP = "M5 4h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
    private const val SERVER_BLOCK_BOTTOM = "M5 13h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z"

    val Menu = stroke("menu", 1.8f, "M4 7h16M4 12h16M4 17h10")
    val ChevronDown = stroke("chevronDown", 2f, "M6 9l6 6 6-6")
    val ChevronRight = stroke("chevronRight", 2f, "M9 6l6 6-6 6")
    val Back = stroke("back", 1.8f, "M15 18l-6-6 6-6")
    val NewChat = stroke("newChat", 1.8f, "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val Plus = stroke("plus", 1.8f, "M12 5v14M5 12h14")
    val PlusBold = stroke("plusBold", 2f, "M12 5v14M5 12h14")
    val ArrowUp = stroke("arrowUp", 2f, "M12 19V5M5 12l7-7 7 7")
    val Stop = filled("stop", "M8 7h8a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z")
    val Server = stroke("server", 1.8f, SERVER_BLOCK_TOP, SERVER_BLOCK_BOTTOM, "M7 7.5h.01M7 16.5h.01")
    val Globe = stroke(
        "globe", 1.8f,
        "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z",
        "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18",
    )
    val Key = stroke(
        "key", 1.8f,
        "M12 15a4 4 0 1 1-8 0a4 4 0 1 1 8 0z",
        "M10.8 12.2L20 3M16 7l3 3M18 5l2 2",
    )
    val Lock = stroke(
        "lock", 1.8f,
        "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z",
        "M8 11V7a4 4 0 0 1 8 0v4",
    )
    val Clipboard = stroke(
        "clipboard", 1.8f,
        "M9 3h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M16 5h2a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2",
    )
    val Eye = stroke(
        "eye", 1.8f,
        "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z",
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
    )
    val EyeOff = stroke(
        "eyeOff", 1.8f,
        "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z",
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
        "M3 3l18 18",
    )
    val Bolt = stroke("bolt", 1.8f, "M13 2L4 14h7l-1 8 9-12h-7z")
    val Check = stroke("check", 2.2f, "M5 12l5 5L20 7")
    val CheckBold = stroke("checkBold", 2.4f, "M5 12l5 5L20 7")
    val Close = stroke("close", 2f, "M6 6l12 12M18 6L6 18")
    val Search = stroke("search", 1.8f, "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z", "M20 20l-4-4")
    val Alert = stroke(
        "alert", 2.2f,
        "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z",
        "M12 8v5M12 16.5h.01",
    )
    val Trash = stroke("trash", 1.8f, "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Code = stroke("code", 1.8f, "M8 8l-4 4 4 4M16 8l4 4-4 4M13.5 5l-3 14")
    val File = stroke("file", 1.8f, "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5")
    val ChevronExpand = stroke("chevronExpand", 2f, "M6 9l6 6 6-6")
}
