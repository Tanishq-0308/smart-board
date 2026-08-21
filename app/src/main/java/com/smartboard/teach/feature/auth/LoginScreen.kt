package com.smartboard.teach.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.theme.ErrorRed
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(dimens.gutterLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Teacher Sign In",
                fontSize = dimens.headlineSize,
                fontWeight = FontWeight.SemiBold,
                color = TextOnSurface,
            )
            Spacer(Modifier.height(dimens.gutterSmall))
            Text(
                text = "Sign in to see your classes, attendance and study material. " +
                    "The whiteboard and notes work without signing in.",
                fontSize = dimens.bodySize,
                color = TextOnSurfaceMuted,
            )

            Spacer(Modifier.height(dimens.gutterLarge))

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(dimens.gutter))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            },
                        )
                    }
                },
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(dimens.gutter))
                ErrorBanner(message = state.errorMessage!!)
            }

            Spacer(Modifier.height(dimens.gutterLarge))

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                shape = RoundedCornerShape(dimens.cornerRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.touchTarget),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sign In", fontSize = dimens.bodySize, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(dimens.gutterLarge))
            DemoCredentialsHint()
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val dimens = SmartBoardTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorRed.copy(alpha = 0.08f), RoundedCornerShape(dimens.cornerRadius))
            .padding(dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = ErrorRed,
            modifier = Modifier.size(dimens.iconSize),
        )
        Spacer(Modifier.width(dimens.gutterSmall))
        Text(text = message, color = ErrorRed, fontSize = dimens.bodySize)
    }
}

/**
 * Phase 1 only. This block disappears when the ERP provides real credentials —
 * it exists so the app is demonstrable without a backend.
 */
@Composable
private fun DemoCredentialsHint() {
    val dimens = SmartBoardTheme.dimens
    Surface(
        shape = RoundedCornerShape(dimens.cornerRadius),
        color = TextOnSurfaceMuted.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Demo accounts (Phase 1)",
                fontSize = dimens.labelSize,
                fontWeight = FontWeight.SemiBold,
                color = TextOnSurfaceMuted,
            )
            Text("demo / demo", fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
            Text("asharma / board123", fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
            Text("rmehta / board123", fontSize = dimens.labelSize, color = TextOnSurfaceMuted)
        }
    }
}
