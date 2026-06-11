@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon

class MovieViewModel(
    private val movieDao: MovieDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val sourceDao: SourceDao,
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

    private val _selectedMovie = MutableStateFlow<MovieEntity?>(null)
    val selectedMovie: StateFlow<MovieEntity?> = _selectedMovie.asStateFlow()

    private var playingMovie: MovieEntity? = null

    init {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            ctx.value = Ctx(pid, sourceDao.sourceIdsForProfile(pid))
        }
        // Periodically persist resume position for the movie currently playing.
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveProgressNow()
            }
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else categoryDao.observe(c.sourceIds, MediaType.MOVIE).map { cats ->
                defaultRail + cats.map { LiveRailItem(LiveKey.Folder(it.id), it.name.take(3).uppercase(), it.name) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val movies: Flow<PagingData<MovieEntity>> = combine(
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
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.MOVIE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val selectedProgress: StateFlow<PlaybackProgressEntity?> = combine(_selectedMovie, ctx) { m, c -> m to c }
        .flatMapLatest { (m, c) ->
            if (m == null) flowOf(null) else progressDao.observe(c.profileId, MediaType.MOVIE, m.id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun select(key: LiveKey) { _selected.value = key }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onMovieFocused(movie: MovieEntity) { _selectedMovie.value = movie }

    fun play(movie: MovieEntity) {
        viewModelScope.launch {
            val saved = progressDao.get(ctx.value.profileId, MediaType.MOVIE, movie.id)
            player.play(
                movie.streamUrl,
                title = movie.name,
                year = movie.year?.toString(),
                isLive = false,
                startPositionMs = saved?.positionMs ?: 0,
            )
            playingMovie = movie
            historyDao.record(WatchHistoryEntity(profileId = ctx.value.profileId, mediaType = MediaType.MOVIE, itemId = movie.id))
        }
    }

    /** Download states for the currently visible movies, keyed by movie id. */
    val downloadStates: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.MOVIE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun download(movie: MovieEntity) {
        downloadManager.enqueue(
            profileId = ctx.value.profileId,
            mediaType = MediaType.MOVIE,
            itemId = movie.id,
            title = movie.name,
            posterUrl = movie.posterUrl,
            streamUrl = movie.streamUrl,
            relativeDir = "Movies",
            fileName = "${StorageAccess.sanitize(movie.name)}.${movie.containerExt ?: StorageAccess.extOf(movie.streamUrl)}",
        )
    }

    fun toggleFavorite(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId
            if (favoriteIds.value.contains(movie.id)) favoriteDao.remove(pid, MediaType.MOVIE, movie.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
        }
    }

    /** Persist the resume position if the player is actually playing the tracked movie. */
    fun saveProgressNow() {
        val m = playingMovie ?: return
        if (player.currentMediaUrl != m.streamUrl || !player.isPlaying.value) return
        val pos = player.position.value
        val dur = player.duration.value
        if (pos > 0 && dur > 0) {
            viewModelScope.launch {
                progressDao.save(
                    PlaybackProgressEntity(profileId = ctx.value.profileId, mediaType = MediaType.MOVIE, itemId = m.id, positionMs = pos, durationMs = dur),
                )
            }
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String): PagingSource<Int, MovieEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return if (query.isBlank()) when (key) {
            LiveKey.All -> movieDao.pagingAll(ids)
            LiveKey.Favorites -> movieDao.pagingFavorites(c.profileId)
            LiveKey.History -> movieDao.pagingHistory(c.profileId)
            is LiveKey.Folder -> movieDao.pagingByCategory(key.id)
        } else when (key) {
            LiveKey.All -> movieDao.searchAll(query, ids)
            LiveKey.Favorites -> movieDao.searchFavorites(query, c.profileId)
            LiveKey.History -> movieDao.searchHistory(query, c.profileId)
            is LiveKey.Folder -> movieDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> movieDao.countAll(ids)
            LiveKey.Favorites -> movieDao.countFavorites(c.profileId)
            LiveKey.History -> historyDao.count(c.profileId, MediaType.MOVIE)
            is LiveKey.Folder -> movieDao.countByCategory(key.id)
        }
    }

    private companion object {
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Movies"),
        )
    }
}
