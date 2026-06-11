@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.repository.SeriesRepository
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.MediaMeta
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaylistItem
import tv.own.owntv.ui.components.OwnTVIcon

class SeriesViewModel(
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val sourceDao: SourceDao,
    private val seriesRepository: SeriesRepository,
    private val settings: SettingsRepository,
    private val player: OwnTVPlayer,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    private val ctx = MutableStateFlow(Ctx(-1L, emptyList()))

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedSeries = MutableStateFlow<SeriesEntity?>(null)
    val selectedSeries: StateFlow<SeriesEntity?> = _selectedSeries.asStateFlow()

    private val _openedSeries = MutableStateFlow<SeriesEntity?>(null)
    val openedSeries: StateFlow<SeriesEntity?> = _openedSeries.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _lastPlayedEpisodeId = MutableStateFlow<Long?>(null)
    val lastPlayedEpisodeId: StateFlow<Long?> = _lastPlayedEpisodeId.asStateFlow()

    private val _episodesLoading = MutableStateFlow(false)
    val episodesLoading: StateFlow<Boolean> = _episodesLoading.asStateFlow()

    init {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            ctx.value = Ctx(pid, sourceDao.sourceIdsForProfile(pid))
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else categoryDao.observe(c.sourceIds, MediaType.SERIES).map { cats ->
                defaultRail + cats.map { LiveRailItem(LiveKey.Folder(it.id), it.name.take(3).uppercase(), it.name) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val series: Flow<PagingData<SeriesEntity>> = combine(
        _selected, ctx, _search.map { it.trim() }.debounce(300).distinctUntilChanged(),
    ) { key, c, query -> Triple(key, c, query) }
        .flatMapLatest { (key, c, query) ->
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 90, maxSize = 300)) {
                pagingSource(key, c, query)
            }.flow
        }
        .cachedIn(viewModelScope)

    val count: StateFlow<Int> = combine(_selected, ctx) { key, c -> key to c }
        .flatMapLatest { (key, c) -> countFlow(key, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.SERIES) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val episodes: StateFlow<List<EpisodeEntity>> = _openedSeries
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else seriesDao.episodesBySeries(s.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(key: LiveKey) { _selected.value = key }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onSeriesFocused(s: SeriesEntity) { _selectedSeries.value = s }
    fun selectSeason(season: Int) { _selectedSeason.value = season }

    fun openSeries(s: SeriesEntity) {
        _openedSeries.value = s
        _selectedSeason.value = 1 // reset season when opening a different show
        viewModelScope.launch {
            _episodesLoading.value = true
            seriesRepository.loadEpisodes(s)
            _episodesLoading.value = false
        }
    }

    fun closeSeries() {
        _openedSeries.value = null
    }

    fun playEpisode(episode: EpisodeEntity) {
        _lastPlayedEpisodeId.value = episode.id
        viewModelScope.launch {
            val pid = ctx.value.profileId
            // Queue the whole season so prev/next work in the player.
            val seasonEpisodes = episodes.value
                .filter { it.seasonNumber == episode.seasonNumber }
                .sortedBy { it.episodeNumber }
            val startIndex = seasonEpisodes.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
            val saved = progressDao.get(pid, MediaType.EPISODE, episode.id)
            val showName = _openedSeries.value?.name
            player.playEpisodes(
                items = seasonEpisodes.map { ep ->
                    PlaylistItem(
                        url = ep.streamUrl,
                        meta = MediaMeta(
                            title = ep.name,
                            subtitle = listOfNotNull(showName, "Season ${ep.seasonNumber}").joinToString(" · "),
                        ),
                    )
                },
                startIndex = startIndex,
                startPositionMs = saved?.positionMs ?: 0,
            )
            historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
            // Also mark the show as recently-watched so it appears under Series → HIS.
            historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
        }
    }

    /** Download states for the open show's episodes, keyed by episode id. */
    val episodeDownloads: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.EPISODE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun downloadEpisode(episode: EpisodeEntity) {
        val show = _openedSeries.value
        val showDir = StorageAccess.sanitize(show?.name ?: "Series")
        val ext = episode.containerExt ?: StorageAccess.extOf(episode.streamUrl)
        downloadManager.enqueue(
            profileId = ctx.value.profileId,
            mediaType = MediaType.EPISODE,
            itemId = episode.id,
            title = episode.name,
            posterUrl = show?.posterUrl,
            streamUrl = episode.streamUrl,
            relativeDir = "Series/$showDir/Season ${episode.seasonNumber}",
            fileName = "${StorageAccess.sanitize(episode.name)}.$ext",
        )
    }

    fun toggleFavorite(s: SeriesEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId
            if (favoriteIds.value.contains(s.id)) favoriteDao.remove(pid, MediaType.SERIES, s.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = s.id))
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String): PagingSource<Int, SeriesEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return if (query.isBlank()) when (key) {
            LiveKey.All -> seriesDao.pagingAll(ids)
            LiveKey.Favorites -> seriesDao.pagingFavorites(c.profileId)
            LiveKey.History -> seriesDao.pagingHistory(c.profileId)
            is LiveKey.Folder -> seriesDao.pagingByCategory(key.id)
        } else when (key) {
            LiveKey.All -> seriesDao.searchAll(query, ids)
            LiveKey.Favorites -> seriesDao.searchFavorites(query, c.profileId)
            LiveKey.History -> seriesDao.searchHistory(query, c.profileId)
            is LiveKey.Folder -> seriesDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> seriesDao.countAll(ids)
            LiveKey.Favorites -> seriesDao.countFavorites(c.profileId)
            LiveKey.History -> historyDao.count(c.profileId, MediaType.SERIES)
            is LiveKey.Folder -> seriesDao.countByCategory(key.id)
        }
    }

    private companion object {
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Series"),
        )
    }
}
