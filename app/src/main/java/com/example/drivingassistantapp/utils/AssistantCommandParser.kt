package com.example.drivingassistantapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed class ParsedCommand {
    data class Navigate(val location: String) : ParsedCommand()
    object ReadMessage : ParsedCommand()
    object CheckUnread : ParsedCommand()
    object Cancel : ParsedCommand()
    data class Unknown(val text: String) : ParsedCommand()
}

class AssistantCommandParser {
    companion object {
        private const val TAG = "CommandParser"

        fun parse(text: String): ParsedCommand {
            val normalized = text.lowercase().trim()
            Log.d(TAG, "Parsing text for command: '$normalized'")

            if (normalized.contains("batal") || normalized.contains("cancel") || normalized.contains("tutup") || normalized.contains("diam")) {
                return ParsedCommand.Cancel
            }

            if (normalized.contains("cek pesan") || normalized.contains("cek wa") || 
                normalized.contains("pesan belum terbaca") || normalized.contains("ada pesan") || 
                normalized.contains("ada wa") || normalized.contains("baca pesan belum terbalas")) {
                return ParsedCommand.CheckUnread
            }

            if (normalized.contains("baca pesan") || normalized.contains("baca wa") || normalized.contains("baca lagi") || normalized.contains("baca ulang")) {
                return ParsedCommand.ReadMessage
            }

            // List of navigation prefixes to clean up, ordered from longest to shortest
            val prefixes = listOf(
                "buka maps ke ", "navigasi ke ", "pergi ke ", "rute ke ", "arah ke ", "maps ke ",
                "buka maps ", "navigasi ", "pergi ", "maps ", "ke "
            )
            
            for (prefix in prefixes) {
                if (normalized.startsWith(prefix)) {
                    val destination = normalized.substring(prefix.length).trim()
                    if (destination.isNotEmpty()) {
                        return ParsedCommand.Navigate(destination)
                    }
                }
            }

            // Fallback: If it's a short query (1 to 4 words), treat it as a direct destination
            // e.g. "jakarta", "monas", "bandara", "spbu terdekat"
            val wordCount = normalized.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            if (wordCount in 1..4) {
                return ParsedCommand.Navigate(normalized)
            }

            return ParsedCommand.Unknown(text)
        }

        fun executeNavigation(context: Context, location: String): Boolean {
            return try {
                val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(location))
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(mapIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error launching Google Maps intent", e)
                false
            }
        }
    }
}
