package com.example.taller3.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.taller3.R
import com.example.taller3.data.ProfileMessage
import com.example.taller3.data.ProfileUiState
import com.example.taller3.ui.components.AppTextField
import com.example.taller3.ui.components.HintText
import com.example.taller3.ui.components.LoadingButton
import com.example.taller3.ui.components.SectionCard
import com.example.taller3.ui.components.StatusMessage
import com.example.taller3.ui.viewmodel.ProfileViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = capturePath?.let { File(it) }
        capturePath = null
        if (saved && file != null && file.length() > 0L) {
            viewModel.onPhotoCaptured(file)
        } else {
            file?.delete()
            if (saved) viewModel.onCameraError()
        }
    }

    Scaffold(
        modifier = Modifier.background(
            Brush.verticalGradient(
                0f to colors.primaryContainer,
                0.35f to colors.background,
                1f to colors.background
            )
        ),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isSaving) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        if (state.isLoading) {
            Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (profile == null) {
            Column(
                modifier = contentModifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileFeedback(state.profileMessage)
                LoadingButton(
                    text = R.string.profile_retry,
                    onClick = viewModel::loadProfile,
                    enabled = true,
                    loading = false,
                    icon = R.drawable.ic_refresh,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
            }
        } else {
            val enabled = !state.isSaving

            Box(modifier = contentModifier, contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProfileHeader(
                        state = state,
                        name = profile.name,
                        email = profile.email,
                        canOpenCamera = capturePath == null,
                        onTakePhoto = {
                            var temporaryFile: File? = null
                            try {
                                val folder = File(context.cacheDir, "profile_photos")
                                folder.mkdirs()
                                val file = File.createTempFile("photo_", ".jpg", folder)
                                temporaryFile = file
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                capturePath = file.absolutePath
                                camera.launch(uri)
                            } catch (_: Exception) {
                                temporaryFile?.delete()
                                capturePath = null
                                viewModel.onCameraError()
                            }
                        },
                        onSavePhoto = { viewModel.savePhoto(context) }
                    )

                    SectionCard(title = R.string.profile_personal_info, icon = R.drawable.ic_person) {
                        AppTextField(
                            value = profile.email,
                            onValueChange = {},
                            label = R.string.login_email,
                            icon = R.drawable.ic_email,
                            enabled = enabled,
                            readOnly = true
                        )
                        AppTextField(
                            value = profile.name,
                            onValueChange = viewModel::onNameChange,
                            label = R.string.register_name,
                            icon = R.drawable.ic_person,
                            enabled = enabled
                        )
                        AppTextField(
                            value = profile.identification,
                            onValueChange = viewModel::onIdentificationChange,
                            label = R.string.register_identification,
                            icon = R.drawable.ic_badge,
                            enabled = enabled,
                            keyboardType = KeyboardType.Number
                        )
                        AppTextField(
                            value = profile.phone,
                            onValueChange = viewModel::onPhoneChange,
                            label = R.string.register_phone,
                            icon = R.drawable.ic_phone,
                            enabled = enabled,
                            keyboardType = KeyboardType.Phone
                        )
                        LoadingButton(
                            text = if (state.isSavingProfile) {
                                R.string.register_saving
                            } else {
                                R.string.profile_save_details
                            },
                            onClick = viewModel::saveProfile,
                            enabled = enabled,
                            loading = state.isSavingProfile,
                            icon = R.drawable.ic_check
                        )
                        ProfileFeedback(state.profileMessage)
                    }

                    SectionCard(title = R.string.profile_security, icon = R.drawable.ic_lock) {
                        AppTextField(
                            value = state.currentPassword,
                            onValueChange = viewModel::onCurrentPasswordChange,
                            label = R.string.profile_current_password,
                            icon = R.drawable.ic_lock,
                            enabled = enabled,
                            keyboardType = KeyboardType.Password
                        )
                        AppTextField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            label = R.string.profile_new_password,
                            icon = R.drawable.ic_lock,
                            enabled = enabled,
                            keyboardType = KeyboardType.Password
                        )
                        AppTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = R.string.profile_confirm_password,
                            icon = R.drawable.ic_lock,
                            enabled = enabled,
                            keyboardType = KeyboardType.Password
                        )
                        LoadingButton(
                            text = if (state.isChangingPassword) {
                                R.string.profile_changing_password
                            } else {
                                R.string.profile_change_password
                            },
                            onClick = viewModel::changePassword,
                            enabled = enabled,
                            loading = state.isChangingPassword,
                            icon = R.drawable.ic_lock
                        )
                        ProfileFeedback(state.passwordMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    name: String,
    email: String,
    canOpenCamera: Boolean,
    onTakePhoto: () -> Unit,
    onSavePhoto: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val model: Any? = state.pendingPhoto ?: state.profile?.photoUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(132.dp)) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .align(Alignment.Center)
                    .border(4.dp, colors.primary, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = stringResource(R.string.profile_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = stringResource(R.string.profile_photo),
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            FilledIconButton(
                onClick = onTakePhoto,
                enabled = canOpenCamera && !state.isSaving,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(44.dp)
                    .border(3.dp, colors.background, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_photo_camera),
                    contentDescription = stringResource(R.string.profile_take_photo),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (name.isNotBlank()) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        }
        if (email.isNotBlank()) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }

        if (state.pendingPhoto != null) {
            LoadingButton(
                text = if (state.isSavingPhoto) {
                    R.string.profile_saving_photo
                } else {
                    R.string.profile_save_photo
                },
                onClick = onSavePhoto,
                enabled = !state.isSaving,
                loading = state.isSavingPhoto,
                icon = R.drawable.ic_check,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        } else {
            HintText(R.string.profile_photo_hint)
        }
        ProfileFeedback(state.photoMessage)
    }
}

@Composable
private fun ProfileFeedback(message: ProfileMessage?) {
    if (message != null) {
        StatusMessage(text = message.textRes, isError = message.isError)
    }
}
