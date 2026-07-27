package app.maoyankanshu.novel.selfuse.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Store
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
) {
    Shelf(
        route = "shelf",
        label = "书架",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        contentDescription = "书架，查看本地书籍",
    ),
    Store(
        route = "store",
        label = "书城",
        selectedIcon = Icons.Filled.Store,
        unselectedIcon = Icons.Outlined.Store,
        contentDescription = "书城，导入与本地书库",
    ),
    Discover(
        route = "discover",
        label = "发现",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        contentDescription = "发现，阅读概览与历史",
    ),
    Profile(
        route = "profile",
        label = "我的",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        contentDescription = "我的，阅读设置与备份",
    );

    companion object {
        fun fromRoute(route: String?): MainTab =
            entries.firstOrNull { it.route == route } ?: Shelf
    }
}
