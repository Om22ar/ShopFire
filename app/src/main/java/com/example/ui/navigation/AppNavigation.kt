package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AppViewModel
import com.example.ui.screens.*

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController, viewModel) }
        composable("onboarding") { OnboardingScreen(navController, viewModel) }
        composable("login") { LoginScreen(navController, viewModel) }
        composable("signup") { SignupScreen(navController, viewModel) }
        composable("forgot_password") { ForgotPasswordScreen(navController, viewModel) }
        composable("main") { MainScreen(viewModel) }
    }
}
