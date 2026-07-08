package com.example.drivingassistantapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class ChatHistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ride_on_chat_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_HISTORY = "chat_history"
        
        private const val KEY_ID = "id"
        private const val KEY_SENDER = "sender"
        private const val KEY_MESSAGE = "message"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_IS_VOICE_NOTE = "is_voice_note"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = ("CREATE TABLE " + TABLE_HISTORY + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_SENDER + " TEXT,"
                + KEY_MESSAGE + " TEXT,"
                + KEY_TIMESTAMP + " INTEGER,"
                + KEY_IS_VOICE_NOTE + " INTEGER" + ")")
        db.execSQL(createTableQuery)
        Log.d("ChatHistoryDbHelper", "Table created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY)
        onCreate(db)
    }

    fun insertMessage(sender: String, message: String, isVoiceNote: Boolean) {
        try {
            val db = this.writableDatabase
            val values = ContentValues().apply {
                // Store lowercase sender for flexible querying
                put(KEY_SENDER, sender.lowercase().trim())
                put(KEY_MESSAGE, message)
                put(KEY_TIMESTAMP, System.currentTimeMillis())
                put(KEY_IS_VOICE_NOTE, if (isVoiceNote) 1 else 0)
            }
            db.insert(TABLE_HISTORY, null, values)
            Log.d("ChatHistoryDbHelper", "Saved message from $sender: \"$message\"")
        } catch (e: Exception) {
            Log.e("ChatHistoryDbHelper", "Error inserting message", e)
        }
    }

    fun getHistoryForSender(sender: String, limit: Int): List<WhatsAppMessage> {
        val list = mutableListOf<WhatsAppMessage>()
        val cleanSender = sender.lowercase().trim()
        val db = this.readableDatabase
        
        // Find messages matching sender (using LIKE query for robust match)
        val query = "SELECT * FROM $TABLE_HISTORY WHERE $KEY_SENDER LIKE ? ORDER BY $KEY_TIMESTAMP DESC LIMIT ?"
        val args = arrayOf("%$cleanSender%", limit.toString())

        try {
            db.rawQuery(query, args)?.use { cursor ->
                val senderIdx = cursor.getColumnIndex(KEY_SENDER)
                val msgIdx = cursor.getColumnIndex(KEY_MESSAGE)
                val vnIdx = cursor.getColumnIndex(KEY_IS_VOICE_NOTE)

                if (senderIdx != -1 && msgIdx != -1 && vnIdx != -1) {
                    while (cursor.moveToNext()) {
                        val dbSender = cursor.getString(senderIdx)
                        val dbMsg = cursor.getString(msgIdx)
                        val isVN = cursor.getInt(vnIdx) == 1
                        list.add(WhatsAppMessage(sender = dbSender, text = dbMsg, isVoiceNote = isVN))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatHistoryDbHelper", "Error querying chat history", e)
        }
        
        // Return chronological order
        return list.reversed()
    }
}
