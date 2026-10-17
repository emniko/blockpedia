package com.emniko.blockpedia.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
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

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SAVED = "saved"
    const val ABOUT = "about"
    const val ARTICLE = "article/{title}?frag={frag}"
    const val CATEGORY = "category/{name}"

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
                    onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
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
                )
            }
        }
    }
}
