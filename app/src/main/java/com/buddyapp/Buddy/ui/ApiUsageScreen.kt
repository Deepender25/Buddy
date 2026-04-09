package com.buddyapp.Buddy.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.buddyapp.Buddy.manager.KeyManager
import com.buddyapp.Buddy.manager.UsageManager
import com.buddyapp.Buddy.ui.components.SlateCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiUsageScreen(navController: NavController, keyIndex: Int) {
    val context = LocalContext.current
    val keyManager = remember { KeyManager(context) }
    val usageManager = remember { UsageManager(context) }
    
    val keys = keyManager.getKeys()
    if (keyIndex < 0 || keyIndex >= keys.size) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
        return
    }

    val key = keys[keyIndex]
    val stats = usageManager.getStats(key)

    val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    val providerType = prefs.getString("provider_type", "gemini") ?: "gemini"

    val providerName = when {
        key.startsWith("AIza") -> "Gemini"
        key.startsWith("sk-ant") -> "Anthropic"
        key.startsWith("sk-") -> "OpenAI"
        else -> if (providerType == "custom") "Custom" else "Unknown Provider"
    }
    
    val formattedDate = if (stats.lastUsedTimestamp > 0) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(stats.lastUsedTimestamp))
    } else {
        "Never"
    }

    // Animation for success rate
    val targetSuccessRate = if (stats.totalRequests > 0) (stats.successfulRequests.toFloat() / stats.totalRequests) else 0f
    var startAnimation by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (startAnimation) targetSuccessRate else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "ProgressAnimation"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage Analytics", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Premium Key Identifier Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (stats.errorRate > 50) MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$providerName Key ${keyIndex + 1}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(${key.takeLast(4)})",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Hero Section: Circular Progress
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(180.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round,
                )
                
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(180.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round,
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.totalRequests}",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Total Requests",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E2A23), // subtle green tint
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E4035))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${stats.successfulRequests}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Successful",
                            fontSize = 13.sp,
                            color = Color(0xFFA5D6A7)
                        )
                    }
                }

                // Error Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF331E1E), // subtle red tint
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4D2D2D))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = "Errors",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${stats.failedRequests}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Failed",
                            fontSize = 13.sp,
                            color = Color(0xFFEF9A9A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Error Rate",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f%%", stats.errorRate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (stats.errorRate > 10f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Active",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
