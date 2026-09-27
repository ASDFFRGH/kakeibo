package jp.local.kakeibo.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import jp.local.kakeibo.KakeiboScreen
import jp.local.kakeibo.CombinedSummaryRoute
import jp.local.kakeibo.gambling.GamblingRoute

enum class AppTab {
    HOUSEHOLD,
    GAMBLING,
    COMBINED_SUMMARY,
}

@Composable
fun KakeiboApp() {
    var selectedTabName by rememberSaveable { mutableStateOf(AppTab.HOUSEHOLD.name) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val selectedTab = AppTab.valueOf(selectedTabName)
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = selectedTab != AppTab.HOUSEHOLD && !fullscreen) {
        selectedTabName = AppTab.HOUSEHOLD.name
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!fullscreen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == AppTab.HOUSEHOLD,
                        onClick = {
                            selectedTabName = AppTab.HOUSEHOLD.name
                            fullscreen = false
                        },
                        icon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                        label = { Text("家計簿") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.GAMBLING,
                        onClick = {
                            selectedTabName = AppTab.GAMBLING.name
                            fullscreen = false
                        },
                        icon = { Icon(Icons.Outlined.Casino, contentDescription = null) },
                        label = { Text("ギャンブル") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.COMBINED_SUMMARY,
                        onClick = {
                            selectedTabName = AppTab.COMBINED_SUMMARY.name
                            fullscreen = false
                        },
                        icon = { Icon(Icons.Outlined.Assessment, contentDescription = null) },
                        label = { Text("全体サマリー") },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            stateHolder.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    AppTab.HOUSEHOLD -> KakeiboScreen(onFullscreenChange = { fullscreen = it })
                    AppTab.GAMBLING -> GamblingRoute(onFullscreenChange = { fullscreen = it })
                    AppTab.COMBINED_SUMMARY -> CombinedSummaryRoute()
                }
            }
        }
    }
}
