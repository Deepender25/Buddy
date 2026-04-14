package com.buddyapp.Buddy.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.buddyapp.Buddy.api.PythonBridge
import com.buddyapp.Buddy.manager.KeyManager
import com.buddyapp.Buddy.manager.UsageManager
import com.buddyapp.Buddy.ui.components.ScreenTitle
import com.buddyapp.Buddy.ui.components.SlateCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyManager = remember { KeyManager(context) }
    val usageManager = remember { UsageManager(context) }
    var keys by remember { mutableStateOf(keyManager.getKeys()) }
    var newKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val providerType = remember { prefs.getString("provider_type", "gemini") ?: "gemini" }
    val customEndpoint = remember { prefs.getString("custom_endpoint", "") ?: "" }
    val uriHandler = LocalUriHandler.current

    // Bottom sheet state
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeSheet() {
        showSheet = false
        newKey = ""
        testResult = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
        ) {
            ScreenTitle("API Keys")
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${keys.size} key${if (keys.size == 1) "" else "s"} · tap + to add",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                itemsIndexed(keys) { index, key ->
                    KeyCard(
                        index = index,
                        keyStr = key,
                        providerType = providerType,
                        customEndpoint = customEndpoint,
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            keyManager.removeKey(key)
                            usageManager.deleteStats(key)
                            keys = keyManager.getKeys()
                        },
                        onUsageClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController?.navigate("api_usage/$index")
                        }
                    )
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Key",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { closeSheet() },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Add API Key",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newKey,
                    onValueChange = { 
                        newKey = it
                        testResult = null
                    },
                    label = { Text("API Key (e.g. sk-...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                testResult?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        color = if (msg.startsWith("Valid")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { closeSheet() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val trimmedKey = newKey.trim()
                            if (trimmedKey.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    if (keys.contains(trimmedKey)) {
                                        isTesting = false
                                        testResult = "This key has already been added"
                                        return@launch
                                    }
                                    val result = when {
                                        trimmedKey.startsWith("gsk_") ->
                                            PythonBridge.groqValidateKey(trimmedKey)
                                        trimmedKey.startsWith("AIza") ->
                                            PythonBridge.geminiValidateKey(trimmedKey)
                                        trimmedKey.startsWith("sk-") -> {
                                            val ep = if (customEndpoint.isNotBlank()) customEndpoint else "https://api.openai.com/v1"
                                            PythonBridge.openaiValidateKey(trimmedKey, ep)
                                        }
                                        providerType == "groq" ->
                                            PythonBridge.groqValidateKey(trimmedKey)
                                        providerType == "custom" && customEndpoint.isNotBlank() ->
                                            PythonBridge.openaiValidateKey(trimmedKey, customEndpoint)
                                        else ->
                                            PythonBridge.geminiValidateKey(trimmedKey)
                                    }
                                    isTesting = false
                                    if (result.isSuccess) {
                                        keyManager.addKey(trimmedKey)
                                        keys = keyManager.getKeys()
                                        newKey = ""
                                        testResult = "Valid key added!"
                                        closeSheet()
                                    } else {
                                        testResult = result.exceptionOrNull()?.message ?: "Validation failed"
                                    }
                                }
                            }
                        },
                        enabled = newKey.isNotBlank() && !isTesting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isTesting) "Testing..." else "Save Key")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Don't have a key?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderLinkChip("Gemini", "https://aistudio.google.com/app/apikey", uriHandler)
                    ProviderLinkChip("Groq", "https://console.groq.com/keys", uriHandler)
                    ProviderLinkChip("OpenAI", "https://platform.openai.com/api-keys", uriHandler)
                }
            }
        }
    }
}

@Composable
private fun ProviderLinkChip(label: String, url: String, uriHandler: UriHandler) {
    Surface(
        onClick = { uriHandler.openUri(url) },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label + " ↗",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun KeyCard(
    index: Int,
    keyStr: String,
    providerType: String,
    customEndpoint: String,
    onDelete: () -> Unit,
    onUsageClick: () -> Unit
) {
    SlateCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val providerName = when {
                    keyStr.startsWith("AIza")   -> "Gemini"
                    keyStr.startsWith("gsk_")   -> "Groq"
                    keyStr.startsWith("sk-ant") -> "Anthropic"
                    keyStr.startsWith("sk-")    -> "OpenAI"
                    else -> if (providerType == "custom") "Custom" else "Unknown Provider"
                }
                
                Text(
                    text = "••••••••" + keyStr.takeLast(minOf(6, keyStr.length)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeBadge(
                        label = providerName,
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color(0xFFFFFFFF)
                    )
                    TypeBadge(
                        label = "Key ${index + 1}",
                        containerColor = Color(0xFF2C2C2E),
                        contentColor = Color(0xFFAEAEB2)
                    )
                }
            }
            Row {
                IconButton(onClick = onUsageClick) {
                    Icon(
                        imageVector = Icons.Outlined.Analytics,
                        contentDescription = "Usage Analytics",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Key",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}
