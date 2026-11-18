package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.UserPreferencesRepository
import com.example.ui.MQTahfidzViewModel
import com.example.ui.MQTahfidzViewModelFactory
import com.example.ui.MainDestination
import com.example.ui.PortalCommand
import com.example.ui.PortalScreen
import com.example.ui.QuranIndexScreen
import com.example.ui.TahfidzToolsScreen
import com.example.ui.theme.MQTahfidzTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            val viewModel: MQTahfidzViewModel = viewModel(
                factory = MQTahfidzViewModelFactory(preferencesRepository)
            )
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()

            MQTahfidzTheme(themeMode = preferences.themeMode) {
                MQTahfidzApp(viewModel = viewModel)
            }
        }
    }
}

private data class NavItemSpec(
    val destination: MainDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MQTahfidzApp(viewModel: MQTahfidzViewModel) {
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val portalState by viewModel.portalState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val toolsState by viewModel.toolsState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val navItems = remember {
        listOf(
            NavItemSpec(
                destination = MainDestination.PORTAL,
                labelRes = R.string.nav_portal,
                selectedIcon = Icons.Filled.Language,
                unselectedIcon = Icons.Outlined.Language,
                testTag = "nav_item_portal"
            ),
            NavItemSpec(
                destination = MainDestination.QURAN_INDEX,
                labelRes = R.string.nav_quran_index,
                selectedIcon = Icons.Filled.MenuBook,
                unselectedIcon = Icons.Outlined.MenuBook,
                testTag = "nav_item_quran_index"
            ),
            NavItemSpec(
                destination = MainDestination.TOOLS,
                labelRes = R.string.nav_tools,
                selectedIcon = Icons.Filled.Build,
                unselectedIcon = Icons.Outlined.Build,
                testTag = "nav_item_tools"
            )
        )
    }

    val hideChromeForPortal =
        currentDestination == MainDestination.PORTAL && portalState.isFullscreen

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavigationRail && !hideChromeForPortal) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination == item.destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectDestination(item.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !hideChromeForPortal) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        navItems.forEach { item ->
                            val selected = currentDestination == item.destination
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectDestination(item.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    val isPortalVisible = currentDestination == MainDestination.PORTAL

                    // Keep PortalScreen mounted so WebView session and history stay intact across tab switches
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isPortalVisible) 1f else 0f)
                    ) {
                        PortalScreen(
                            isVisible = isPortalVisible,
                            uiState = portalState,
                            commands = viewModel.portalCommands,
                            onBackPressedInWeb = { viewModel.sendPortalCommand(PortalCommand.GoBack) },
                            onForwardPressedInWeb = { viewModel.sendPortalCommand(PortalCommand.GoForward) },
                            onReloadPressed = { viewModel.sendPortalCommand(PortalCommand.Reload) },
                            onLoadUrl = { url -> viewModel.sendPortalCommand(PortalCommand.LoadUrl(url)) },
                            onClearCache = { viewModel.sendPortalCommand(PortalCommand.ClearCache) },
                            onToggleFullscreen = { viewModel.toggleFullscreen() },
                            onExitFullscreen = { viewModel.exitFullscreen() },
                            onWebPageStarted = viewModel::onWebPageStarted,
                            onWebPageFinished = viewModel::onWebPageFinished,
                            onWebProgressChanged = viewModel::onWebProgressChanged,
                            onWebError = viewModel::onWebError,
                            onWebHistoryChanged = viewModel::onWebHistoryChanged,
                            onOpenOfflineIndex = {
                                viewModel.selectDestination(MainDestination.QURAN_INDEX)
                            },
                            onShowMessage = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        )
                    }

                    if (currentDestination == MainDestination.QURAN_INDEX) {
                        BackHandler {
                            viewModel.selectDestination(MainDestination.PORTAL)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f)
                                .background(MaterialTheme.colorScheme.background)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            QuranIndexScreen(
                                uiState = indexState,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onSelectFilter = viewModel::selectIndexFilter,
                                onSelectViewMode = viewModel::selectIndexViewMode,
                                onOpenSurahDetail = viewModel::openSurahDetail,
                                onSelectJuzCard = viewModel::jumpToJuzFilterFromJuzCard,
                                onResetSearch = viewModel::resetIndexSearch,
                                onApplySurahToCalculator = viewModel::applySurahToCalculator
                            )
                        }
                    } else if (currentDestination == MainDestination.TOOLS) {
                        BackHandler {
                            viewModel.selectDestination(MainDestination.PORTAL)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f)
                                .background(MaterialTheme.colorScheme.background)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            TahfidzToolsScreen(
                                uiState = toolsState,
                                preferences = preferences,
                                onIncrementTikrar = viewModel::incrementTikrar,
                                onResetTikrar = viewModel::resetTikrar,
                                onSelectTikrarTarget = viewModel::setTikrarTarget,
                                onToggleTimer = viewModel::toggleTimer,
                                onResetTimer = viewModel::resetTimer,
                                onUpdateTargetJuz = viewModel::updateTargetJuz,
                                onUpdateDurationDays = viewModel::updateDurationDays,
                                onSelectThemeMode = viewModel::setThemeMode,
                                onToggleImmersiveDefault = viewModel::setImmersivePortalDefault
                            )
                        }
                    }
                }
            }
        }
    }
}
