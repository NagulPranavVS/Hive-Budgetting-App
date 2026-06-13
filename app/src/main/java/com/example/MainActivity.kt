package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.components.LiquidBackground
import com.example.ui.TrackerViewModel
import com.example.ui.TrackerViewModelFactory
import com.example.ui.screens.AddExpenseScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MonthlyReportScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.TransactionsScreen
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var trackerViewModel: TrackerViewModel? = null
    private var isStartIntentProcessed = false

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        trackerViewModel?.let { viewModel ->
            handleIntent(intent, viewModel)
        }
    }

    private fun handleIntent(intent: android.content.Intent?, viewModel: TrackerViewModel) {
         intent?.action?.let { action ->
             if (action == "com.example.action.ADD_EXPENSE" || action == "com.example.action.ADD_INCOME") {
                 viewModel.widgetActionTrigger.value = action
                 viewModel.widgetRouteIsIncome.value = (action == "com.example.action.ADD_INCOME")
             }
         }
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get application and repository singletons
        val trackerApp = application as TrackerApplication
        val repository = trackerApp.repository
        
        val viewModel = ViewModelProvider(
            this,
            TrackerViewModelFactory(trackerApp, repository)
        )[TrackerViewModel::class.java]
        trackerViewModel = viewModel

        if (savedInstanceState == null && !isStartIntentProcessed) {
            handleIntent(intent, viewModel)
            isStartIntentProcessed = true
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                // Remember the startup destination to avoid recreating NavHost graph on recompositions
                val startDestination = remember {
                    val action = intent?.action
                    if (action == "com.example.action.ADD_EXPENSE" || action == "com.example.action.ADD_INCOME") {
                        TabDestination.AddExpense.route
                    } else {
                        TabDestination.Home.route
                    }
                }

                // Track selected tab dynamically from backstack!
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: TabDestination.Home.route
                val showBottomBar = currentRoute != TabDestination.AddExpense.route && currentRoute != "manage_categories"

                // Listen to widget quick-actions and navigate accordingly
                LaunchedEffect(viewModel, navController) {
                    viewModel.widgetActionTrigger.collect { action ->
                        if (action != null) {
                            if (action == "com.example.action.ADD_EXPENSE") {
                                viewModel.widgetRouteIsIncome.value = false
                            } else if (action == "com.example.action.ADD_INCOME") {
                                viewModel.widgetRouteIsIncome.value = true
                            }
                            
                            viewModel.widgetActionTrigger.value = null
                            
                            try {
                                // Wait for navController to be initialized with a graph
                                var hasGraph = false
                                for (i in 1..20) {
                                    try {
                                        navController.graph
                                        hasGraph = true
                                        break
                                    } catch (e: IllegalStateException) {
                                        kotlinx.coroutines.delay(50)
                                    }
                                }
                                
                                if (hasGraph) {
                                    val currentEntry = navController.currentBackStackEntry
                                    val currentDestRoute = currentEntry?.destination?.route
                                    if (currentDestRoute != TabDestination.AddExpense.route) {
                                        navController.navigate(TabDestination.AddExpense.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                e.printStackTrace()
                            }
                        }
                    }
                }

                val selectedTab = remember(currentRoute) {
                    when (currentRoute) {
                        TabDestination.Home.route -> TabDestination.Home
                        TabDestination.Transactions.route -> TabDestination.Transactions
                        TabDestination.AddExpense.route -> TabDestination.AddExpense
                        TabDestination.MonthlyReport.route -> TabDestination.MonthlyReport
                        TabDestination.Profile.route -> TabDestination.Profile
                        else -> TabDestination.Home
                    }
                }

                val googleEmail by viewModel.googleEmail.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    // Shifting fluid organic background layer sitting beneath transparent screens
                    LiquidBackground(themeMode = themeMode)

                    Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            floatingActionButton = {
                                val showFab = currentRoute == TabDestination.Home.route ||
                                        currentRoute == TabDestination.Transactions.route
                                AnimatedVisibility(
                                    visible = !WindowInsets.isImeVisible && showFab,
                                    enter = fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(150)),
                                    exit = fadeOut(animationSpec = tween(150)) + scaleOut(animationSpec = tween(150))
                                ) {
                                    FloatingActionButton(
                                        onClick = {
                                            navController.navigate(TabDestination.AddExpense.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        containerColor = Color.Transparent,

                                        contentColor = Color.White,
                                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                                        modifier = Modifier
                                            .padding(
                                                bottom = if (showBottomBar) 102.dp else 14.dp,
                                                end = 4.dp
                                            )
                                            .testTag("floating_add_button")
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                    colors = listOf(Color(0xFF2420F9), Color(0xFF5F5CFF))
                                                ),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Transaction",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            bottomBar = {},
                            snackbarHost = {
                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier
                                        .padding(bottom = 16.dp)
                                        .testTag("snackbar_host")
                                ) { data ->
                                    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
                                    Snackbar(
                                        snackbarData = data,
                                        containerColor = if (isDark) Color(0xFF1E1A33) else Color(0xFF111827),
                                        contentColor = Color.White,
                                        actionColor = if (isDark) Color(0xFF5F5CFF) else Color(0xFF3B82F6),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                NavHost(
                                navController = navController,
                                startDestination = startDestination,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = innerPadding.calculateTopPadding(),
                                        bottom = 0.dp,
                                        start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                        end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                                    ),
                                enterTransition = { fadeIn(animationSpec = tween(150)) }, exitTransition = { fadeOut(animationSpec = tween(150)) }, popEnterTransition = { fadeIn(animationSpec = tween(150)) }, popExitTransition = { fadeOut(animationSpec = tween(150)) }, /*
                                    val initialRoute = initialState.destination.route
                                    val targetRoute = targetState.destination.route
                                    if (initialRoute == null || targetRoute == null || initialRoute == targetRoute) {
                                        fadeIn(animationSpec = tween(300))
                                    } else {
                                        val initialTab = getTabRouteIndex(initialRoute)
                                        val targetTab = getTabRouteIndex(targetRoute)
                                        fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                                            initialOffsetX = { width -> if (targetTab > initialTab) width else -width },
                                            animationSpec = tween(300)
                                        )
                                    }
                                },
                                exitTransition = {
                                    val initialRoute = initialState.destination.route
                                    val targetRoute = targetState.destination.route
                                    if (initialRoute == null || targetRoute == null || initialRoute == targetRoute) {
                                        fadeOut(animationSpec = tween(300))
                                    } else {
                                        val initialTab = getTabRouteIndex(initialRoute)
                                        val targetTab = getTabRouteIndex(targetRoute)
                                        fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                                            targetOffsetX = { width -> if (targetTab > initialTab) -width else width },
                                            animationSpec = tween(300)
                                        )
                                    }
                                },
                                popEnterTransition = {
                                    val initialRoute = initialState.destination.route
                                    val targetRoute = targetState.destination.route
                                    if (initialRoute == null || targetRoute == null || initialRoute == targetRoute) {
                                        fadeIn(animationSpec = tween(300))
                                    } else {
                                        val initialTab = getTabRouteIndex(initialRoute)
                                        val targetTab = getTabRouteIndex(targetRoute)
                                        fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                                            initialOffsetX = { width -> if (targetTab > initialTab) width else -width },
                                            animationSpec = tween(300)
                                        )
                                    }
                                },
                                popExitTransition = {
                                    val initialRoute = initialState.destination.route
                                    val targetRoute = targetState.destination.route
                                    if (initialRoute == null || targetRoute == null || initialRoute == targetRoute) {
                                        fadeOut(animationSpec = tween(300))
                                    } else {
                                        val initialTab = getTabRouteIndex(initialRoute)
                                        val targetTab = getTabRouteIndex(targetRoute)
                                        fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                                            targetOffsetX = { width -> if (targetTab > initialTab) -width else width },
                                            animationSpec = tween(300)
                                        )
                                    }
                                },
                                */
                            ) {
                                composable(TabDestination.Home.route) {
                                    HomeScreen(
                                        viewModel = viewModel,
                                        onSeeAllClicked = {
                                            navController.navigate(TabDestination.Transactions.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onEditExpense = { expense ->
                                            viewModel.startEditingExpense(expense)
                                            navController.navigate(TabDestination.AddExpense.route)
                                        },
                                        snackbarHostState = snackbarHostState,
                                        onNavigateToAnalytics = {
                                            navController.navigate(TabDestination.MonthlyReport.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onNavigateToProfile = {
                                            navController.navigate(TabDestination.Profile.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onSetBudgetClick = {
                                            navController.navigate("manage_categories")
                                        }
                                    )
                                }
                                composable(TabDestination.Transactions.route) {
                                    TransactionsScreen(
                                        viewModel = viewModel,
                                        onEditExpense = { expense ->
                                            viewModel.startEditingExpense(expense)
                                            navController.navigate(TabDestination.AddExpense.route)
                                        },
                                        snackbarHostState = snackbarHostState
                                    )
                                }
                                composable(TabDestination.AddExpense.route) {
                                    AddExpenseScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navController.navigate(TabDestination.Home.route) {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            }
                                        }
                                    )
                                }
                                composable(TabDestination.MonthlyReport.route) {
                                    MonthlyReportScreen(viewModel = viewModel)
                                }
                                composable(TabDestination.Profile.route) {
                                    ProfileScreen(
                                        viewModel = viewModel,
                                        onManageCategories = {
                                            navController.navigate("manage_categories")
                                        }
                                    )
                                }
                                composable("manage_categories") {
                                    com.example.ui.screens.ManageCategoriesScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }

                            // Beautiful, solid floating bottom nav bar adapted to both Dark and Light themes
                            if (!WindowInsets.isImeVisible && showBottomBar) {
                                val isDark = MaterialTheme.colorScheme.background.red < 0.2f
                                val barBgColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFFFFFFF)
                                val barBorderColor = if (isDark) Color(0x1FFFFFFF) else Color(0x0F000000)
                                val itemSelectedColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
                                val itemUnselectedColor = if (isDark) Color.White.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 16.dp, top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(72.dp)
                                            .shadow(
                                                elevation = 16.dp,
                                                shape = CircleShape,
                                                clip = false
                                            )
                                            .background(
                                                color = barBgColor,
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = barBorderColor,
                                                shape = CircleShape
                                            )
                                            .padding(horizontal = 12.dp)
                                            .testTag("bottom_nav_bar"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        val items = listOf(
                                            TabDestination.Home,
                                            TabDestination.Transactions,
                                            TabDestination.MonthlyReport,
                                            TabDestination.Profile
                                        )
                                        items.forEach { item ->
                                            val isSelected = selectedTab == item
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .padding(vertical = 6.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        navController.navigate(item.route) {
                                                             popUpTo(navController.graph.startDestinationId) {
                                                                 saveState = true
                                                             }
                                                             launchSingleTop = true
                                                             restoreState = true
                                                         }
                                                    }
                                                    .testTag(item.testTag),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    val iconColor = if (isSelected) itemSelectedColor else itemUnselectedColor
                                                    val textColor = if (isSelected) itemSelectedColor else itemUnselectedColor

                                                    Icon(
                                                        imageVector = if (item == TabDestination.Transactions) {
                                                            ImageVector.vectorResource(id = R.drawable.ic_swaps_horiz)
                                                        } else {
                                                            if (isSelected) item.activeIcon else item.inactiveIcon
                                                        },
                                                        contentDescription = item.label,
                                                        tint = iconColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = item.label,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                            letterSpacing = 0.4.sp,
                                                            fontSize = 11.sp
                                                        ),
                                                        color = textColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }


                    }
                }
            }
        }

    private fun getTabRouteIndex(route: String?): Int {
        return when (route) {
            "home" -> 0
            "transactions" -> 1
            "monthly_report" -> 2
            "profile" -> 3
            else -> 0
        }
    }
}

sealed class TabDestination(
    val route: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val testTag: String
) {
    object Home : TabDestination(
        route = "home",
        label = "Home",
        activeIcon = Icons.Filled.Home,
        inactiveIcon = Icons.Outlined.Home,
        testTag = "tab_home"
    )
    object Transactions : TabDestination(
        route = "transactions",
        label = "Transactions",
        activeIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        inactiveIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
        testTag = "tab_transactions"
    )
    object AddExpense : TabDestination(
        route = "add_expense",
        label = "Add",
        activeIcon = Icons.Filled.AddCircle,
        inactiveIcon = Icons.Outlined.AddCircleOutline,
        testTag = "tab_add"
    )
    object MonthlyReport : TabDestination(
        route = "monthly_report",
        label = "Analytics",
        activeIcon = Icons.Filled.Assessment,
        inactiveIcon = Icons.Outlined.Assessment,
        testTag = "tab_report"
    )
    object Profile : TabDestination(
        route = "profile",
        label = "Profile",
        activeIcon = Icons.Filled.Person,
        inactiveIcon = Icons.Outlined.Person,
        testTag = "tab_profile"
    )
}


