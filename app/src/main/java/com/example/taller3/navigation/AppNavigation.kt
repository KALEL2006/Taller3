package com.example.taller3.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.taller3.ui.screens.LoginScreen
import com.example.taller3.ui.screens.MapScreen
import com.example.taller3.ui.screens.ProfileScreen
import com.example.taller3.ui.screens.RegisterScreen
import com.example.taller3.ui.viewmodel.AuthViewModel
import com.example.taller3.ui.viewmodel.MapViewModel

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val mapViewModel: MapViewModel = viewModel()
    val activity = LocalActivity.current
    val rootRoute: NavKey = when {
        state.isSignedIn -> MapRoute
        state.needsProfile -> RegisterRoute
        else -> LoginRoute
    }
    val backStack = rememberNavBackStack(rootRoute)

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            mapViewModel.stopSharing()
        }
    }

    LaunchedEffect(rootRoute, state.isLoading) {
        if (!state.isLoading && backStack.firstOrNull() != rootRoute) {
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

    val leaveProfile: () -> Unit = {
        if (backStack.lastOrNull() == ProfileRoute) {
            backStack.removeLastOrNull()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            when (backStack.lastOrNull()) {
                RegisterRoute -> leaveRegister()
                ProfileRoute -> leaveProfile()
                else -> Unit
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
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
                MapScreen(
                    onSignOut = authViewModel::signOut,
                    onOpenProfile = {
                        if (backStack.lastOrNull() == MapRoute) {
                            backStack.add(ProfileRoute)
                        }
                    },
                    viewModel = mapViewModel
                )
            }

            entry<ProfileRoute> {
                ProfileScreen(onBack = leaveProfile)
            }
        }
    )
}