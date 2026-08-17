package com.pourista.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pourista.R
import com.pourista.appContainer
import com.pourista.ui.brew.BrewScreen
import com.pourista.ui.brew.BrewViewModel
import com.pourista.ui.history.BrewDetailScreen
import com.pourista.ui.history.BrewDetailViewModel
import com.pourista.ui.history.HistoryScreen
import com.pourista.ui.history.HistoryViewModel
import com.pourista.ui.recipes.RecipeEditorScreen
import com.pourista.ui.recipes.RecipeEditorViewModel
import com.pourista.ui.recipes.RecipeListScreen
import com.pourista.ui.recipes.RecipesViewModel
import com.pourista.ui.settings.SettingsScreen
import com.pourista.ui.settings.SettingsViewModel

private object Routes {
    const val BREW = "brew"
    const val RECIPES = "recipes"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RECIPE_EDITOR = "recipe_editor"
    const val BREW_DETAIL = "brew_detail"
    const val ARG_ID = "id"
}

private enum class TopLevel(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    BREW(Routes.BREW, Icons.Default.LocalCafe, R.string.tab_brew),
    RECIPES(Routes.RECIPES, Icons.AutoMirrored.Filled.MenuBook, R.string.tab_recipes),
    HISTORY(Routes.HISTORY, Icons.Default.History, R.string.tab_history),
    SETTINGS(Routes.SETTINGS, Icons.Default.Settings, R.string.tab_settings),
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val bottomBar: @Composable () -> Unit = { BottomBar(navController) }

    NavHost(navController = navController, startDestination = Routes.BREW) {
        composable(Routes.BREW) {
            val context = LocalContext.current
            val viewModel: BrewViewModel = viewModel { BrewViewModel(context.appContainer) }
            BrewScreen(
                viewModel = viewModel,
                onEditRecipe = { id -> navController.navigate("${Routes.RECIPE_EDITOR}/$id") },
                onOpenDraft = { navController.navigate("${Routes.RECIPE_EDITOR}/0") },
                bottomBar = bottomBar,
            )
        }

        composable(Routes.RECIPES) {
            val context = LocalContext.current
            val viewModel: RecipesViewModel = viewModel { RecipesViewModel(context.appContainer) }
            RecipeListScreen(
                viewModel = viewModel,
                onEdit = { id -> navController.navigate("${Routes.RECIPE_EDITOR}/$id") },
                onCreate = { navController.navigate("${Routes.RECIPE_EDITOR}/0") },
                onBrew = { navController.navigateToTab(Routes.BREW) },
                bottomBar = bottomBar,
            )
        }

        composable(Routes.HISTORY) {
            val context = LocalContext.current
            val viewModel: HistoryViewModel = viewModel { HistoryViewModel(context.appContainer) }
            HistoryScreen(
                viewModel = viewModel,
                onOpen = { id -> navController.navigate("${Routes.BREW_DETAIL}/$id") },
                bottomBar = bottomBar,
            )
        }

        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val viewModel: SettingsViewModel = viewModel { SettingsViewModel(context.appContainer) }
            SettingsScreen(viewModel = viewModel, bottomBar = bottomBar)
        }

        composable(
            route = "${Routes.RECIPE_EDITOR}/{${Routes.ARG_ID}}",
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            val context = LocalContext.current
            val recipeId = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
            val viewModel: RecipeEditorViewModel = viewModel(key = "recipe_$recipeId") {
                RecipeEditorViewModel(context.appContainer, recipeId)
            }
            RecipeEditorScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.BREW_DETAIL}/{${Routes.ARG_ID}}",
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            val context = LocalContext.current
            val brewId = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
            val viewModel: BrewDetailViewModel = viewModel(key = "brew_$brewId") {
                BrewDetailViewModel(context.appContainer, brewId)
            }
            BrewDetailScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        TopLevel.entries.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
