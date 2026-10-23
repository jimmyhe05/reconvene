package com.reconvene.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reconvene.ui.discover.DiscoverScreen
import com.reconvene.ui.groups.GroupsScreen
import com.reconvene.ui.navigation.DiscoverRoute
import com.reconvene.ui.navigation.GroupsRoute
import com.reconvene.ui.navigation.ProfileRoute
import com.reconvene.ui.navigation.TopLevelDestination
import com.reconvene.ui.profile.ProfileScreen
import com.reconvene.ui.theme.ReconveneTheme

private const val MAX_NAV_LABEL_SCALE = 1.5f

@Composable
fun ReconveneApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            ReconveneBottomBar(
                currentDestination = backStackEntry?.destination,
                onTabSelected = navController::navigateToTab,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DiscoverRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<DiscoverRoute> { DiscoverScreen() }
            composable<GroupsRoute> { GroupsScreen() }
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}

@Composable
private fun ReconveneBottomBar(
    currentDestination: NavDestination?,
    onTabSelected: (TopLevelDestination) -> Unit,
) {
    Column {
        HorizontalDivider()
        NavigationBar {
            TopLevelDestination.entries.forEach { tab ->
                val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                    label = { NavLabel(tab.label) },
                )
            }
        }
    }
}

// Nav labels stop growing at 1.5x so they still fit at the largest font sizes.
@Composable
private fun NavLabel(@StringRes text: Int) {
    val style = MaterialTheme.typography.labelMedium
    val fontScale = LocalDensity.current.fontScale
    val shrink = if (fontScale > MAX_NAV_LABEL_SCALE) MAX_NAV_LABEL_SCALE / fontScale else 1f
    Text(
        text = stringResource(text),
        style = style.copy(fontSize = style.fontSize * shrink, lineHeight = style.lineHeight * shrink),
    )
}

private fun NavController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Preview
@Composable
private fun ReconveneAppPreview() {
    ReconveneTheme {
        ReconveneApp()
    }
}
