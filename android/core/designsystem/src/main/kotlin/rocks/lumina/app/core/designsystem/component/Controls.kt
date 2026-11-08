package rocks.lumina.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.icon.LucideGlyph
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/** Clickable with a labelled role and a visible `accent` focus ring. */
public fun Modifier.tapTarget(
    role: Role = Role.Button,
    label: String? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    this
        .then(
            if (focused) Modifier.border(2.dp, LuminaTheme.colors.accent, LuminaShapes.control) else Modifier,
        )
        .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(bounded = false), enabled = enabled, role = role, onClick = onClick)
        .semantics {
            if (label != null) contentDescription = label
            if (selected != null) this.selected = selected
        }
}

/** 48dp labelled icon button. */
@Composable
public fun LuminaIconButton(
    glyph: LucideGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LuminaTheme.colors.foreground,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    badge: Boolean = false,
) {
    Box(
        modifier
            .size(LuminaDimens.touchTarget)
            .clip(CircleShape)
            .tapTarget(label = contentDescription, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LuminaIcon(
            glyph = glyph,
            contentDescription = null,
            filled = filled,
            tint = if (enabled) tint else LuminaTheme.colors.mutedForeground,
            size = iconSize,
        )
        if (badge) UnreadDot(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
    }
}

public enum class ButtonStyle { Primary, Secondary, Outline, Destructive, Ghost }

/** LUMINA button: 8 radius, min 44 high. */
@Composable
public fun LuminaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: LucideGlyph? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val colors = LuminaTheme.colors
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = if (style == ButtonStyle.Primary) colors.onPrimary else colors.foreground,
            )
            Spacer(Modifier.width(8.dp))
        } else if (leadingIcon != null) {
            LuminaIcon(
                leadingIcon,
                contentDescription = null,
                size = 18.dp,
                tint = when (style) {
                    ButtonStyle.Primary -> colors.onPrimary
                    ButtonStyle.Destructive -> colors.onPrimary
                    else -> colors.foreground
                },
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LuminaTheme.typography.username)
    }
    val base = modifier.defaultMinSize(minHeight = 44.dp)
    when (style) {
        ButtonStyle.Primary, ButtonStyle.Secondary, ButtonStyle.Destructive -> Button(
            onClick = onClick,
            modifier = base,
            enabled = enabled && !loading,
            shape = LuminaShapes.control,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (style) {
                    ButtonStyle.Primary -> colors.primary
                    ButtonStyle.Destructive -> colors.destructive
                    else -> colors.surfaceMuted
                },
                contentColor = when (style) {
                    ButtonStyle.Primary -> colors.onPrimary
                    ButtonStyle.Destructive -> Color.White
                    else -> colors.foreground
                },
                disabledContainerColor = colors.surfaceMuted,
                disabledContentColor = colors.mutedForeground,
            ),
            content = content,
        )
        ButtonStyle.Outline -> OutlinedButton(
            onClick = onClick,
            modifier = base,
            enabled = enabled && !loading,
            shape = LuminaShapes.control,
            contentPadding = contentPadding,
            border = BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.foreground, disabledContentColor = colors.mutedForeground),
            content = content,
        )
        ButtonStyle.Ghost -> TextButton(
            onClick = onClick,
            modifier = base,
            enabled = enabled && !loading,
            shape = LuminaShapes.control,
            contentPadding = contentPadding,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            content = content,
        )
    }
}

/** Text input on `surface-muted`, 8 radius, `accent` focus border. */
@Composable
public fun LuminaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingGlyph: LucideGlyph? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LuminaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp),
        label = label?.let { { Text(it, style = LuminaTheme.typography.caption) } },
        placeholder = placeholder?.let { { Text(it, style = LuminaTheme.typography.body, color = colors.mutedForeground) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, style = LuminaTheme.typography.caption) } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = LuminaTheme.typography.body.copy(color = colors.foreground),
        shape = LuminaShapes.control,
        leadingIcon = leadingGlyph?.let { { LuminaIcon(it, null, size = 18.dp, tint = colors.mutedForeground) } },
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceMuted,
            unfocusedContainerColor = colors.surfaceMuted,
            disabledContainerColor = colors.surfaceMuted,
            errorContainerColor = colors.surfaceMuted,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = colors.destructive,
            cursorColor = colors.accent,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.mutedForeground,
            focusedTextColor = colors.foreground,
            unfocusedTextColor = colors.foreground,
        ),
    )
}

/** LUMINA switch (accent when on). */
@Composable
public fun LuminaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LuminaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = colors.mutedForeground,
            uncheckedTrackColor = colors.surfaceMuted,
            uncheckedBorderColor = colors.border,
        ),
    )
}

/** Rounded pill chip (selected = primary). */
@Composable
public fun LuminaChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuminaTheme.colors
    Box(
        modifier
            .clip(LuminaShapes.pill)
            .background(if (selected) colors.primary else colors.surfaceMuted)
            .tapTarget(role = Role.RadioButton, selected = selected, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LuminaTheme.typography.username, color = if (selected) colors.onPrimary else colors.foreground)
    }
}

/** Settings-style row: icon, title, optional subtitle, trailing content/chevron. */
@Composable
public fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: LucideGlyph? = null,
    titleColor: Color = LuminaTheme.colors.foreground,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tapTarget(label = null, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = LuminaDimens.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (glyph != null) LuminaIcon(glyph, null, tint = titleColor)
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = LuminaTheme.typography.body, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            LuminaIcon(rocks.lumina.app.core.designsystem.icon.LucideGlyphs.ChevronRight, null, tint = LuminaTheme.colors.mutedForeground, size = 18.dp)
        }
    }
}

@Composable
public fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = LuminaTheme.typography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = LuminaTheme.colors.mutedForeground,
        modifier = modifier.fillMaxWidth().padding(start = LuminaDimens.gutter, end = LuminaDimens.gutter, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
public fun Spinner(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    CircularProgressIndicator(modifier = modifier.size(size).focusable(false), strokeWidth = 2.dp, color = LuminaTheme.colors.mutedForeground)
}
