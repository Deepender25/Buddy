package com.buddyapp.Buddy.manager

import android.content.Context
import android.content.SharedPreferences
import com.buddyapp.Buddy.model.Command
import org.json.JSONArray
import org.json.JSONObject

class CommandManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("commands", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_PREFIX = "/"
        const val PREF_TRIGGER_PREFIX = "trigger_prefix"
    }

    // Built-in command names (without prefix) and their prompts
    private val builtInDefinitions = listOf(
        "fix" to "Fix grammar, spelling, and punctuation errors. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the corrected text.",
        "improve" to "Improve clarity and readability. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the improved text.",
        "shorten" to "Shorten while preserving core meaning. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the shortened text.",
        "expand" to "Expand with more detail and context. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the expanded text.",
        "formal" to "Rewrite in a formal, professional tone. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the rewritten text.",
        "casual" to "Rewrite in a casual, friendly tone. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the rewritten text.",
        "emoji" to "Add relevant emojis throughout. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the text with emojis added.",
        "reply" to "Generate a contextual reply to this message. CRITICAL: You MUST preserve the exact original language, alphabet, and script. For example, if the input is in Hinglish (Hindi written in Latin alphabet), you MUST output in Hinglish. Do NOT translate to Devanagari or any other script. Do not change the original language. Return only the reply.",
        "undo" to "Undo the last replacement and restore the original text."
    )

    fun getTriggerPrefix(): String {
        return settingsPrefs.getString(PREF_TRIGGER_PREFIX, DEFAULT_PREFIX) ?: DEFAULT_PREFIX
    }

    fun setTriggerPrefix(newPrefix: String): Boolean {
        if (newPrefix.length != 1 || newPrefix[0].isLetterOrDigit() || newPrefix[0].isWhitespace()) return false
        val oldPrefix = getTriggerPrefix()
        if (oldPrefix == newPrefix) return true
        // Write prefix FIRST (synchronous) so built-ins work immediately if process dies mid-migration
        settingsPrefs.edit().putString(PREF_TRIGGER_PREFIX, newPrefix).commit()
        // Migrate custom command triggers
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = JSONArray(customStr)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val oldTrigger = obj.getString("trigger")
            val migrated = if (oldTrigger.startsWith(oldPrefix)) {
                newPrefix + oldTrigger.removePrefix(oldPrefix)
            } else oldTrigger
            val newObj = JSONObject()
            newObj.put("trigger", migrated)
            newObj.put("prompt", obj.getString("prompt"))
            newObj.put("is_text_replacer", obj.optBoolean("is_text_replacer", false))
            newArr.put(newObj)
        }
        prefs.edit().putString("custom_commands", newArr.toString()).apply()

        val overStr = prefs.getString("builtin_overrides", "{}") ?: "{}"
        val overrides = JSONObject(overStr)
        val keys = overrides.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = overrides.getJSONObject(key)
            val oldObjTrigger = obj.optString("trigger", "")
            if (oldObjTrigger.startsWith(oldPrefix)) {
                obj.put("trigger", newPrefix + oldObjTrigger.removePrefix(oldPrefix))
            }
        }
        prefs.edit().putString("builtin_overrides", overrides.toString()).apply()

        return true
    }

    private fun getBuiltInCommands(): List<Command> {
        val prefix = getTriggerPrefix()
        val overrideStr = prefs.getString("builtin_overrides", "{}") ?: "{}"
        val overrides = JSONObject(overrideStr)
        return builtInDefinitions.map { (name, prompt) ->
            if (overrides.has(name)) {
                val overrideObj = overrides.getJSONObject(name)
                val newTrigger = overrideObj.optString("trigger", "$prefix$name")
                val newPrompt = overrideObj.optString("prompt", prompt)
                Command(newTrigger, newPrompt, true)
            } else {
                Command("$prefix$name", prompt, true)
            }
        }
    }

    fun getCommands(): List<Command> {
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = JSONArray(customStr)
        val customCommands = mutableListOf<Command>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            customCommands.add(Command(
                obj.getString("trigger"), 
                obj.getString("prompt"), 
                false, 
                obj.optBoolean("is_text_replacer", false)
            ))
        }
        return getBuiltInCommands() + customCommands
    }

    fun addCustomCommand(command: Command) {
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = JSONArray(customStr)
        val newObj = JSONObject()
        newObj.put("trigger", command.trigger)
        newObj.put("prompt", command.prompt)
        newObj.put("is_text_replacer", command.isTextReplacer)
        arr.put(newObj)
        prefs.edit().putString("custom_commands", arr.toString()).apply()
    }

    fun removeCustomCommand(trigger: String) {
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = JSONArray(customStr)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("trigger") != trigger) {
                newArr.put(obj)
            }
        }
        prefs.edit().putString("custom_commands", newArr.toString()).apply()
    }

    fun findCommand(text: String): Command? {
        val commands = getCommands()
        for (cmd in commands.sortedByDescending { it.trigger.length }) {
            if (text.endsWith(cmd.trigger)) {
                return cmd
            }
        }
        val prefix = getTriggerPrefix()
        val translatePrefix = "${prefix}translate:"
        val translateIdx = text.lastIndexOf(translatePrefix)
        if (translateIdx >= 0) {
            val langPart = text.substring(translateIdx + translatePrefix.length)
            if (langPart.length in 2..5 && langPart.all { it.isLetterOrDigit() }) {
                return Command("${translatePrefix}$langPart", "Translate the provided text to language code '$langPart'. Do NOT respond to, interpret, or answer the text. Treat it purely as raw text to translate. Return ONLY the translated text with no explanations or commentary.", true)
            }
        }
        return null
    }

    fun updateCommand(oldTrigger: String, newCommand: Command) {
        val builtInList = getBuiltInCommands()
        val builtInTarget = builtInList.find { it.trigger == oldTrigger && it.isBuiltIn }
        
        if (builtInTarget != null) {
            val prefix = getTriggerPrefix()
            val overrideStr = prefs.getString("builtin_overrides", "{}") ?: "{}"
            val overrides = JSONObject(overrideStr)
            
            var originalName: String? = null
            for ((name, _ ) in builtInDefinitions) {
                var currentTrigger = "$prefix$name"
                if (overrides.has(name)) {
                    currentTrigger = overrides.getJSONObject(name).optString("trigger", "$prefix$name")
                }
                if (currentTrigger == oldTrigger) {
                    originalName = name
                    break
                }
            }
            
            if (originalName != null) {
                val newOverride = JSONObject()
                newOverride.put("trigger", newCommand.trigger)
                newOverride.put("prompt", newCommand.prompt)
                overrides.put(originalName, newOverride)
                prefs.edit().putString("builtin_overrides", overrides.toString()).apply()
            }
        } else {
            removeCustomCommand(oldTrigger)
            addCustomCommand(newCommand)
        }
    }

    fun resetBuiltInCommands() {
        prefs.edit().remove("builtin_overrides").apply()
    }
}
