package com.hearu.app.ui.auth

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.R
import com.hearu.app.ui.theme.HearUTheme

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

@Composable
fun LoginScreen(
    onLoginSubmit: (String, String) -> Unit = { _, _ -> },
    onSignUpSubmit: (String, String) -> Unit = { _, _ -> },
    viewModel: AuthViewModel = hiltViewModel(),
    initialMode: AuthMode = AuthMode.SIGN_IN
) {
    var authMode by rememberSaveable { mutableStateOf(initialMode) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isLoading = authState is AuthState.Loading
    val serverError = (authState as? AuthState.Error)?.message
    val activeErrorMessage = localError ?: serverError

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Title
        Text(
            text = if (authMode == AuthMode.SIGN_IN) {
                stringResource(R.string.welcome_title)
            } else {
                stringResource(R.string.register_btn)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (authMode == AuthMode.SIGN_IN) {
                "Sign in to connect with a supportive, empathetic listener"
            } else {
                "Join a safe, anonymous haven for listening and healing"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Segmented Tab Row for Mode Switching
        TabRow(
            selectedTabIndex = if (authMode == AuthMode.SIGN_IN) 0 else 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Tab(
                selected = authMode == AuthMode.SIGN_IN,
                onClick = {
                    authMode = AuthMode.SIGN_IN
                    localError = null
                    viewModel.clearError()
                },
                text = {
                    Text(
                        stringResource(R.string.sign_in_tab),
                        fontWeight = if (authMode == AuthMode.SIGN_IN) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = authMode == AuthMode.SIGN_UP,
                onClick = {
                    authMode = AuthMode.SIGN_UP
                    localError = null
                    viewModel.clearError()
                },
                text = {
                    Text(
                        stringResource(R.string.sign_up_tab),
                        fontWeight = if (authMode == AuthMode.SIGN_UP) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        // Email Address Field
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                localError = null
                viewModel.clearError()
            },
            label = { Text("Email") },
            placeholder = { Text("user@example.com") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_field"),
            singleLine = true,
            isError = activeErrorMessage != null,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                localError = null
                viewModel.clearError()
            },
            label = { Text("Password") },
            placeholder = { Text("At least 8 characters") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_field"),
            singleLine = true,
            isError = activeErrorMessage != null,
            shape = RoundedCornerShape(12.dp)
        )

        // Confirm Password Field (Sign Up Mode only)
        AnimatedVisibility(visible = authMode == AuthMode.SIGN_UP) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                        viewModel.clearError()
                    },
                    label = { Text(stringResource(R.string.confirm_password_label)) },
                    placeholder = { Text("Re-enter your password") },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(imageVector = image, contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_password_field"),
                    singleLine = true,
                    isError = activeErrorMessage != null,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Inline Error Message
        AnimatedVisibility(visible = activeErrorMessage != null) {
            activeErrorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Main Action Button (Sign In or Sign Up)
        val isFormValid = if (authMode == AuthMode.SIGN_IN) {
            email.isNotBlank() && password.isNotBlank()
        } else {
            email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank()
        }

        Button(
            onClick = {
                if (authMode == AuthMode.SIGN_IN) {
                    onLoginSubmit(email, password)
                } else {
                    if (password != confirmPassword) {
                        localError = "Passwords do not match"
                    } else if (password.length < 8) {
                        localError = "Password must be at least 8 characters."
                    } else {
                        localError = null
                        onSignUpSubmit(email, password)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(if (authMode == AuthMode.SIGN_IN) "login_button" else "signup_button"),
            enabled = !isLoading && isFormValid,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = if (authMode == AuthMode.SIGN_IN) {
                        stringResource(R.string.login_btn)
                    } else {
                        stringResource(R.string.register_btn)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle Mode Secondary Action
        TextButton(
            onClick = {
                localError = null
                viewModel.clearError()
                authMode = if (authMode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN
            }
        ) {
            Text(
                text = if (authMode == AuthMode.SIGN_IN) {
                    stringResource(R.string.dont_have_account)
                } else {
                    stringResource(R.string.already_have_account)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Preview(name = "Sign In Mode", showBackground = true)
@Composable
fun LoginScreenSignInPreview() {
    HearUTheme {
        LoginScreen(initialMode = AuthMode.SIGN_IN)
    }
}

@Preview(name = "Sign Up Mode", showBackground = true)
@Composable
fun LoginScreenSignUpPreview() {
    HearUTheme {
        LoginScreen(initialMode = AuthMode.SIGN_UP)
    }
}

@Preview(name = "Sign In Dark Theme", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LoginScreenDarkPreview() {
    HearUTheme {
        LoginScreen(initialMode = AuthMode.SIGN_IN)
    }
}
