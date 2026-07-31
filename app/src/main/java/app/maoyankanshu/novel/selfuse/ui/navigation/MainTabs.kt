package app.maoyankanshu.novel.selfuse.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import app.maoyankanshu.novel.selfuse.R

/**
 * Bottom navigation destinations.
 * Labels live in [R.string] (`tab_shelf` …) — resolve with stringResource in Compose UI.
 * Avoid embedding R.string in enum constructors that run at class-load time.
 */
enum class MainTab(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Shelf(
        route = "shelf",
        labelRes = R.string.tab_shelf,
        contentDescriptionRes = R.string.tab_shelf_cd,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    Store(
        route = "store",
        labelRes = R.string.tab_store,
        contentDescriptionRes = R.string.tab_store_cd,
        selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
        unselectedIcon = Icons.AutoMirrored.Outlined.LibraryBooks,
    ),
    Discover(
        route = "discover",
        labelRes = R.string.tab_discover,
        contentDescriptionRes = R.string.tab_discover_cd,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
    ),
    Profile(
        route = "profile",
        labelRes = R.string.tab_profile,
        contentDescriptionRes = R.string.tab_profile_cd,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    );

    companion object {
        fun fromRoute(route: String?): MainTab =
            entries.firstOrNull { it.route == route } ?: Shelf
    }
}
