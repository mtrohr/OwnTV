package tv.own.owntv.features.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.features.settings.data.SettingsRepository

/** One channel row in the guide grid: the channel and its programmes inside the visible window. */
data class GuideRow(val channel: ChannelEntity, val programmes: List<EpgProgrammeEntity>)

data class EpgUiState(
    val rows: List<GuideRow> = emptyList(),
    val windowStart: Long = 0,
    val windowEnd: Long = 0,
    val now: Long = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val canRefresh: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Drives the EPG guide grid. Loads the active profile's EPG-capable channels and the programmes in a
 * rolling [GRID_HOURS] window from the DB, and can re-download the bulk XMLTV guide via [EpgRepository].
 */
class EpgViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val epgRepository: EpgRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(EpgUiState())
    val state: StateFlow<EpgUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val pid = settings.activeProfileId.first()
            val sources = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first()
            val sourceIds = sources.map { it.id }
            val canRefresh = sources.any { epgRepository.hasGuide(it) }

            if (sourceIds.isEmpty()) {
                _state.value = EpgUiState(loading = false, canRefresh = false, message = "Add a source to see the guide.")
                return@launch
            }

            val now = System.currentTimeMillis()
            val windowStart = now - (now % HALF_HOUR_MS) // align to the half hour
            val windowEnd = windowStart + GRID_HOURS * 60L * 60 * 1000

            val channels = channelDao.channelsForGuide(sourceIds, MAX_CHANNELS)
            val stored = epgDao.countForSources(sourceIds)
            val programmes = epgDao.programmesInWindow(sourceIds, windowStart, windowEnd)
            val byChannel = programmes.groupBy { it.epgChannelId }

            val rows = channels
                .map { ch -> GuideRow(ch, byChannel[ch.epgChannelId].orEmpty()) }
                .filter { it.programmes.isNotEmpty() }

            val message = when {
                stored == 0 && canRefresh -> "No guide downloaded yet. Press Refresh to fetch the EPG."
                stored == 0 -> "Your sources don't provide an EPG guide."
                rows.isEmpty() -> "No programmes scheduled in the next ${GRID_HOURS}h."
                else -> null
            }

            _state.value = EpgUiState(
                rows = rows, windowStart = windowStart, windowEnd = windowEnd, now = now,
                loading = false, refreshing = false, canRefresh = canRefresh, message = message,
            )
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, message = "Downloading guide…")
            val pid = settings.activeProfileId.first()
            val sources = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first()
            var ok = false
            var lastError: String? = null
            for (source in sources.filter { epgRepository.hasGuide(it) }) {
                runCatching { epgRepository.refresh(source) }
                    .onSuccess { ok = true }
                    .onFailure { lastError = it.message }
            }
            if (!ok && lastError != null) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    isError = true,
                    message = friendlySyncError(lastError, connectivity.isOnlineNow()),
                )
            } else {
                load() // reloads from DB (clears refreshing)
            }
        }
    }

    companion object {
        const val GRID_HOURS = 24
        private const val HALF_HOUR_MS = 30L * 60 * 1000
        private const val MAX_CHANNELS = 300
    }
}
