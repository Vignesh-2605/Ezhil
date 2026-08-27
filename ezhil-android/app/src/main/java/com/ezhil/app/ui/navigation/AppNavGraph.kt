package com.ezhil.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.ui.screens.SplashScreen
import com.ezhil.app.ui.screens.auth.NewStudentProfileScreen
import com.ezhil.app.ui.screens.auth.RoleSelectionScreen
import com.ezhil.app.ui.screens.auth.StudentLoginScreen
import com.ezhil.app.ui.screens.auth.StudentProfileScreen
import com.ezhil.app.ui.screens.auth.TeacherLoginScreen
import com.ezhil.app.ui.screens.auth.TeacherRegisterScreen
import com.ezhil.app.ui.screens.onboarding.OnboardingScreen
import com.ezhil.app.ui.screens.student.*
import com.ezhil.app.ui.screens.teacher.*

sealed class Screen(val route: String) {
    // Splash
    object Splash              : Screen("splash")

    // Onboarding
    object Onboarding          : Screen("onboarding")

    // Auth
    object RoleSelection       : Screen("role_selection")
    object TeacherLogin        : Screen("teacher_login")
    object StudentLogin        : Screen("student_login")
    object StudentProfile      : Screen("student_profile")
    object NewStudentProfile   : Screen("new_student_profile")
    object TeacherRegister     : Screen("teacher_register")

    // Student
    object StudentHome         : Screen("student_home")
    object ReadAloud           : Screen("read_aloud")
    object GamesHub            : Screen("games_hub")
    object PhonicsGames        : Screen("phonics_games")
    object MatchSound          : Screen("match_sound")
    object SpotLetter          : Screen("spot_letter")
    object BuildWord           : Screen("build_word")
    object MyLessons           : Screen("my_lessons")
    object MyJourney           : Screen("my_journey")
    object AssessmentsHistory  : Screen("assessments_history")
    object Leaderboard         : Screen("leaderboard")
    object StudentProfileView  : Screen("student_profile_view")
    object Achievement         : Screen("achievement/{type}/{stars}") {
        fun route(type: String = "well_done", stars: Int = 3) = "achievement/$type/$stars"
    }
    object LessonPlayer        : Screen("lesson_player/{lessonId}") {
        fun route(lessonId: String) = "lesson_player/$lessonId"
    }
    object VocabularyDetail    : Screen("lesson_vocab/{lessonId}") {
        fun route(lessonId: String) = "lesson_vocab/$lessonId"
    }
    object ComprehensionQuiz   : Screen("lesson_quiz/{lessonId}") {
        fun route(lessonId: String) = "lesson_quiz/$lessonId"
    }

    // Teacher
    object TeacherDashboard    : Screen("teacher_dashboard")
    object StudentDetail       : Screen("student_detail/{studentId}") {
        fun route(studentId: String) = "student_detail/$studentId"
    }
    object LessonStudio        : Screen("lesson_studio")
    object LessonStudioReview  : Screen("lesson_studio_review/{lessonId}") {
        fun route(lessonId: String) = "lesson_studio_review/$lessonId"
    }
    object RosterManagement    : Screen("roster_management")
    object LessonLibrary       : Screen("lesson_library")
    object Reports             : Screen("reports")
    object TeacherProfile      : Screen("teacher_profile")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    securePrefs: SecurePrefs
) {
    // Spring-feel screen transitions: pages rise in and settle; back slides
    // away. Short fades keep it gentle for motion-sensitive children.
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = {
            androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(220)
            ) + androidx.compose.animation.slideInVertically(
                initialOffsetY = { it / 14 },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                )
            )
        },
        exitTransition = {
            androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(140)
            )
        },
        popEnterTransition = {
            androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(220)
            )
        },
        popExitTransition = {
            androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(140)
            ) + androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it / 14 },
                animationSpec = androidx.compose.animation.core.tween(200)
            )
        }
    ) {

        // Splash
        composable(Screen.Splash.route) {
            SplashScreen(navController, securePrefs)
        }

        // Onboarding
        composable(Screen.Onboarding.route) { OnboardingScreen(navController) }

        // Auth
        composable(Screen.RoleSelection.route)      { RoleSelectionScreen(navController) }
        composable(Screen.TeacherLogin.route)       { TeacherLoginScreen(navController) }
        composable(Screen.StudentLogin.route)       { StudentLoginScreen(navController) }
        composable(Screen.StudentProfile.route)     { StudentProfileScreen(navController) }
        composable(Screen.NewStudentProfile.route)  { NewStudentProfileScreen(navController) }
        composable(Screen.TeacherRegister.route)    { TeacherRegisterScreen(navController) }

        // Student
        composable(Screen.StudentHome.route)        { StudentHomeScreen(navController) }
        composable(Screen.ReadAloud.route)          { ReadAloudScreen(navController) }
        composable(Screen.GamesHub.route)           { GamesHubScreen(navController) }
        composable(Screen.PhonicsGames.route)       { PhonicsGamesScreen(navController) }
        composable(Screen.MatchSound.route)         { MatchSoundGameScreen(navController) }
        composable(Screen.SpotLetter.route)         { SpotLetterGameScreen(navController) }
        composable(Screen.BuildWord.route)          { BuildWordGameScreen(navController) }
        composable(Screen.MyLessons.route)          { MyLessonsScreen(navController) }
        composable(Screen.MyJourney.route)          { MyJourneyScreen(navController) }
        composable(Screen.AssessmentsHistory.route) { AssessmentsHistoryScreen(navController) }
        composable(Screen.Leaderboard.route)        { LeaderboardScreen(navController) }
        composable(Screen.StudentProfileView.route) { StudentProfileViewScreen(navController) }
        composable(Screen.Achievement.route) { backStack ->
            val type  = backStack.arguments?.getString("type") ?: "well_done"
            val stars = backStack.arguments?.getString("stars")?.toIntOrNull() ?: 3
            val achievementType = when (type) {
                "lesson_complete" -> AchievementType.LESSON_COMPLETE
                "streak"          -> AchievementType.STREAK
                "reader_badge"    -> AchievementType.READER_BADGE
                else              -> AchievementType.WELL_DONE
            }
            AchievementScreen(navController, achievementType, stars)
        }
        composable(Screen.LessonPlayer.route) { backStack ->
            val lessonId = backStack.arguments?.getString("lessonId") ?: ""
            LessonPlayerScreen(navController, lessonId)
        }
        composable(Screen.VocabularyDetail.route) { backStack ->
            val lessonId = backStack.arguments?.getString("lessonId") ?: ""
            VocabularyDetailScreen(navController, lessonId)
        }
        composable(Screen.ComprehensionQuiz.route) { backStack ->
            val lessonId = backStack.arguments?.getString("lessonId") ?: ""
            ComprehensionQuizScreen(navController, lessonId)
        }

        // Teacher
        composable(Screen.TeacherDashboard.route)   { TeacherDashboardScreen(navController) }
        composable(Screen.StudentDetail.route) { backStack ->
            val studentId = backStack.arguments?.getString("studentId") ?: ""
            StudentDetailScreen(navController, studentId)
        }
        composable(Screen.LessonStudio.route)       { LessonStudioScreen(navController) }
        composable(Screen.LessonStudioReview.route) { backStack ->
            val lessonId = backStack.arguments?.getString("lessonId") ?: ""
            LessonStudioReviewScreen(navController, lessonId)
        }
        composable(Screen.RosterManagement.route)   { RosterManagementScreen(navController) }
        composable(Screen.LessonLibrary.route)      { LessonLibraryScreen(navController) }
        composable(Screen.Reports.route)            { ReportsScreen(navController) }
        composable(Screen.TeacherProfile.route)     { TeacherProfileScreen(navController) }
    }
}
