package com.example.entrevista_app_android

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.entrevista_app_android.data.local.auth.TokenStorage
import com.example.entrevista_app_android.data.remote.consent.ConsentRemoteDataSource
import com.example.entrevista_app_android.data.remote.me.FlagsAccesibilidadDto
import com.example.entrevista_app_android.data.remote.me.MeRemoteDataSource
import com.example.entrevista_app_android.data.repository.me.FullProfile
import com.example.entrevista_app_android.data.repository.me.MeRepository
import com.example.entrevista_app_android.ui.auth.LoginViewModel
import com.example.entrevista_app_android.ui.consent.ConsentScreen
import com.example.entrevista_app_android.ui.consent.LastConsentScreen
import com.example.entrevista_app_android.ui.home.HomeScreen
import com.example.entrevista_app_android.ui.onboarding.ObjectivesListScreen
import com.example.entrevista_app_android.ui.onboarding.OnboardingScreen
import com.example.entrevista_app_android.ui.profile.ProfileEditScreen
import com.example.entrevista_app_android.ui.profile.ProfileViewScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope

private enum class LoggedInScreen {
    HOME,
    PROFILE_VIEW,
    PROFILE_EDIT,
    LAST_CONSENT,
    OBJECTIVES,
    ONBOARDING
}

@Composable
fun LoggedInRoot(
    loginViewModel: LoginViewModel
) {
    val loginUiState by loginViewModel.uiState.collectAsState()

    var currentScreen by rememberSaveable { mutableStateOf(LoggedInScreen.HOME) }

    var profileLoaded by rememberSaveable { mutableStateOf(false) }
    var needsConsent by rememberSaveable { mutableStateOf(false) }
    var consentChecked by rememberSaveable { mutableStateOf(false) }

    val consentRemote = remember { ConsentRemoteDataSource() }
    val meRepository = remember { MeRepository(MeRemoteDataSource()) }

    var fullProfile by remember { mutableStateOf<FullProfile?>(null) }
    var accessToken by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(loginUiState.isLoggedIn) {
        if (!loginUiState.isLoggedIn) {
            profileLoaded = false
            consentChecked = false
            needsConsent = false
            currentScreen = LoggedInScreen.HOME
            fullProfile = null
            accessToken = null
            return@LaunchedEffect
        }

        profileLoaded = false
        consentChecked = false
        needsConsent = false
        currentScreen = LoggedInScreen.HOME

        val tokens = TokenStorage.get()
        if (tokens == null) {
            loginViewModel.logout()
            return@LaunchedEffect
        }
        accessToken = tokens.accessToken

        val profileResult = meRepository.loadProfile(tokens.accessToken)
        val profile = profileResult.getOrElse {
            loginViewModel.logout()
            return@LaunchedEffect
        }
        fullProfile = profile

        val needsOnboarding =
            profile.area.isBlank() ||
                    profile.nivelExperiencia.isBlank() ||
                    profile.notaObjetivos.isBlank() ||
                    profile.metaCargo.isNullOrBlank()

        currentScreen = if (needsOnboarding) {
            LoggedInScreen.ONBOARDING
        } else {
            LoggedInScreen.HOME
        }

        profileLoaded = true

        try {
            val current = consentRemote.getCurrentConsent()
            val latest = consentRemote.getLatestUserConsentOrNull()
            needsConsent = latest == null || latest.version != current.version
        } catch (e: Exception) {
            needsConsent = false
        } finally {
            consentChecked = true
        }
    }

    when {
        !profileLoaded || !consentChecked -> {
            LoadingScreen()
        }

        needsConsent -> {
            ConsentScreen(
                onBack = { loginViewModel.logout() },
                onAccepted = { needsConsent = false }
            )
        }

        else -> {
            LoggedInContent(
                currentScreen = currentScreen,
                onChangeScreen = { currentScreen = it },
                fullProfile = fullProfile,
                onFullProfileChange = { fullProfile = it },
                accessToken = accessToken,
                meRepository = meRepository,
                scope = scope,
                onLogout = {
                    loginViewModel.logout()
                    profileLoaded = false
                    consentChecked = false
                    needsConsent = false
                    currentScreen = LoggedInScreen.HOME
                    fullProfile = null
                    accessToken = null
                }
            )
        }
    }
}

@Composable
private fun LoggedInContent(
    currentScreen: LoggedInScreen,
    onChangeScreen: (LoggedInScreen) -> Unit,
    fullProfile: FullProfile?,
    onFullProfileChange: (FullProfile) -> Unit,
    accessToken: String?,
    meRepository: MeRepository,
    scope: CoroutineScope,
    onLogout: () -> Unit
) {
    when (currentScreen) {
        LoggedInScreen.HOME -> {
            HomeScreen(
                onLogout = onLogout,
                onProfileClick = { onChangeScreen(LoggedInScreen.PROFILE_VIEW) },
                onOpenObjectives = {
                    val p = fullProfile
                    if (p?.metaCargo.isNullOrBlank()) {
                        onChangeScreen(LoggedInScreen.ONBOARDING)
                    } else {
                        onChangeScreen(LoggedInScreen.OBJECTIVES)
                    }
                }
            )
        }

        LoggedInScreen.PROFILE_VIEW -> {
            ProfileViewScreen(
                onBack = { onChangeScreen(LoggedInScreen.HOME) },
                onEditClick = { onChangeScreen(LoggedInScreen.PROFILE_EDIT) },
                onShowLastConsent = { onChangeScreen(LoggedInScreen.LAST_CONSENT) }
            )
        }

        LoggedInScreen.PROFILE_EDIT -> {
            ProfileEditScreen(
                onBack = { onChangeScreen(LoggedInScreen.PROFILE_VIEW) },
                onAccountDeleted = { onLogout() }
            )
        }

        LoggedInScreen.LAST_CONSENT -> {
            LastConsentScreen(
                onBack = { onChangeScreen(LoggedInScreen.PROFILE_VIEW) }
            )
        }

        LoggedInScreen.OBJECTIVES -> {
            val p = fullProfile
            ObjectivesListScreen(
                area = p?.area,
                objetivo = p?.notaObjetivos,
                cargoMeta = p?.metaCargo,
                nivel = p?.nivelExperiencia,
                onBack = { onChangeScreen(LoggedInScreen.HOME) },
                onEdit = { onChangeScreen(LoggedInScreen.ONBOARDING) },
                onDelete = {
                    val token = accessToken
                    if (token == null) {
                        onLogout()
                        return@ObjectivesListScreen
                    }
                    scope.launch {
                        meRepository.deleteObjetivo(token)
                            .onSuccess {
                                val updated = p?.copy(metaCargo = null)
                                if (updated != null) {
                                    onFullProfileChange(updated)
                                }
                                onChangeScreen(LoggedInScreen.HOME)
                            }
                            .onFailure {
                                onChangeScreen(LoggedInScreen.HOME)
                            }
                    }
                }
            )
        }

        LoggedInScreen.ONBOARDING -> {
            val tokenLocal = accessToken
            OnboardingScreen(
                initialArea = fullProfile?.area,
                initialObjetivo = fullProfile?.notaObjetivos,
                initialCargoMeta = fullProfile?.metaCargo,
                initialNivel = fullProfile?.nivelExperiencia,
                onFinished = { area, objetivo, cargoMeta, nivel ->
                    val t = tokenLocal
                    if (t == null) {
                        onLogout()
                        return@OnboardingScreen
                    }
                    scope.launch {
                        try {
                            meRepository.guardarOnboardingObjetivo(
                                accessToken = t,
                                area = area,
                                objetivo = objetivo,
                                cargoMeta = cargoMeta,
                                nivel = nivel
                            )

                            val updated = (fullProfile ?: FullProfile(
                                email = "",
                                nombre = "",
                                idioma = "es",
                                nivelExperiencia = "",
                                area = "",
                                pais = "CL",
                                notaObjetivos = "",
                                metaCargo = null,
                                flags = FlagsAccesibilidadDto(
                                    tts = false,
                                    altoContraste = false,
                                    subtitulos = false
                                )
                            )).copy(
                                area = area,
                                notaObjetivos = objetivo,
                                nivelExperiencia = nivel,
                                metaCargo = cargoMeta
                            )

                            onFullProfileChange(updated)
                        } catch (e: Exception) {
                            Log.e("LoggedInRoot", "Error guardando onboarding", e)
                        } finally {
                            onChangeScreen(LoggedInScreen.HOME)
                        }
                    }
                },
                onCancel = {
                    onChangeScreen(LoggedInScreen.HOME)
                }
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Preparando tu cuenta…")
        }
    }
}
