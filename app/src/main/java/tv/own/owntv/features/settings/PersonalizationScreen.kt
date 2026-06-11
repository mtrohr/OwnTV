package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.features.shell.components.AvatarPickerDialog
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.AMOLED_DARK -> "AMOLED Dark"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.SYSTEM -> "System"
}

/**
 * Personalization — appearance preferences gathered in one place: theme, accent color, UI scale, and
 * the active profile's avatar. All apply live (the shell observes the same settings flows).
 */
@Composable
fun PersonalizationScreen(
    onBack: () -> Unit,
    avatarId: Int,
    onSetAvatar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val accent by vm.accent.collectAsStateWithLifecycle()
    val zoom by vm.uiZoomPercent.collectAsStateWithLifecycle()
    val isDark = theme != ThemeMode.LIGHT

    var dialog by remember { mutableStateOf(Dlg.NONE) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header("Personalization", onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel("Theme")
        Row2(
            icon = OwnTVIcon.THEME, title = "Theme", desc = "Choose a light, dark or system theme.",
            chip = themeLabel(theme), chevron = true,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { dialog = Dlg.THEME },
        )
        Row2(
            icon = OwnTVIcon.PALETTE, title = "Accent color", desc = "Tint the interface with an accent.",
            chip = accent.label, chevron = true,
            onClick = { dialog = Dlg.ACCENT },
        )

        Divider()
        GroupLabel("Layout")
        Row2(
            icon = OwnTVIcon.ZOOM, title = "UI Zoom", desc = "Scale the whole interface.",
            chip = UiZoom.label(zoom), chevron = true,
            onClick = { dialog = Dlg.ZOOM },
        )

        Divider()
        GroupLabel("Profile")
        Row2(
            icon = OwnTVIcon.PERSON, title = "Avatar", desc = "Change this profile's picture.",
            chevron = true,
            onClick = { dialog = Dlg.AVATAR },
        )
    }

    when (dialog) {
        Dlg.THEME -> PickerDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it.name to themeLabel(it) },
            selected = theme.name,
            onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)); dialog = Dlg.NONE },
            onDismiss = { dialog = Dlg.NONE },
        )
        Dlg.ACCENT -> AccentPickerDialog(
            selected = accent, isDark = isDark,
            onSelect = { vm.setAccent(it); dialog = Dlg.NONE },
            onDismiss = { dialog = Dlg.NONE },
        )
        Dlg.ZOOM -> StepperDialog(
            title = "UI Zoom",
            value = zoom, step = UiZoom.STEP, min = UiZoom.MIN, max = UiZoom.MAX,
            format = { UiZoom.label(it) },
            onSet = { vm.setUiZoom(it) },
            onReset = { vm.setUiZoom(UiZoom.DEFAULT) },
            onDismiss = { dialog = Dlg.NONE },
        )
        Dlg.AVATAR -> AvatarPickerDialog(
            selectedId = avatarId,
            onSelect = { onSetAvatar(it); dialog = Dlg.NONE },
            onDismiss = { dialog = Dlg.NONE },
        )
        Dlg.NONE -> Unit
    }
}

private enum class Dlg { NONE, THEME, ACCENT, ZOOM, AVATAR }

/** Accent picker: a row of color swatches; the selected one is ringed. */
@Composable
private fun AccentPickerDialog(selected: AccentColor, isDark: Boolean, onSelect: (AccentColor) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(520.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Accent color", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AccentColor.entries.forEach { ac ->
                    val isSel = ac == selected
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FocusableSurface(
                            onClick = { onSelect(ac) },
                            modifier = if (ac == selected) Modifier.size(64.dp).focusRequester(fr) else Modifier.size(64.dp),
                            shape = CircleShape,
                            selected = isSel,
                            unfocusedContainerColor = Color.Transparent,
                            selectedContainerColor = Color.Transparent,
                            contentAlignment = Alignment.Center,
                        ) { _ ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ac.primary(isDark))
                                    .border(if (isSel) 3.dp else 0.dp, colors.onSurface, CircleShape),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(ac.label, style = MaterialTheme.typography.labelMedium, color = if (isSel) colors.onSurface else colors.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}
