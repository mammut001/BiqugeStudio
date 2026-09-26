package app.maoyankanshu.novel.selfuse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.maoyankanshu.novel.selfuse.AppIntents
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ui.navigation.MainTab
import app.maoyankanshu.novel.selfuse.ui.reader.ReaderLeaveSave
import app.maoyankanshu.novel.selfuse.ui.screens.DiscoverScreen
import app.maoyankanshu.novel.selfuse.ui.screens.ProfileScreen
import app.maoyankanshu.novel.selfuse.ui.screens.ShelfScreen
import app.maoyankanshu.novel.selfuse.ui.screens.StoreScreen
import app.maoyankanshu.novel.selfuse.ui.theme.appTopBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiqugeApp(
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = MainTab.fromRoute(navBackStackEntry?.destination?.route)

    var libraryVersion by remember { mutableIntStateOf(0) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var showProfileSettings by rememberSaveable { mutableStateOf(false) }

    var booksState by remember { mutableStateOf<List<Book>?>(null) }

    // Metadata-only list rows: titles/progress/covers without multi-MB body Strings.
    // getForListing runs seed migrations that never full-decode user multi-MB TXT.
    // Full text stays on reader / detail / export paths (byId or full library dump).
    LaunchedEffect(libraryVersion) {
        val updatedBooks = withContext(Dispatchers.IO) {
            LibraryStore.getForListing(context).booksForListing()
        }
        booksState = updatedBooks
    }

    val isLoading = booksState == null
    val books = booksState ?: emptyList()

    // Leaving “我的” always returns it to the overview/history landing page.
    LaunchedEffect(currentTab) {
        if (currentTab != MainTab.Profile) {
            showProfileSettings = false
        }
    }

    // Refresh shelf/history when returning from Java Activities (import, reader, detail).
    // Skip the first ON_RESUME (cold start already loads via LaunchedEffect).
    // Await leave-save IO so progress/stats from the just-closed reader are visible.
    DisposableEffect(lifecycleOwner) {
        var skipFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipFirstResume) {
                    skipFirstResume = false
                    return@LifecycleEventObserver
                }
                scope.launch {
                    ReaderLeaveSave.awaitIdle()
                    libraryVersion++
                    historyVersion++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun navigateTo(tab: MainTab) {
        showProfileSettings = false
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val profileSettingsVisible = currentTab == MainTab.Profile && showProfileSettings

    // Bar sits flush with the paper background and tints only once content scrolls under it.
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val pageLabel = if (profileSettingsVisible) {
                        stringResource(R.string.profile_settings_heading)
                    } else {
                        stringResource(currentTab.labelRes)
                    }
                    val pageCd = stringResource(R.string.reader_page_title_cd, pageLabel)
                    Text(
                        text = pageLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics {
                            contentDescription = pageCd
                        },
                    )
                },
                navigationIcon = {
                    if (profileSettingsVisible) {
                        val backCd = stringResource(R.string.search_back_cd)
                        IconButton(
                            onClick = { showProfileSettings = false },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = backCd },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    when {
                        currentTab == MainTab.Shelf -> {
                            val searchCd = stringResource(R.string.search_shelf_cd)
                            val importCd = stringResource(R.string.import_browser_download_cd)
                            IconButton(
                                onClick = {
                                    context.startActivity(AppIntents.search(context))
                                },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = searchCd },
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            }
                            IconButton(
                                onClick = {
                                    context.startActivity(AppIntents.browserImport(context))
                                },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = importCd },
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        }

                        currentTab == MainTab.Profile && !profileSettingsVisible -> {
                            val settingsCd = stringResource(R.string.profile_settings_heading)
                            IconButton(
                                onClick = { showProfileSettings = true },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = settingsCd },
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        }
                    }
                },
                colors = appTopBarColors(),
                scrollBehavior = topBarScrollBehavior,
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    MainTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        val tabLabel = stringResource(tab.labelRes)
                        val tabCd = stringResource(tab.contentDescriptionRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(tab) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = tabLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = tabCd
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.Shelf.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(MainTab.Shelf.route) {
                ShelfScreen(
                    books = books,
                    isLoading = isLoading,
                    onLibraryChanged = { libraryVersion++ },
                    contentPadding = innerPadding,
                    historyVersion = historyVersion,
                    onHistoryChanged = { historyVersion++ },
                )
            }
            composable(MainTab.Store.route) {
                StoreScreen(
                    bookCount = books.size,
                    contentPadding = innerPadding,
                )
            }
            composable(MainTab.Profile.route) {
                if (showProfileSettings) {
                    ProfileScreen(
                        contentPadding = innerPadding,
                        onLibraryRestored = { libraryVersion++ },
                        onDarkThemeChanged = onDarkThemeChanged,
                    )
                } else {
                    DiscoverScreen(
                        books = books,
                        historyVersion = historyVersion,
                        onHistoryCleared = { historyVersion++ },
                        onOpenShelf = { navigateTo(MainTab.Shelf) },
                        contentPadding = innerPadding,
                    )
                }
            }
        }
    }
}
