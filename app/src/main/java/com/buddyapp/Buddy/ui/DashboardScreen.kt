package com.buddyapp.Buddy.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.buddyapp.Buddy.manager.CommandManager
import com.buddyapp.Buddy.manager.KeyManager
import com.buddyapp.Buddy.ui.components.ScreenTitle
import com.buddyapp.Buddy.ui.components.SlateCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun checkServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyManager = remember { KeyManager(context) }
    val commandManager = remember { CommandManager(context) }
    var isServiceEnabled by remember { mutableStateOf(checkServiceEnabled(context)) }
    var keyCount by remember { mutableIntStateOf(keyManager.getKeys().size) }
    var currentPrefix by remember { mutableStateOf(commandManager.getTriggerPrefix()) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var apkDownloadUrl by remember { mutableStateOf<String?>(null) }
    val versionName = remember { 
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) { "1.0.0" }
    }
    val uriHandler = LocalUriHandler.current

    // Use the Activity lifecycle so polling only restarts when the app returns
    // from the background, not when switching between navbar tabs.
    val activityLifecycle = (context as? ComponentActivity)?.lifecycle

    LaunchedEffect(activityLifecycle) {
        val lifecycle = activityLifecycle ?: return@LaunchedEffect
        
        launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/Deepender25/Buddy/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name").removePrefix("v")
                    latestVersion = tagName
                    val assets = json.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            apkDownloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors gracefully
            }
        }
        
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                isServiceEnabled = checkServiceEnabled(context)
                keyCount = keyManager.getKeys().size
                currentPrefix = commandManager.getTriggerPrefix()
                delay(3000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Top row: title + GitHub/update icon button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScreenTitle("Dashboard")
            latestVersion?.let { latest ->
                val isLatest = versionName == latest || versionName >= latest
                TextButton(
                    onClick = {
                        if (isLatest) {
                            uriHandler.openUri("https://github.com/Deepender25/Buddy")
                        } else {
                            uriHandler.openUri("https://github.com/Deepender25/Buddy/releases/latest")
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    if (isLatest) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = "Star on GitHub",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Star",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                contentDescription = "Update Available",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Update",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (!isServiceEnabled) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Setup Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To empower your typing experience, Buddy needs Accessibility permissions to monitor triggered commands.",
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Accessibility Request", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant ?: MaterialTheme.colorScheme.outline),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Service Active",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = "API Keys",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "API Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$keyCount keys securely stored.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            if (keyCount == 0) {
                Text(
                    text = "The application requires an active API key to process AI requests.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Banking App Warning Card
        if (isServiceEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "Banking",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Using a banking app?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Turn off Buddy first — banking apps block Accessibility Services.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Turn Off", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Usage Instructions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            val instructions = listOf(
                "Ensure accessibility permissions are granted.",
                "Insert your desired API keys via the Keys tab.",
                "Type anywhere in Android, terminating commands with a configured trigger (e.g., '${currentPrefix}casual').",
                "Wait momentarily as Buddy seamlessly substitutes your query with the AI-generated context."
            )
            
            instructions.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = "${index + 1}.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = step,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
