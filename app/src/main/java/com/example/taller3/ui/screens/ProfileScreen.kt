package com.example.taller3.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.taller3.R
import com.example.taller3.data.ProfileMessage
import com.example.taller3.data.ProfileUiState
import com.example.taller3.ui.viewmodel.ProfileViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val context = LocalContext.current
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSaving) {
                        Text(stringResource(R.string.profile_back))
                    }
                }
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
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileFeedback(state.profileMessage)
                Button(onClick = viewModel::loadProfile) {
                    Text(stringResource(R.string.profile_retry))
                }
            }
        } else {
            val enabled = !state.isSaving

            Column(
                modifier = contentModifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfilePhotoEditor(
                    state = state,
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProfileField(R.string.login_email, profile.email, {}, enabled, readOnly = true)
                ProfileField(R.string.register_name, profile.name, viewModel::onNameChange, enabled)
                ProfileField(
                    R.string.register_identification,
                    profile.identification,
                    viewModel::onIdentificationChange,
                    enabled,
                    keyboardType = KeyboardType.Number
                )
                ProfileField(
                    R.string.register_phone,
                    profile.phone,
                    viewModel::onPhoneChange,
                    enabled,
                    keyboardType = KeyboardType.Phone
                )
                Button(
                    onClick = viewModel::saveProfile,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = if (state.isSavingProfile) {
                        R.string.register_saving
                    } else {
                        R.string.profile_save_details
                    }
                    Text(stringResource(label))
                }
                ProfileFeedback(state.profileMessage)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.profile_change_password),
                    style = MaterialTheme.typography.titleMedium
                )
                ProfileField(
                    R.string.profile_current_password,
                    state.currentPassword,
                    viewModel::onCurrentPasswordChange,
                    enabled,
                    keyboardType = KeyboardType.Password
                )
                ProfileField(
                    R.string.profile_new_password,
                    state.newPassword,
                    viewModel::onNewPasswordChange,
                    enabled,
                    keyboardType = KeyboardType.Password
                )
                ProfileField(
                    R.string.profile_confirm_password,
                    state.confirmPassword,
                    viewModel::onConfirmPasswordChange,
                    enabled,
                    keyboardType = KeyboardType.Password
                )
                Button(
                    onClick = viewModel::changePassword,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = if (state.isChangingPassword) {
                        R.string.profile_changing_password
                    } else {
                        R.string.profile_change_password
                    }
                    Text(stringResource(label))
                }
                ProfileFeedback(state.passwordMessage)
            }
        }
    }
}

@Composable
private fun ProfilePhotoEditor(
    state: ProfileUiState,
    canOpenCamera: Boolean,
    onTakePhoto: () -> Unit,
    onSavePhoto: () -> Unit
) {
    val model: Any? = state.pendingPhoto ?: state.profile?.photoUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.profile_photo), style = MaterialTheme.typography.titleMedium)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.profile_photo),
                modifier = Modifier.size(96.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        OutlinedButton(onClick = onTakePhoto, enabled = canOpenCamera && !state.isSaving) {
            Text(stringResource(R.string.profile_take_photo))
        }
        if (state.pendingPhoto != null) {
            Button(onClick = onSavePhoto, enabled = !state.isSaving) {
                val label = if (state.isSavingPhoto) {
                    R.string.profile_saving_photo
                } else {
                    R.string.profile_save_photo
                }
                Text(stringResource(label))
            }
        }
        ProfileFeedback(state.photoMessage)
    }
}

@Composable
private fun ProfileField(
    @StringRes label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (keyboardType == KeyboardType.Password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        }
    )
}

@Composable
private fun ProfileFeedback(message: ProfileMessage?) {
    if (message != null) {
        Text(
            text = stringResource(message.textRes),
            color = if (message.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}