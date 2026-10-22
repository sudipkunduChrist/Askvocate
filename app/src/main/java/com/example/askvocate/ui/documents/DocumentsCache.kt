package com.example.askvocate.ui.documents

import android.content.Context
import com.example.askvocate.util.SessionManager
import org.json.JSONArray
import java.io.File

/**
 * Last successful documents response plus previously viewed images, cached
 * per signed-in user in app-private storage. No full PAN/Aadhaar number is stored.
 */
class DocumentsCache(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("my_documents_cache", Context.MODE_PRIVATE)
    private val userId = SessionManager.getUserId(appContext)
    private val imageDir = File(appContext.filesDir, "documents_cache/$userId")

    fun load(): List<MyDocument>? {
        if (userId.isBlank()) return null
        val raw = prefs.getString(key(), null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { MyDocument.from(array.getJSONObject(it)) }
        }.getOrNull()
    }

    fun save(docs: List<MyDocument>) {
        if (userId.isBlank()) return
        val array = JSONArray()
        docs.forEach { array.put(it.toJson()) }
        prefs.edit().putString(key(), array.toString()).apply()
    }

    fun image(type: String, side: String): ByteArray? {
        if (userId.isBlank()) return null
        val file = imageFile(type, side)
        return if (file.isFile && file.length() > 0) runCatching { file.readBytes() }.getOrNull() else null
    }

    fun saveImage(type: String, side: String, bytes: ByteArray) {
        if (userId.isBlank() || bytes.isEmpty()) return
        runCatching {
            imageDir.mkdirs()
            imageFile(type, side).writeBytes(bytes)
        }
    }

    fun clear() {
        if (userId.isNotBlank()) prefs.edit().remove(key()).apply()
        runCatching { imageDir.deleteRecursively() }
    }

    private fun key() = "documents_$userId"

    private fun imageFile(type: String, side: String) =
        File(imageDir, "${type.lowercase()}_${side.lowercase()}.bin")
}
