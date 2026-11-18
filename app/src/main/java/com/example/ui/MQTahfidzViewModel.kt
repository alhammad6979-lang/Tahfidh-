package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.JuzInfo
import com.example.data.QuranReferenceData
import com.example.data.RevelationType
import com.example.data.SurahInfo
import com.example.data.UserPreferences
import com.example.data.UserPreferencesRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class MainDestination {
    PORTAL,
    QURAN_INDEX,
    TOOLS
}

enum class IndexFilter(val label: String) {
    ALL("Semua"),
    JUZ_30("Juz 30"),
    MAKKIYAH("Makkiyah"),
    MADANIYAH("Madaniyah")
}

enum class IndexViewMode {
    SURAH,
    JUZ
}

object PortalUrls {
    const val TAHFIDZ_HOME = "https://mq.dasister.com/tahfidz/home"
    const val MQ_ROOT = "https://mq.dasister.com/"
    const val DASISTER_MAIN = "https://dasister.com/"
}

sealed interface PortalCommand {
    data class LoadUrl(val url: String) : PortalCommand
    data object Reload : PortalCommand
    data object GoBack : PortalCommand
    data object GoForward : PortalCommand
    data object ClearCache : PortalCommand
}

data class PortalUiState(
    val currentUrl: String = PortalUrls.TAHFIDZ_HOME,
    val pageTitle: String = "Portal Tahfidz MQ",
    val isLoading: Boolean = true,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val hasError: Boolean = false,
    val errorDescription: String = "",
    val isFullscreen: Boolean = false
)

data class QuranIndexUiState(
    val searchQuery: String = "",
    val selectedFilter: IndexFilter = IndexFilter.ALL,
    val viewMode: IndexViewMode = IndexViewMode.SURAH,
    val filteredSurahs: List<SurahInfo> = QuranReferenceData.allSurahs,
    val allJuz: List<JuzInfo> = QuranReferenceData.allJuz,
    val selectedSurah: SurahInfo? = null
)

data class TahfidzToolsUiState(
    val tikrarCount: Int = 0,
    val tikrarTarget: Int = 20,
    val elapsedSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val targetJuz: Int = 5,
    val durationDays: Int = 60
) {
    val totalPages: Int get() = targetJuz * 20
    val pagesPerDay: Double get() = totalPages.toDouble() / durationDays.coerceAtLeast(1)
    val linesPerDay: Int get() = ceil(pagesPerDay * 15.0).toInt()
    val linesPerPrayer: Int get() = ceil(linesPerDay / 5.0).toInt()
}

class MQTahfidzViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _currentDestination = MutableStateFlow(MainDestination.PORTAL)
    val currentDestination: StateFlow<MainDestination> = _currentDestination.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _portalState = MutableStateFlow(PortalUiState())
    val portalState: StateFlow<PortalUiState> = _portalState.asStateFlow()

    private val _portalCommands = MutableSharedFlow<PortalCommand>(extraBufferCapacity = 8)
    val portalCommands: SharedFlow<PortalCommand> = _portalCommands.asSharedFlow()

    private val _indexState = MutableStateFlow(QuranIndexUiState())
    val indexState: StateFlow<QuranIndexUiState> = _indexState.asStateFlow()

    private val _toolsState = MutableStateFlow(TahfidzToolsUiState())
    val toolsState: StateFlow<TahfidzToolsUiState> = _toolsState.asStateFlow()

    private var timerJob: Job? = null
    private var appliedInitialImmersive = false

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _preferences.value = prefs
                if (!appliedInitialImmersive) {
                    appliedInitialImmersive = true
                    if (prefs.immersivePortalDefault) {
                        _portalState.update { it.copy(isFullscreen = true) }
                    }
                }
            }
        }
    }

    fun selectDestination(destination: MainDestination) {
        _currentDestination.value = destination
    }

    // Portal state & actions
    fun sendPortalCommand(command: PortalCommand) {
        if (command is PortalCommand.LoadUrl) {
            _portalState.update {
                it.copy(
                    currentUrl = command.url,
                    hasError = false,
                    errorDescription = "",
                    isLoading = true
                )
            }
        } else if (command is PortalCommand.Reload) {
            _portalState.update {
                it.copy(hasError = false, errorDescription = "", isLoading = true)
            }
        }
        _portalCommands.tryEmit(command)
    }

    fun toggleFullscreen() {
        _portalState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun exitFullscreen() {
        _portalState.update { it.copy(isFullscreen = false) }
    }

    fun onWebPageStarted(url: String?) {
        _portalState.update { state ->
            state.copy(
                currentUrl = url ?: state.currentUrl,
                isLoading = true,
                hasError = false,
                errorDescription = ""
            )
        }
    }

    fun onWebPageFinished(url: String?, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        _portalState.update { state ->
            state.copy(
                currentUrl = url ?: state.currentUrl,
                pageTitle = title?.takeIf { it.isNotBlank() } ?: state.pageTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onWebProgressChanged(newProgress: Int, title: String?) {
        _portalState.update { state ->
            state.copy(
                progress = newProgress,
                isLoading = newProgress in 1..99,
                pageTitle = title?.takeIf { it.isNotBlank() } ?: state.pageTitle
            )
        }
    }

    fun onWebError(description: String, failingUrl: String?) {
        _portalState.update { state ->
            state.copy(
                isLoading = false,
                hasError = true,
                errorDescription = description,
                currentUrl = failingUrl ?: state.currentUrl
            )
        }
    }

    fun onWebHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) {
        _portalState.update {
            it.copy(canGoBack = canGoBack, canGoForward = canGoForward)
        }
    }

    // Quran Index actions
    fun updateSearchQuery(query: String) {
        _indexState.update { state ->
            val updated = state.copy(searchQuery = query)
            updated.copy(filteredSurahs = filterSurahs(query, updated.selectedFilter))
        }
    }

    fun selectIndexFilter(filter: IndexFilter) {
        _indexState.update { state ->
            val updated = state.copy(selectedFilter = filter)
            updated.copy(filteredSurahs = filterSurahs(updated.searchQuery, filter))
        }
    }

    fun selectIndexViewMode(mode: IndexViewMode) {
        _indexState.update { it.copy(viewMode = mode) }
    }

    fun openSurahDetail(surah: SurahInfo?) {
        _indexState.update { it.copy(selectedSurah = surah) }
    }

    fun jumpToJuzFilterFromJuzCard(juzNumber: Int) {
        _indexState.update { state ->
            val matching = QuranReferenceData.allSurahs.filter {
                juzNumber in it.startJuz..it.endJuz
            }
            state.copy(
                viewMode = IndexViewMode.SURAH,
                searchQuery = "Juz $juzNumber",
                selectedFilter = IndexFilter.ALL,
                filteredSurahs = matching
            )
        }
    }

    fun resetIndexSearch() {
        _indexState.update { state ->
            state.copy(
                searchQuery = "",
                selectedFilter = IndexFilter.ALL,
                filteredSurahs = QuranReferenceData.allSurahs
            )
        }
    }

    fun applySurahToCalculator(surah: SurahInfo) {
        val juzSpan = (surah.endJuz - surah.startJuz + 1).coerceIn(1, 30)
        _toolsState.update {
            it.copy(
                targetJuz = juzSpan,
                durationDays = (juzSpan * 14).coerceIn(7, 365)
            )
        }
        _indexState.update { it.copy(selectedSurah = null) }
        _currentDestination.value = MainDestination.TOOLS
    }

    private fun filterSurahs(query: String, filter: IndexFilter): List<SurahInfo> {
        val trimmed = query.trim().lowercase()
        val juzMatch = Regex("^juz\\s+(\\d{1,2})$").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()

        return QuranReferenceData.allSurahs.filter { surah ->
            val matchesFilter = when (filter) {
                IndexFilter.ALL -> true
                IndexFilter.JUZ_30 -> 30 in surah.startJuz..surah.endJuz
                IndexFilter.MAKKIYAH -> surah.revelationType == RevelationType.MAKKIYAH
                IndexFilter.MADANIYAH -> surah.revelationType == RevelationType.MADANIYAH
            }
            if (!matchesFilter) return@filter false
            if (trimmed.isEmpty()) return@filter true

            if (juzMatch != null && juzMatch in 1..30) {
                return@filter juzMatch in surah.startJuz..surah.endJuz
            }

            surah.nameLatin.lowercase().contains(trimmed) ||
                surah.meaningId.lowercase().contains(trimmed) ||
                surah.nameArabic.contains(trimmed) ||
                surah.number.toString() == trimmed ||
                surah.juzRange.lowercase().contains(trimmed)
        }
    }

    // Tikrar & Calculator actions
    fun incrementTikrar() {
        _toolsState.update { it.copy(tikrarCount = it.tikrarCount + 1) }
    }

    fun resetTikrar() {
        _toolsState.update { it.copy(tikrarCount = 0) }
    }

    fun setTikrarTarget(target: Int) {
        _toolsState.update { it.copy(tikrarTarget = target) }
    }

    fun toggleTimer() {
        val currentlyRunning = _toolsState.value.isTimerRunning
        if (currentlyRunning) {
            timerJob?.cancel()
            _toolsState.update { it.copy(isTimerRunning = false) }
        } else {
            _toolsState.update { it.copy(isTimerRunning = true) }
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (true) {
                    delay(1000L)
                    _toolsState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _toolsState.update { it.copy(elapsedSeconds = 0, isTimerRunning = false) }
    }

    fun updateTargetJuz(juz: Int) {
        _toolsState.update { it.copy(targetJuz = juz.coerceIn(1, 30)) }
    }

    fun updateDurationDays(days: Int) {
        _toolsState.update { it.copy(durationDays = days.coerceIn(7, 730)) }
    }

    // Preferences
    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setImmersivePortalDefault(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setImmersivePortalDefault(enabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class MQTahfidzViewModelFactory(
    private val repository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MQTahfidzViewModel::class.java)) {
            return MQTahfidzViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
