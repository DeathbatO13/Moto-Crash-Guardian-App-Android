package com.motocrashguardian.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.motocrashguardian.ui.home.HomeScreen
import com.motocrashguardian.ui.onboarding.ConsentScreen
import com.motocrashguardian.ui.onboarding.OnboardingViewModel
import com.motocrashguardian.ui.onboarding.PairingScreen
import com.motocrashguardian.ui.onboarding.PermissionsScreen
import com.motocrashguardian.ui.onboarding.ProfileScreen
import com.motocrashguardian.ui.onboarding.WelcomeScreen
import com.motocrashguardian.ui.privacy.PrivacyScreen

/** Rutas del graph (docs/02 UF-01, docs/06 §3). */
object Route {
    const val WELCOME = "onboarding/welcome"         // S-01
    const val CONSENT = "onboarding/consent"          // S-02
    const val PROFILE = "onboarding/profile"          // S-03b
    const val PERMISSIONS = "onboarding/permissions"  // S-03
    const val PAIRING = "onboarding/pairing"          // S-04
    const val HOME = "home"                           // S-10
    const val PRIVACY = "privacy"                     // Privacidad y datos
}

/**
 * Navegación raíz. 
 * Orden: Bienvenida -> Consentimiento -> Perfil (Datos) -> Permisos -> Vinculación (Bluetooth)
 * 
 * Se ha movido el Bluetooth al final y se ha hecho skippable.
 * Se han eliminado las peticiones de ubicación.
 */
@Composable
fun AppNavigation(
    onboardingCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (onboardingCompleted) Route.HOME else Route.WELCOME,
        modifier = modifier
    ) {
        composable(Route.WELCOME) {
            WelcomeScreen(onContinue = { navController.navigate(Route.CONSENT) })
        }
        composable(Route.CONSENT) {
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            ConsentScreen(
                onBack = { navController.popBackStack() },
                onAccept = {
                    onboardingViewModel.acceptConsent()
                    navController.navigate(Route.PROFILE)
                }
            )
        }
        composable(Route.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Route.PERMISSIONS) }
            )
        }
        composable(Route.PERMISSIONS) {
            PermissionsScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Route.PAIRING) }
            )
        }
        composable(Route.PAIRING) {
            PairingScreen(
                onBack = { navController.popBackStack() },
                onFinish = {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.HOME) {
            HomeScreen(
                modifier = Modifier.fillMaxSize(),
                onOpenPrivacy = { navController.navigate(Route.PRIVACY) }
            )
        }
        composable(Route.PRIVACY) {
            PrivacyScreen(
                onBack = { navController.popBackStack() },
                // Tras borrar todo el estado vuelve a ser el de una instalacion nueva.
                onErased = {
                    navController.navigate(Route.WELCOME) {
                        popUpTo(Route.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
