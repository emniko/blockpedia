package com.emniko.blockpedia.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emniko.blockpedia.ui.about.AboutScreen
import com.emniko.blockpedia.ui.article.ArticleScreen
import com.emniko.blockpedia.ui.category.CategoryScreen
import com.emniko.blockpedia.ui.home.HomeScreen
import com.emniko.blockpedia.ui.saved.SavedScreen
import com.emniko.blockpedia.ui.search.SearchScreen
import com.emniko.blockpedia.ui.seed.NearestScreen
import com.emniko.blockpedia.ui.seed.SeedMapScreen
import com.emniko.blockpedia.ui.seed.SeedsScreen
import com.emniko.blockpedia.ui.seed.SlimeScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"

    /** Search opened from inside a page: sits on top of it, so Back returns to the page. */
    const val FIND = "find"
    const val SAVED = "saved"
    const val ABOUT = "about"
    const val ARTICLE = "article/{title}?frag={frag}"
    const val CATEGORY = "category/{name}"
    const val SEEDS = "seeds"
    const val SEED_MAP = "seedmap?x={x}&z={z}&dim={dim}"
    const val NEAREST = "nearest"
    const val SLIME = "slime"

    fun seedMap(
        pos: com.emniko.blockpedia.seed.Pos?,
        dim: com.emniko.blockpedia.seed.Dimension = com.emniko.blockpedia.seed.Dimension.Overworld,
    ): String = if (pos == null) "seedmap?dim=${dim.name}" else "seedmap?x=${pos.x}&z=${pos.z}&dim=${dim.name}"

    fun article(title: String, fragment: String? = null): String {
        val base = "article/" + Uri.encode(title)
        return if (fragment.isNullOrBlank()) base else base + "?frag=" + Uri.encode(fragment)
    }

    fun category(name: String): String = "category/" + Uri.encode(name)
}

private data class TopTab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val TopTabs = listOf(
    TopTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopTab(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    TopTab(Routes.SEEDS, "Seeds", Icons.Filled.Map, Icons.Outlined.Map),
    TopTab(Routes.SAVED, "Saved", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
)

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BlockpediaRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = TopTabs.any { it.route == route }

    val openArticle: (String) -> Unit = { nav.navigate(Routes.article(it)) }
    val openCategory: (String) -> Unit = { nav.navigate(Routes.category(it)) }
    val goHome: () -> Unit = { nav.popBackStack(Routes.HOME, inclusive = false) }
    val openFind: () -> Unit = {
        nav.navigate(Routes.FIND) { launchSingleTop = true }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TopTabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenArticle = openArticle,
                    onOpenCategory = openCategory,
                    onSearch = { nav.openTab(Routes.SEARCH) },
                    onOpenSaved = { nav.openTab(Routes.SAVED) },
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenArticle = openArticle)
            }
            composable(Routes.FIND) {
                SearchScreen(
                    onOpenArticle = { title ->
                        // Replace the search screen with the result, so Back skips it.
                        nav.navigate(Routes.article(title)) { popUpTo(Routes.FIND) { inclusive = true } }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SEEDS) {
                SeedsScreen(
                    onOpenMap = { nav.navigate(Routes.seedMap(it)) },
                    onOpenNearest = { nav.navigate(Routes.NEAREST) },
                    onOpenSlime = { nav.navigate(Routes.SLIME) },
                )
            }
            composable(
                route = Routes.SEED_MAP,
                arguments = listOf(
                    navArgument("x") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("z") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("dim") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                SeedMapScreen(
                    startX = entry.arguments?.getString("x")?.toIntOrNull(),
                    startZ = entry.arguments?.getString("z")?.toIntOrNull(),
                    startDimension = entry.arguments?.getString("dim")
                        ?.let { name -> com.emniko.blockpedia.seed.Dimension.entries.firstOrNull { it.name == name } }
                        ?: com.emniko.blockpedia.seed.Dimension.Overworld,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.NEAREST) {
                NearestScreen(
                    onBack = { nav.popBackStack() },
                    onOpenMap = { pos, dim -> nav.navigate(Routes.seedMap(pos, dim)) },
                )
            }
            composable(Routes.SLIME) {
                SlimeScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SAVED) {
                SavedScreen(onOpenArticle = openArticle)
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable(
                route = Routes.ARTICLE,
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                    navArgument("frag") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val title = entry.arguments?.getString("title").orEmpty()
                val fragment = entry.arguments?.getString("frag")
                ArticleScreen(
                    title = title,
                    fragment = fragment,
                    onBack = { nav.popBackStack() },
                    onOpenArticle = { t, f -> nav.navigate(Routes.article(t, f)) },
                    onOpenCategory = openCategory,
                    onHome = goHome,
                    onSearch = openFind,
                )
            }
            composable(
                route = Routes.CATEGORY,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                CategoryScreen(
                    name = entry.arguments?.getString("name").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenArticle = openArticle,
                    onOpenCategory = openCategory,
                    onHome = goHome,
                    onSearch = openFind,
                )
            }
        }
    }
}
