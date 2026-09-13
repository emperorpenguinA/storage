package com.mementostorage.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mementostorage.app.auth.GoogleAuthClient
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.DriveAccountSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    authClient: GoogleAuthClient,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val authState by authClient.authState.collectAsState()
    val driveSettings by container.driveSettingsRepository.observeSettings().collectAsState(initial = DriveAccountSettings.EMPTY)

    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Google Drive バックアップ", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (authState.isSignedIn) {
                Text("接続中${authState.accountEmail?.let { ": $it" } ?: ""}")
                driveSettings.lastSyncAt?.let { lastSync ->
                    Text("最終同期: ${lastSync}")
                }
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            statusMessage = null
                            val result = container.syncService.backupNow()
                            statusMessage = if (result.isSuccess) "バックアップが完了しました" else "バックアップに失敗しました: ${result.exceptionOrNull()?.message}"
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("今すぐバックアップ")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            statusMessage = null
                            val result = container.syncService.restoreLatestBackup()
                            statusMessage = when {
                                result.isFailure -> "復元に失敗しました: ${result.exceptionOrNull()?.message}"
                                result.getOrNull() == true -> "バックアップから復元しました"
                                else -> "Drive にバックアップが見つかりませんでした"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Drive から復元")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { scope.launch { authClient.signOut() } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Google アカウントを切断")
                }
            } else {
                Text("写真やバックアップの保存先として Google Drive を接続できます。")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            statusMessage = null
                            val result = authClient.signIn()
                            if (result.isFailure) {
                                statusMessage = "ログインに失敗しました: ${result.exceptionOrNull()?.message}"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Google でログイン")
                }
            }

            if (busy) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator()
            }
            statusMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it)
            }
            authState.authError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
