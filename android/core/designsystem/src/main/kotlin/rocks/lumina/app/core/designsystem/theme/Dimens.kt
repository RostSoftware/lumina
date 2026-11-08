package rocks.lumina.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing/shape/size tokens (root AGENTS.md "Shape, spacing, components"). */
public object LuminaDimens {
    public val gutter: androidx.compose.ui.unit.Dp = 16.dp
    public val postHeaderPadding: androidx.compose.ui.unit.Dp = 12.dp
    public val barHeight: androidx.compose.ui.unit.Dp = 56.dp
    public val contentMaxWidth: androidx.compose.ui.unit.Dp = 470.dp
    public val avatarPost: androidx.compose.ui.unit.Dp = 32.dp
    public val avatarBottomBar: androidx.compose.ui.unit.Dp = 26.dp
    public val avatarProfile: androidx.compose.ui.unit.Dp = 86.dp
    public val avatarList: androidx.compose.ui.unit.Dp = 44.dp
    public val gridGap: androidx.compose.ui.unit.Dp = 2.dp
    public val touchTarget: androidx.compose.ui.unit.Dp = 48.dp
    public val hairline: androidx.compose.ui.unit.Dp = 0.5.dp
}

public object LuminaShapes {
    public val control: RoundedCornerShape = RoundedCornerShape(8.dp)
    public val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    public val pill: RoundedCornerShape = RoundedCornerShape(percent = 50)
}

/** Motion: 150–250 ms ease-out. */
public object LuminaMotion {
    public const val SHORT: Int = 150
    public const val MEDIUM: Int = 200
    public const val LONG: Int = 250
}
