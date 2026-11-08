package com.example.taller3.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.taller3.ui.screens.LoginScreen
import com.example.taller3.ui.screens.MapScreen
import com.example.taller3.ui.screens.RegisterScreen
import com.example.taller3.ui.viewmodel.AuthViewModel

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val rootRoute: NavKey = when {
        state.isSignedIn -> MapRoute
        state.needsProfile -> RegisterRoute
        else -> LoginRoute
    }
    val backStack = rememberNavBackStack(rootRoute)

    LaunchedEffect(rootRoute) {
        if (backStack.firstOrNull() != rootRoute) {
            backStack.clear()
            backStack.add(rootRoute)
        }
    }

    val leaveRegister: () -> Unit = {
        if (!state.isLoading) {
            if (state.needsProfile) {
                authViewModel.signOut()
            } else if (backStack.size > 1) {
                authViewModel.clearForm()
                backStack.removeLastOrNull()
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = leaveRegister,
        entryProvider = entryProvider {
            entry<LoginRoute> {
                LoginScreen(
                    state = state,
                    onEmailChange = authViewModel::onEmailChange,
                    onPasswordChange = authViewModel::onPasswordChange,
                    onSignIn = authViewModel::signInUser,
                    onOpenRegister = {
                        if (backStack.lastOrNull() == LoginRoute) {
                            authViewModel.clearForm()
                            backStack.add(RegisterRoute)
                        }
                    }
                )
            }

            entry<RegisterRoute> {
                RegisterScreen(
                    state = state,
                    onNameChange = authViewModel::onNameChange,
                    onIdentificationChange = authViewModel::onIdentificationChange,
                    onEmailChange = authViewModel::onEmailChange,
                    onPasswordChange = authViewModel::onPasswordChange,
                    onPhoneChange = authViewModel::onPhoneChange,
                    onRegister = authViewModel::registerUser,
                    onBack = leaveRegister
                )
            }

            entry<MapRoute> {
                MapScreen(onSignOut = authViewModel::signOut)
            }
        }
    )
}