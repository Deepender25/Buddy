package com.buddyapp.Buddy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.text.style.TextOverflow
import com.buddyapp.Buddy.manager.CommandManager
import com.buddyapp.Buddy.model.Command
import com.buddyapp.Buddy.ui.components.ScreenTitle
import com.buddyapp.Buddy.ui.components.SlateCard

@Composable
fun CommandsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val commandManager = remember { CommandManager(context) }
    var commands by remember { mutableStateOf(commandManager.getCommands()) }
    var trigger by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editingCommandTrigger by remember { mutableStateOf<String?>(null) }
    var isTextReplacer by remember { mutableStateOf(false) }
    val prefix = commandManager.getTriggerPrefix()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        ScreenTitle("Commands")

        SlateCard {
            Text(
                text = if (editingCommandTrigger != null) "Edit Command" else "Add Custom Command",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !isTextReplacer,
                        onClick = { isTextReplacer = false }
                    )
                    Text("AI", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isTextReplacer,
                        onClick = { isTextReplacer = true }
                    )
                    Text("Text Replacer", fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = trigger,
                onValueChange = {
                    trigger = it
                    errorMessage = null
                },
                label = { Text(if (isTextReplacer) "Trigger (e.g., ${prefix}address)" else "Trigger (e.g., ${prefix}code)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(if (isTextReplacer) "Replacement Text (e.g., 123 Main St)" else "Prompt (must ask for JUST modified text)") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (editingCommandTrigger != null) {
                    OutlinedButton(onClick = {
                        editingCommandTrigger = null
                        trigger = ""
                        prompt = ""
                        isTextReplacer = false
                        errorMessage = null
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = {
                        val trimmedTrigger = trigger.trim()
                        if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                            if (!trimmedTrigger.startsWith(prefix)) {
                                errorMessage = "Trigger must start with '$prefix'"
                                return@Button
                            }
                            if (commands.any { it.trigger == trimmedTrigger && it.trigger != editingCommandTrigger }) {
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
                            trigger = ""
                            prompt = ""
                            isTextReplacer = false
                            errorMessage = null
                            editingCommandTrigger = null
                        }
                    },
                    enabled = trigger.isNotBlank() && prompt.isNotBlank()
                ) {
                    Text(if (editingCommandTrigger != null) "Save Command" else "Add Command")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                commandManager.resetBuiltInCommands()
                commands = commandManager.getCommands()
            }) {
                Icon(Icons.Default.Restore, contentDescription = "Reset Built-ins")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Defaults")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(commands) { cmd ->
                SlateCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cmd.trigger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cmd.prompt,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (cmd.isBuiltIn) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Built-in",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else if (cmd.isTextReplacer) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Text Replacer",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Row {
                            if (!(cmd.isBuiltIn && cmd.trigger.endsWith("undo"))) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    trigger = cmd.trigger
                                    prompt = cmd.prompt
                                    isTextReplacer = cmd.isTextReplacer
                                    editingCommandTrigger = cmd.trigger
                                    errorMessage = null
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Command",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!cmd.isBuiltIn) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    commandManager.removeCustomCommand(cmd.trigger)
                                    commands = commandManager.getCommands()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Command",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}