package tv.own.owntv.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.components.ErrorState
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.theme.OwnTVTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHANNEL_COL = 176.dp
private val ROW_HEIGHT = 64.dp
private val PX_PER_MIN = 4.dp
private const val SLOT_MIN = 30

private fun clock(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
private fun minutesWide(fromMs: Long, toMs: Long): Dp = (((toMs - fromMs) / 60_000L).toInt().coerceAtLeast(0) * PX_PER_MIN.value).dp

/**
 * The full EPG guide: a time × channel grid. Channel labels are pinned on the left; every channel row
 * and the time axis share one horizontal scroll state, so moving the D-pad across programmes scrolls
 * the whole guide in lock-step. Picking a programme opens its details.
 */
@Composable
fun EpgScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: EpgViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val hScroll = rememberScrollState()
    val firstCell = remember { FocusRequester() }
    var detail by remember { mutableStateOf<Pair<String, EpgProgrammeEntity>?>(null) }

    BackHandler { onBack() }
    LaunchedEffect(Unit) { vm.load() } // reload from DB each time the guide is opened
    LaunchedEffect(state.rows.isNotEmpty()) {
        if (state.rows.isNotEmpty()) { kotlinx.coroutines.delay(80); runCatching { firstCell.requestFocus() } }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.surface).padding(horizontal = 32.dp, vertical = 24.dp)) {
        // Header: back + title + date + refresh
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusableSurface(onClick = onBack, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), contentAlignment = Alignment.Center) { _ ->
                OwnTVIcon(OwnTVIcon.BACK, tint = colors.onSurface, modifier = Modifier.size(20.dp))
            }
            Text("TV Guide", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            if (state.windowStart > 0) {
                Text(
                    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(state.windowStart)),
                    style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.canRefresh) {
                OwnTVButton(
                    if (state.refreshing) "Refreshing…" else "Refresh",
                    onClick = { vm.refresh() },
                    icon = OwnTVIcon.HISTORY,
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            state.loading -> CenterBox { OwnTVSpinner(sizeDp = 56) }
            state.refreshing && state.rows.isEmpty() -> CenterBox {
                OwnTVSpinner(sizeDp = 44)
                Spacer(Modifier.height(16.dp))
                Text("Downloading guide…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            state.isError && state.rows.isEmpty() -> CenterBox {
                val retry: (() -> Unit)? = if (state.canRefresh) ({ vm.refresh() }) else null
                ErrorState(
                    title = "Couldn't load the guide",
                    message = state.message ?: "Something went wrong.",
                    retryLabel = "Try again",
                    onRetry = retry,
                )
            }
            state.rows.isEmpty() -> CenterBox {
                Text(state.message ?: "No guide.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                if (state.canRefresh) {
                    Spacer(Modifier.height(20.dp))
                    OwnTVButton("Download guide", onClick = { vm.refresh() }, icon = OwnTVIcon.HISTORY)
                }
            }
            else -> {
                // Time axis (shares hScroll with the rows below).
                val slots = ((state.windowEnd - state.windowStart) / (SLOT_MIN * 60_000L)).toInt()
                Row {
                    Spacer(Modifier.width(CHANNEL_COL))
                    Row(Modifier.horizontalScroll(hScroll)) {
                        for (i in 0 until slots) {
                            val slotMs = state.windowStart + i * SLOT_MIN * 60_000L
                            Text(
                                clock(slotMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width((SLOT_MIN * PX_PER_MIN.value).dp).padding(start = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.rows, key = { it.channel.id }) { row ->
                        val rowIndex = state.rows.indexOf(row)
                        Row {
                            // Pinned channel label
                            Box(
                                modifier = Modifier.width(CHANNEL_COL).height(ROW_HEIGHT).padding(end = 6.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    row.channel.number?.let { "$it  ${row.channel.name}" } ?: row.channel.name,
                                    style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // Scrollable programme strip (shared hScroll)
                            Row(Modifier.horizontalScroll(hScroll)) {
                                ProgrammeStrip(
                                    row = row,
                                    windowStart = state.windowStart,
                                    windowEnd = state.windowEnd,
                                    now = state.now,
                                    firstCellFocus = if (rowIndex == 0) firstCell else null,
                                    onOpen = { detail = row.channel.name to it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { (channelName, p) ->
        ProgrammeDetailDialog(channelName = channelName, programme = p, onDismiss = { detail = null })
    }
}

/** Lays a channel's programmes end-to-end across the window, with gap/trailing spacers so every row is the same total width (keeping the shared scroll aligned). */
@Composable
private fun ProgrammeStrip(
    row: GuideRow,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    firstCellFocus: FocusRequester?,
    onOpen: (EpgProgrammeEntity) -> Unit,
) {
    var cursor = windowStart
    var firstUsed = false
    row.programmes.forEach { p ->
        val start = p.startMs.coerceIn(windowStart, windowEnd)
        val stop = p.stopMs.coerceIn(windowStart, windowEnd)
        if (stop <= cursor) return@forEach
        if (start > cursor) { Spacer(Modifier.width(minutesWide(cursor, start))); cursor = start }
        val isNow = now in p.startMs until p.stopMs
        val fr = if (!firstUsed) firstCellFocus else null
        firstUsed = true
        ProgrammeCell(
            title = p.title,
            timeLabel = "${clock(p.startMs)} – ${clock(p.stopMs)}",
            width = minutesWide(start, stop),
            isNow = isNow,
            focusRequester = fr,
            onClick = { onOpen(p) },
        )
        cursor = stop
    }
    if (cursor < windowEnd) Spacer(Modifier.width(minutesWide(cursor, windowEnd)))
}

@Composable
private fun ProgrammeCell(
    title: String,
    timeLabel: String,
    width: Dp,
    isNow: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Box(Modifier.width(width).height(ROW_HEIGHT).padding(end = 4.dp)) {
        FocusableSurface(
            onClick = onClick,
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).fillMaxSize(),
            shape = RoundedCornerShape(10.dp),
            selected = isNow,
            selectedContainerColor = colors.primaryContainer,
            contentAlignment = Alignment.CenterStart,
        ) { focused ->
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isNow) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isNow) "NOW · $timeLabel" else timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNow) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgrammeDetailDialog(channelName: String, programme: EpgProgrammeEntity, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp)) {
            Text(channelName.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(programme.title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("${clock(programme.startMs)} – ${clock(programme.stopMs)}", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            if (!programme.description.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(programme.description!!, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(fr))
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
