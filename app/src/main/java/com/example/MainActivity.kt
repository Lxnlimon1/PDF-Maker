package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.ConversionToolType
import com.example.data.model.ThemeMode
import com.example.ui.editor.PdfEditorScreen
import com.example.ui.editor.PdfEditorViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.navigation.EditorRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.ScanReviewRoute
import com.example.ui.navigation.ScannerRoute
import com.example.ui.navigation.ToolRunnerRoute
import com.example.ui.navigation.ToolsRoute
import com.example.ui.scanner.ScanReviewScreen
import com.example.ui.scanner.ScannerScreen
import com.example.ui.scanner.ScannerViewModel
import com.example.ui.theme.PdfMasterTheme
import com.example.ui.tools.ToolRunnerScreen
import com.example.ui.tools.ToolsScreen
import com.example.ui.tools.ToolsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PdfMasterApp
        setContent {
            val themeMode by app.preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PdfMasterTheme(darkTheme = isDark) {
                PdfMasterRoot(app = app)
            }
        }
    }
}

private data class NavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val routeObj: Any,
    val testTag: String
)

@Composable
private fun PdfMasterRoot(app: PdfMasterApp) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(app.repository, app.preferences)
    )
    val scannerViewModel: ScannerViewModel = viewModel(
        factory = ScannerViewModel.factory(app.repository, app.preferences)
    )
    val editorViewModel: PdfEditorViewModel = viewModel(
        factory = PdfEditorViewModel.factory(app.repository)
    )
    val toolsViewModel: ToolsViewModel = viewModel(
        factory = ToolsViewModel.factory(app.repository)
    )

    val isTopLevel = currentDest == null ||
        currentDest.hasRoute<HomeRoute>() ||
        currentDest.hasRoute<EditorRoute>() ||
        currentDest.hasRoute<ToolsRoute>()

    val tabs = listOf(
        NavTab("Library", Icons.Filled.Folder, Icons.Outlined.Folder, HomeRoute, "nav_home"),
        NavTab("Scan", Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner, ScannerRoute, "nav_scan"),
        NavTab("Editor", Icons.Filled.EditNote, Icons.Outlined.EditNote, EditorRoute(null), "nav_editor"),
        NavTab("Tools", Icons.Filled.Build, Icons.Outlined.Build, ToolsRoute, "nav_tools")
    )

    fun selectTab(tab: NavTab) {
        if (tab.routeObj is ScannerRoute) {
            navController.navigate(ScannerRoute)
        } else {
            navController.navigate(tab.routeObj) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail && isTopLevel) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    tabs.forEach { tab ->
                        val selected = when (tab.routeObj) {
                            is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                            is EditorRoute -> currentDest?.hasRoute<EditorRoute>() == true
                            is ToolsRoute -> currentDest?.hasRoute<ToolsRoute>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
                AppNavHost(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    scannerViewModel = scannerViewModel,
                    editorViewModel = editorViewModel,
                    toolsViewModel = toolsViewModel,
                    contentBottomPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (isTopLevel) {
                        NavigationBar {
                            tabs.forEach { tab ->
                                val selected = when (tab.routeObj) {
                                    is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                    is EditorRoute -> currentDest?.hasRoute<EditorRoute>() == true
                                    is ToolsRoute -> currentDest?.hasRoute<ToolsRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { outerPadding ->
                AppNavHost(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    scannerViewModel = scannerViewModel,
                    editorViewModel = editorViewModel,
                    toolsViewModel = toolsViewModel,
                    contentBottomPadding = PaddingValues(bottom = outerPadding.calculateBottomPadding()),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    homeViewModel: HomeViewModel,
    scannerViewModel: ScannerViewModel,
    editorViewModel: PdfEditorViewModel,
    toolsViewModel: ToolsViewModel,
    contentBottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenScanner = { navController.navigate(ScannerRoute) },
                onOpenEditor = { docId ->
                    navController.navigate(EditorRoute(docId))
                },
                onOpenTool = { tool, docId ->
                    navController.navigate(ToolRunnerRoute(tool.id, docId))
                },
                contentBottomPadding = contentBottomPadding
            )
        }

        composable<ScannerRoute> {
            ScannerScreen(
                viewModel = scannerViewModel,
                onClose = { navController.popBackStack() },
                onGoToReview = { navController.navigate(ScanReviewRoute) }
            )
        }

        composable<ScanReviewRoute> {
            ScanReviewScreen(
                viewModel = scannerViewModel,
                onBackToCamera = { navController.popBackStack() },
                onSavedPdf = { docId ->
                    navController.navigate(EditorRoute(docId)) {
                        popUpTo(HomeRoute)
                    }
                }
            )
        }

        composable<EditorRoute> { entry ->
            val route = entry.toRoute<EditorRoute>()
            PdfEditorScreen(
                docId = route.docId,
                viewModel = editorViewModel,
                onBack = if (route.docId != null) {
                    { navController.popBackStack() }
                } else null,
                contentBottomPadding = contentBottomPadding
            )
        }

        composable<ToolsRoute> {
            ToolsScreen(
                onSelectTool = { tool ->
                    navController.navigate(ToolRunnerRoute(tool.id, null))
                },
                contentBottomPadding = contentBottomPadding
            )
        }

        composable<ToolRunnerRoute> { entry ->
            val route = entry.toRoute<ToolRunnerRoute>()
            val tool = ConversionToolType.entries.firstOrNull { it.id == route.toolId }
                ?: ConversionToolType.IMAGE_TO_PDF
            ToolRunnerScreen(
                tool = tool,
                preselectedDocId = route.preselectedDocId,
                viewModel = toolsViewModel,
                onBack = { navController.popBackStack() },
                onOpenInEditor = { docId ->
                    navController.navigate(EditorRoute(docId)) {
                        popUpTo(HomeRoute)
                    }
                }
            )
        }
    }
}
