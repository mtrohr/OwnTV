package tv.own.owntv.features.shell.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * A category as shown in the rail: a 2–3 char abbreviation plus its full name for the header. Special
 * rails (Favorites / History) render an [icon] instead of the abbreviation.
 */
data class RailCategory(val abbr: String, val fullName: String, val icon: OwnTVIcon? = null)

/**
 * Layer 2 — the compact vertical folder rail. Shows short abbreviations (FAV, HIS, ALL, …) with the
 * full category name surfacing in the Layer-3 header on focus/selection.
 */
@Composable
fun CategoryRail(
    categories: List<RailCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimens.RailWidth)
            .background(colors.panel)
            .onFocusChanged { if (it.hasFocus) onFocused() }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Dimens.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
    ) {
        categories.forEachIndexed { index, category ->
            RailPill(
                category = category,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RailPill(
    category: RailCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // M3 tonal states: selected uses the primary *container* (soft tonal fill), focus uses a
    // surface-container fill with a primary outline.
    val bg by animateColorAsState(
        targetValue = when {
            selected -> colors.primaryContainer
            focused -> colors.card
            else -> Color.Transparent
        },
        label = "railPillBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            selected -> colors.onPrimaryContainer
            focused -> colors.accent
            else -> colors.textSecondary
        },
        label = "railPillFg",
    )

    Box(
        modifier = Modifier
            .size(Dimens.RailPillSize)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (focused && !selected) Modifier.border(Dimens.FocusBorderWidth, colors.focusBorder, CircleShape)
                else Modifier
            )
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (category.icon != null) {
            OwnTVIcon(icon = category.icon, tint = fg, filled = selected, modifier = Modifier.size(Dimens.RailPillSize / 2))
        } else {
            Text(
                text = category.abbr,
                color = fg,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
