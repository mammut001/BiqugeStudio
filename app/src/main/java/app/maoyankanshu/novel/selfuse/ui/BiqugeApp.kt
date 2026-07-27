package app.maoyankanshu.novel.selfuse.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.SearchActivity
import app.maoyankanshu.novel.selfuse.ui.navigation.MainTab
import app.maoyankanshu.novel.selfuse.ui.screens.DiscoverScreen
import app.maoyankanshu.novel.selfuse.ui.screens.ProfileScreen
import app.maoyankanshu.novel.selfuse.ui.screens.ShelfScreen
import app.maoyankanshu.novel.selfuse.ui.screens.StoreScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiqugeApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = MainTab.fromRoute(navBackStackEntry?.destination?.route)

    var libraryVersion by remember { mutableIntStateOf(0) }
    var historyVersion by remember { mutableIntStateOf(0) }
    val books = remember(libraryVersion) { LibraryStore.get(context).books() }

    // Refresh shelf/history when returning from Java Activities (import, reader, detail).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                libraryVersion++
                historyVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentTab.label,
                        modifier = Modifier.semantics {
                            contentDescription = "当前页面：${currentTab.label}"
                        },
                    )
                },
                actions = {
                    if (currentTab == MainTab.Shelf) {
                        IconButton(
                            onClick = {
                                context.startActivity(Intent(context, SearchActivity::class.java))
                            },
                            modifier = Modifier.semantics { contentDescription = "搜索本地书架" },
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, SearchActivity::class.java)
                                        .putExtra(SearchActivity.EXTRA_IMPORT, true),
                                )
                            },
                            modifier = Modifier.semantics { contentDescription = "导入 TXT 或 EPUB 文件" },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.semantics {
                            contentDescription = tab.contentDescription
                        },
                    )
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
                    onLibraryChanged = { libraryVersion++ },
                    contentPadding = innerPadding,
                )
            }
            composable(MainTab.Store.route) {
                StoreScreen(
                    books = books,
                    contentPadding = innerPadding,
                )
            }
            composable(MainTab.Discover.route) {
                DiscoverScreen(
                    books = books,
                    historyVersion = historyVersion,
                    onHistoryCleared = { historyVersion++ },
                    contentPadding = innerPadding,
                )
            }
            composable(MainTab.Profile.route) {
                ProfileScreen(
                    contentPadding = innerPadding,
                    onLibraryRestored = { libraryVersion++ },
                )
            }
        }
    }
}
