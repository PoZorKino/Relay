package app.relay.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.relay.chat.ui.theme.Relay
import app.relay.chat.R
import androidx.compose.ui.res.stringResource

/** Text style shorthand; sizes are the design's px values as sp. Defaults follow the active theme. */
fun ts(
    size: Float,
    weight: Int = 400,
    color: Color = Relay.Ink,
    family: FontFamily = Relay.Body,
    lineHeight: Float? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    color = color,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
    letterSpacing = letterSpacing,
)

/** Lilac's soft purple drop shadow (CSS `0 2px 10px rgba(74,51,168,.08–.12)`). */
fun Modifier.lilacShadow(shape: Shape, elevation: Dp = 6.dp) =
    shadow(elevation, shape, clip = false, ambientColor = Color(0x334A33A8), spotColor = Color(0x334A33A8))

/** 44dp icon button (the design's touch target size). */
@Composable
fun IconBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    tint: Color = Relay.Ink,
    background: Color = Color.Transparent,
    shape: Shape = Relay.shape(12.dp),
    border: Color? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Section heading. Classic/Midnight: tracked uppercase; Lilac: sentence case, bold;
 * Terminal: `[ snake_case ]`.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    when {
        Relay.isTerminal -> Text(
            "[ ${text.lowercase().replace(" & ", "_").replace(' ', '_')} ]", modifier,
            style = ts(12f, 700, Relay.Muted), maxLines = 1,
        )
        Relay.isLilac -> Text(text, modifier, style = ts(13f, 700, Relay.Muted), maxLines = 1)
        else -> Text(text.uppercase(), modifier, style = ts(12f, 600, Relay.Muted, letterSpacing = 0.08.em), maxLines = 1)
    }
}

@Composable
fun Badge(text: String, fg: Color = Relay.AccentOnTint, bg: Color = Relay.AccentTint) {
    Text(
        text,
        Modifier.clip(Relay.round).background(bg).padding(horizontal = if (Relay.isLilac) 8.dp else 7.dp, vertical = 2.dp),
        style = ts(11f, if (Relay.isLilac) 700 else 600, fg),
        maxLines = 1,
    )
}

/** Card: hairline border (Classic/Midnight/Terminal) or borderless with a soft shadow (Lilac). */
@Composable
fun Card(radius: Dp = 16.dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = Relay.shape(if (Relay.isLilac) radius + 4.dp else radius)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (Relay.isLilac) Modifier.lilacShadow(shape, 3.dp) else Modifier)
            .clip(shape)
            .background(Relay.Surface)
            .then(if (Relay.isLilac) Modifier else Modifier.border(1.dp, Relay.Line, shape))
    ) { content() }
}

@Composable
fun RowDivider() = HorizontalDivider(thickness = 1.dp, color = Relay.RowLine)

/** Dashed hairline, used by Terminal where the design draws `1px dashed` borders. */
@Composable
fun DashedDivider() {
    val c = Relay.Line
    Box(
        Modifier.fillMaxWidth().height(1.dp).drawBehind {
            drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        }
    )
}

/**
 * The "Add API key or base URL" action. Classic/Midnight: dashed accent outline;
 * Lilac: filled accent pill; Terminal: filled green block.
 */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    when {
        Relay.isTerminal -> Box(
            modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Relay.shape(4.dp)).background(Relay.Accent)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.term_add_connection), style = ts(13.5f, 700, Relay.OnAccent)) }

        Relay.isLilac -> Row(
            modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(50)).background(Relay.Accent)
                .clickable(role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(RelayIcons.PlusBold, null, tint = Relay.OnAccent, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.add_connection), style = ts(15f, 700, Relay.OnAccent))
        }

        else -> {
            val border = Relay.Accent
            Row(
                modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            color = border,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(14.dp.toPx() - w / 2),
                            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    .clickable(role = Role.Button, onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RelayIcons.PlusBold, null, tint = Relay.AccentText, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.add_connection), style = ts(15f, 600, Relay.AccentText))
            }
        }
    }
}

/** Form label (13px / 600, 4px inset). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 4.dp), style = ts(13f, 600))
}

/**
 * Bordered input: 48dp tall, 12dp radius, 1dp border that becomes 1.5dp accent while
 * focused (the Base URL field in the design shows the focused state).
 */
@Composable
fun RelayField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textStyle: TextStyle = ts(16f),
    singleLine: Boolean = true,
    minHeight: Dp = 48.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = Relay.shape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        cursorBrush = SolidColor(Relay.Accent),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(shape)
                    .background(Relay.Surface)
                    .border(if (focused) 1.5.dp else 1.dp, if (focused) Relay.Accent else Relay.Line, shape)
                    .padding(start = 14.dp, end = if (trailing == null) 14.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(vertical = if (singleLine) 0.dp else 12.dp)
                ) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = Relay.Chevron), maxLines = if (singleLine) 1 else 3)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun Spacer(h: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(h))
