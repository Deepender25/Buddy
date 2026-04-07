package com.buddyapp.Buddy.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddyapp.Buddy.api.PythonBridge
import com.buddyapp.Buddy.manager.KeyManager
import com.buddyapp.Buddy.ui.components.ScreenTitle
import com.buddyapp.Buddy.ui.components.SlateCard
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun KeysScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyManager = remember { KeyManager(context) }
    var keys by remember { mutableStateOf(keyManager.getKeys()) }
    var newKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val providerType = remember { prefs.getString("provider_type", "gemini") ?: "gemini" }
    val customEndpoint = remember { prefs.getString("custom_endpoint", "") ?: "" }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        ScreenTitle("API Keys")

        SlateCard {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        if (newKey.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isTesting = true; testResult = null
                            scope.launch {
                                val trimmedKey = newKey.trim()
                                if (keys.contains(trimmedKey)) {
                                    isTesting = false
                                    testResult = "This key has already been added"
                                    return@launch
                                }
                                // ── Validation goes through Python ──────────
                                val result = if (providerType == "custom" && customEndpoint.isNotBlank()) {
                                    PythonBridge.openaiValidateKey(trimmedKey, customEndpoint)
                                } else {
                                    PythonBridge.geminiValidateKey(trimmedKey)
                                }
                                isTesting = false
                                if (result.isSuccess) {
                                    keyManager.addKey(trimmedKey)
                                    keys = keyManager.getKeys()
                                    newKey = ""
                                    testResult = "Valid key added!"
                                } else {
                                    testResult = result.exceptionOrNull()?.message ?: "Validation failed"
                                }
                            }
                        }
                    },
                    enabled = newKey.isNotBlank() && !isTesting
                ) { Text(if (isTesting) "Testing..." else "Add Key") }
            }
            testResult?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.startsWith("Valid")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How to get an API Key",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Google Gemini",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            TextButton(
                onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Get Gemini API Key ↗",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "OpenAI & OpenAI-Compatible",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            TextButton(
                onClick = { uriHandler.openUri("https://platform.openai.com/api-keys") },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Get OpenAI API Key ↗",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(keys) { index, key ->
                SlateCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Key ${index + 1}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "••••••••" + key.takeLast(6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            keyManager.removeKey(key); keys = keyManager.getKeys()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Key",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
