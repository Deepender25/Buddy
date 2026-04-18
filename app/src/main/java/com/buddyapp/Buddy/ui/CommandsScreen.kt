package com.buddyapp.Buddy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddyapp.Buddy.manager.CommandManager
import com.buddyapp.Buddy.model.Command
import com.buddyapp.Buddy.ui.components.ScreenTitle
import com.buddyapp.Buddy.ui.components.SlateCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val commandManager = remember { CommandManager(context) }
    var commands by remember { mutableStateOf(commandManager.getCommands()) }

    // Bottom sheet state
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Form state (shared for add & edit)
    var trigger by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editingCommandTrigger by remember { mutableStateOf<String?>(null) }
    var isTextReplacer by remember { mutableStateOf(false) }
    val prefix = commandManager.getTriggerPrefix()

    fun openSheet(cmd: Command? = null) {
        if (cmd != null) {
            trigger = cmd.trigger
            prompt = cmd.prompt
            isTextReplacer = cmd.isTextReplacer
            editingCommandTrigger = cmd.trigger
        } else {
            trigger = ""
            prompt = ""
            isTextReplacer = false
            editingCommandTrigger = null
        }
        errorMessage = null
        showSheet = true
    }

    fun closeSheet() {
        showSheet = false
        trigger = ""
        prompt = ""
        isTextReplacer = false
        editingCommandTrigger = null
        errorMessage = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
        ) {
            // Header row: title + Reset Defaults
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTitle("Commands")
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    commandManager.resetBuiltInCommands()
                    commands = commandManager.getCommands()
                }) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = "Reset Built-ins",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Command count hint
            Text(
                text = "${commands.size} command${if (commands.size == 1) "" else "s"} · tap + to add",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp) // leave space for FAB
            ) {
                items(commands) { cmd ->
                    CommandCard(
                        cmd = cmd,
                        onEdit = { openSheet(cmd) },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            commandManager.removeCustomCommand(cmd.trigger)
                            commands = commandManager.getCommands()
                        }
                    )
                }
            }
        }

        // FAB — positioned bottom-right
        FloatingActionButton(
            onClick = { openSheet() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Command",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    // ── Bottom Sheet ──────────────────────────────────────────────────────────
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (editingCommandTrigger != null) "Edit Command" else "New Command",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Type selector
                Text(
                    text = "Command Type",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip(
                        label = "AI",
                        selected = !isTextReplacer,
                        onClick = { isTextReplacer = false }
                    )
                    TypeChip(
                        label = "Text Replacer",
                        selected = isTextReplacer,
                        onClick = { isTextReplacer = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = trigger,
                    onValueChange = {
                        trigger = it
                        errorMessage = null
                    },
                    label = {
                        Text(
                            if (isTextReplacer) "Trigger (e.g. ${prefix}address)"
                            else "Trigger (e.g. ${prefix}code)"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = {
                        Text(
                            if (isTextReplacer) "Replacement Text (e.g. 123 Main St)"
                            else "Prompt (ask for JUST modified text)"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (editingCommandTrigger != null) {
                        OutlinedButton(
                            onClick = { closeSheet() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                    Button(
                        onClick = {
                            val trimmedTrigger = trigger.trim()
                            if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                                if (!trimmedTrigger.startsWith(prefix)) {
                                    errorMessage = "Trigger must start with '$prefix'"
                                    return@Button
                                }
                                if (commands.any {
                                        it.trigger == trimmedTrigger && it.trigger != editingCommandTrigger
                                    }) {
                                    errorMessage = "A command with this trigger already exists"
                                    return@Button
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val newCommand = Command(trimmedTrigger, prompt.trim(), false, isTextReplacer)
                                if (editingCommandTrigger != null) {
                                    commandManager.updateCommand(editingCommandTrigger!!, newCommand)
                                } else {
                                    commandManager.addCustomCommand(newCommand)
                                }
                                commands = commandManager.getCommands()
                                closeSheet()
                            }
                        },
                        enabled = trigger.isNotBlank() && prompt.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (editingCommandTrigger != null) "Save Changes" else "Add Command")
                    }
                }
            }
        }
    }
}

// ── Command Card ──────────────────────────────────────────────────────────────

@Composable
private fun CommandCard(
    cmd: Command,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Command?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${cmd.trigger}'? This action cannot be undone.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    SlateCard(
        modifier = Modifier.clickable { isExpanded = !isExpanded },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trigger view
                Text(
                    text = cmd.trigger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                // Action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!(cmd.isBuiltIn && cmd.trigger.endsWith("undo"))) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEdit()
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (!cmd.isBuiltIn) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteDialog = true
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 2.dp).fillMaxWidth()) {
                    Text(
                        text = cmd.prompt,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            cmd.isBuiltIn -> TypeBadge(
                                label = "Built-in",
                                containerColor = Color(0xFF2C2C2E),
                                contentColor = Color(0xFFAEAEB2)
                            )
                            cmd.isTextReplacer -> TypeBadge(
                                label = "Text Replacer",
                                containerColor = Color(0xFF1C1C1E),
                                contentColor = Color(0xFFFFFFFF)
                            )
                            else -> TypeBadge(
                                label = "AI",
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color(0xFFFFFFFF)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Small type badge ──────────────────────────────────────────────────────────

@Composable
private fun TypeBadge(
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
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

// ── Type selector chip used inside the sheet ──────────────────────────────────

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgColor,
        tonalElevation = if (selected) 0.dp else 0.dp
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
