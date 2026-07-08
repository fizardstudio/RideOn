package com.example.drivingassistantapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

sealed class ParsedCommand {
    data class Navigate(val location: String) : ParsedCommand()
    object ReadMessage : ParsedCommand()
    object CheckUnread : ParsedCommand()
    data class ReadFromContact(val contactName: String) : ParsedCommand()
    data class SendToContact(val contactName: String) : ParsedCommand()
    data class ReadHistory(val contactName: String) : ParsedCommand()
    object Cancel : ParsedCommand()
    data class Unknown(val text: String) : ParsedCommand()
}

class AssistantCommandParser {
    companion object {
        private const val TAG = "CommandParser"

        fun parse(text: String): ParsedCommand {
            val normalized = text.lowercase().trim()
            Log.d(TAG, "Parsing text for command: '$normalized'")

            // 1. Cancel commands
            if (normalized.contains("batal") || normalized.contains("cancel") || normalized.contains("tutup") || normalized.contains("diam")) {
                return ParsedCommand.Cancel
            }

            // 2. Read History from X prefixes
            val historyPrefixes = listOf(
                "bacakan riwayat pesan dari ", "bacakan riwayat wa dari ", "bacakan riwayat dari ",
                "baca riwayat pesan dari ", "baca riwayat wa dari ", "baca riwayat dari ",
                "riwayat pesan dari ", "riwayat wa dari ", "riwayat dari ",
                "bacakan riwayat pesan ", "bacakan riwayat wa ", "bacakan riwayat ",
                "riwayat pesan ", "riwayat wa "
            )
            for (prefix in historyPrefixes) {
                if (normalized.startsWith(prefix)) {
                    val name = normalized.substring(prefix.length).trim()
                    if (name.isNotEmpty() && name.split(" ").size <= 3) {
                        return ParsedCommand.ReadHistory(name)
                    }
                }
            }

            // 3. Send Message to X prefixes
            val sendPrefixes = listOf(
                "kirim wa ke ", "kirim pesan ke ", "wa ke ", "kirim wa ", "kirim pesan ", "wa "
            )
            for (prefix in sendPrefixes) {
                if (normalized.startsWith(prefix)) {
                    val name = normalized.substring(prefix.length).trim()
                    if (name.isNotEmpty() && name.split(" ").size <= 3) {
                        return ParsedCommand.SendToContact(name)
                    }
                }
            }

            // 3. Read Message from X prefixes
            val readPrefixes = listOf(
                "bacakan pesan dari ", "bacakan wa dari ", "cek wa dari ", "baca pesan dari ",
                "baca wa dari ", "pesan dari ", "wa dari "
            )
            for (prefix in readPrefixes) {
                if (normalized.startsWith(prefix)) {
                    val name = normalized.substring(prefix.length).trim()
                    if (name.isNotEmpty() && name.split(" ").size <= 3) {
                        return ParsedCommand.ReadFromContact(name)
                    }
                }
            }

            // 4. Check all unread messages
            if (normalized.contains("cek pesan") || normalized.contains("cek wa") || 
                normalized.contains("pesan belum terbaca") || normalized.contains("ada pesan") || 
                normalized.contains("ada wa") || normalized.contains("baca pesan belum terbalas")) {
                return ParsedCommand.CheckUnread
            }

            // 5. Repeat last message
            if (normalized.contains("baca pesan") || normalized.contains("baca wa") || normalized.contains("baca lagi") || normalized.contains("baca ulang")) {
                return ParsedCommand.ReadMessage
            }

            // 6. Navigation commands
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
