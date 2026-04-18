package com.buddyapp.Buddy.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.buddyapp.Buddy.api.PythonBridge
import com.buddyapp.Buddy.manager.HistoryManager
import com.buddyapp.Buddy.manager.KeyManager
import com.buddyapp.Buddy.manager.UsageManager
import com.buddyapp.Buddy.ui.components.ScreenTitle
import com.buddyapp.Buddy.ui.components.SlateCard
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyManager = remember { KeyManager(context) }
    val usageManager = remember { UsageManager(context) }
    val historyManager = remember { HistoryManager(context) }
    
    var keys by remember { mutableStateOf(keyManager.getKeys()) }
    var newKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val providerType = remember { prefs.getString("provider_type", "gemini") ?: "gemini" }
    val customEndpoint = remember { prefs.getString("custom_endpoint", "") ?: "" }
    val uriHandler = LocalUriHandler.current

    val historyItems = remember { historyManager.getHistory() }
    
    val dailyCounts = remember(historyItems) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val dayMs = 86400000L
        val counts = IntArray(7) { 0 }
        
        for (item in historyItems) {
            if (item.timestamp >= todayStart) {
                counts[6]++ 
            } else {
                val diff = todayStart - item.timestamp
                val daysAgo = (diff / dayMs).toInt() + 1
                if (daysAgo in 1..6) {
                    counts[6 - daysAgo]++
                }
            }
        }
        counts.toList()
    }

    val mostUsedCommand = remember(historyItems) {
        val groups = historyItems.groupingBy { it.commandTrigger }.eachCount()
        val maxEntry = groups.maxByOrNull { it.value }
        if (maxEntry != null) {
            Pair(maxEntry.key, maxEntry.value)
        } else {
            Pair("None", 0)
        }
    }

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

            if (keys.isEmpty()) {
                UsageDashboard(dailyCounts, mostUsedCommand)
                Spacer(modifier = Modifier.height(32.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = "No Keys",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No API Keys Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You need an API key to use Buddy.\nTap the + button to add one.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    item {
                        UsageDashboard(dailyCounts, mostUsedCommand)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    itemsIndexed(keys) { index, key ->
                        KeyCard(
                            index = index,
                            keyStr = key,
                            providerType = providerType,
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
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add API Key",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = newKey,
                    onValueChange = { 
                        newKey = it
                        testResult = null
                    },
                    label = { Text("API Key (e.g. sk-...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
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
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
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
private fun UsageDashboard(counts: List<Int>, mostUsedCommand: Pair<String, Int>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SlateCard(
            modifier = Modifier.weight(1.3f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Last 7 Days Usage",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                val barWidth = 6.dp.toPx()
                val gap = (size.width - (barWidth * 7)) / 6
                
                counts.forEachIndexed { index, count ->
                    val x = index * (barWidth + gap)
                    val barHeight = (count.toFloat() / maxCount) * size.height
                    val finalHeight = if (count == 0) 0f else barHeight.coerceAtLeast(4.dp.toPx())
                    val y = size.height - finalHeight
                    
                    drawRoundRect(
                        color = surfaceVariant,
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(barWidth / 2)
                    )
                    
                    if (count > 0) {
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, finalHeight),
                            cornerRadius = CornerRadius(barWidth / 2)
                        )
                    }
                }
            }
        }

        SlateCard(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Most Used",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = mostUsedCommand.first,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${mostUsedCommand.second} times",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onDelete: () -> Unit,
    onUsageClick: () -> Unit
) {
    SlateCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
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
                    text = "$providerName Key ${index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row {
                IconButton(onClick = onUsageClick) {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
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
