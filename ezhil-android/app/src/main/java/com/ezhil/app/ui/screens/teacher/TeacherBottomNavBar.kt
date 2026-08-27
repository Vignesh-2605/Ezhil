package com.ezhil.app.ui.screens.teacher

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.theme.Amber
import com.ezhil.app.ui.theme.AmberDim
import com.ezhil.app.ui.theme.BgNavBar
import com.ezhil.app.ui.theme.TextMuted

data class TeacherNavItem(
    val route: String,
    val labelTamil: String,
    val labelEnglish: String,
    val icon: ImageVector
)

@Composable
fun TeacherBottomNavBar(
    navController: NavHostController,
    currentRoute: String,
    langVm: AppLanguageViewModel = hiltViewModel(),
) {
    // The bar always drew labelTamil, so the language toggle changed every
    // other word on the screen and left the navigation in Tamil. It also made
    // the bar overflow: "டாஷ்போர்டு" needs 132dp of a 144dp tab and wrapped to
    // two lines against the screen edge, where "Home" fits easily.
    val language by langVm.language.collectAsState()
    val items = listOf(
        TeacherNavItem(Screen.TeacherDashboard.route, "முகப்பு",    "Home",    Icons.Default.Home),
        TeacherNavItem(Screen.LessonLibrary.route,    "நூலகம்",     "Library", Icons.Default.Book),
        TeacherNavItem(Screen.Reports.route,          "அறிக்கை",    "Reports", Icons.Default.Analytics),
        TeacherNavItem(Screen.RosterManagement.route, "பட்டியல்",   "Roster",  Icons.Default.People),
        TeacherNavItem(Screen.TeacherProfile.route,   "சுயவிவரம்",  "Profile", Icons.Default.Person),
    )

    NavigationBar(containerColor = BgNavBar, tonalElevation = 0.dp) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Screen.TeacherDashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.labelEnglish,
                        tint = if (selected) Amber else TextMuted
                    )
                },
                label = {
                    Text(
                        text = if (language == AppLanguage.TAMIL) item.labelTamil
                               else item.labelEnglish,
                        maxLines = 2,
                        color = if (selected) Amber else TextMuted
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = Amber,
                    unselectedIconColor = TextMuted,
                    selectedTextColor   = Amber,
                    unselectedTextColor = TextMuted,
                    indicatorColor      = AmberDim
                )
            )
        }
    }
}
