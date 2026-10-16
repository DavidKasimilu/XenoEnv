package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.XenoTab

data class BottomBarItem(
    val tab: XenoTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

@Composable
fun XenoBottomBar(
    currentTab: XenoTab,
    onTabSelected: (XenoTab) -> Unit,
    missedCallsCount: Int,
    unreadMessagesCount: Int,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomBarItem(
            tab = XenoTab.CALLS,
            label = "Calls",
            selectedIcon = Icons.Filled.Call,
            unselectedIcon = Icons.Outlined.Call,
            badgeCount = missedCallsCount
        ),
        BottomBarItem(
            tab = XenoTab.KEYPAD,
            label = "Keypad",
            selectedIcon = Icons.Filled.Dialpad,
            unselectedIcon = Icons.Outlined.Dialpad
        ),
        BottomBarItem(
            tab = XenoTab.MESSAGES,
            label = "Messages",
            selectedIcon = Icons.Filled.Message,
            unselectedIcon = Icons.Outlined.Message,
            badgeCount = unreadMessagesCount
        ),
        BottomBarItem(
            tab = XenoTab.BROWSER,
            label = "Browser",
            selectedIcon = Icons.Filled.Public,
            unselectedIcon = Icons.Outlined.Public
        ),
        BottomBarItem(
            tab = XenoTab.GAMES,
            label = "Games",
            selectedIcon = Icons.Filled.SportsEsports,
            unselectedIcon = Icons.Outlined.SportsEsports
        )
    )

    NavigationBar(
        modifier = modifier.testTag("xeno_bottom_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        items.forEach { item ->
            val isSelected = currentTab == item.tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.tab) },
                alwaysShowLabel = false,
                icon = {
                    if (item.badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = if (item.tab == XenoTab.CALLS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                ) {
                                    Text(text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                },
                modifier = Modifier.testTag("tab_${item.tab.name.lowercase()}")
            )
        }
    }
}
