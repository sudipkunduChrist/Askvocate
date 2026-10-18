package com.example.askvocate.ui.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private fun JSONObject.optionalText(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim()
        .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

data class MyDocument(
    val type: String, val title: String, val status: String, val note: String?,
    val maskedNumber: String?, val details: Map<String, String>, val submittedAt: String?,
    val verifiedAt: String?, val media: List<String>, val requiresBack: Boolean,
    val uploadOnly: Boolean, val liveCapture: Boolean, val aadhaarNumber: String? = null,
    val qrDetails: Map<String, String> = emptyMap(), val qrPhotoUrl: String? = null
) {
    companion object {
        fun from(json: JSONObject): MyDocument {
            val fields = json.optJSONObject("details")
            val details = fields?.keys()?.asSequence()?.mapNotNull { key ->
                fields.optionalText(key)?.let { key to it }
            }?.toMap() ?: emptyMap()
            val qrFields = json.optJSONObject("qrDetails")
            val qrDetails = qrFields?.keys()?.asSequence()?.mapNotNull { key ->
                qrFields.optionalText(key)?.let { key to it }
            }?.toMap() ?: emptyMap()
            val mediaArray = json.optJSONArray("media") ?: JSONArray()
            return MyDocument(json.getString("type"), json.getString("title"), json.getString("status"),
                json.optionalText("note"), json.optionalText("maskedNumber"), details,
                json.optionalText("submittedAt"), json.optionalText("verifiedAt"),
                (0 until mediaArray.length()).map { mediaArray.getString(it) },
                json.optBoolean("requiresBack"), json.optBoolean("uploadOnly"), json.optBoolean("liveCapture"),
                json.optionalText("aadhaarNumber"), qrDetails, json.optionalText("qrPhotoUrl"))
        }
    }
}

class MyDocumentsApi(private val context: Context) {
    private val compressedImageLimit = 8L * 1024 * 1024
    // Match the backend receive ceiling; oversized photos are also compressed server-side.
    private val selectedImageLimit = 30L * 1024 * 1024

    private fun connection(path: String, method: String = "GET"): HttpURLConnection {
        val token = SessionManager.getAppToken(context)
        if (token.isBlank()) throw IllegalStateException("Please sign in again to access your documents.")
        return (URL("${ApiConfig.BASE_URL}/api/my-documents$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun response(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching {
                JSONObject(body).let { it.optionalText("message") ?: it.optionalText("detail") }
            }.getOrNull() ?: "Request failed ($code). Please try again."
            throw IllegalStateException(message)
        }
        if (body.trimStart().startsWith("{")) {
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json?.optBoolean("success", true) == false) {
                throw IllegalStateException(
                    json.optionalText("error") ?: json.optionalText("message")
                        ?: "The document request could not be completed."
                )
            }
        }
        return body
    }

    suspend fun list(): List<MyDocument> = withContext(Dispatchers.IO) {
        val conn = connection("")
        try {
            val array = JSONArray(response(conn))
            (0 until array.length()).map { MyDocument.from(array.getJSONObject(it)) }
        } finally { conn.disconnect() }
    }

    suspend fun detail(type: String): MyDocument = withContext(Dispatchers.IO) {
        val conn = connection("/$type")
        try { MyDocument.from(JSONObject(response(conn))) } finally { conn.disconnect() }
    }

    suspend fun revealAadhaarNumber(): String = withContext(Dispatchers.IO) {
        val conn = connection("/AADHAAR")
        try {
            JSONObject(response(conn)).optionalText("aadhaarNumber")
                ?.takeIf { it.length == 12 && it.all(Char::isDigit) }
                ?: throw IllegalStateException("The verified Aadhaar number is unavailable.")
        } finally { conn.disconnect() }
    }

    suspend fun media(type: String, side: String): ByteArray = withContext(Dispatchers.IO) {
        val conn = connection("/$type/media/$side")
        try {
            // This endpoint returns image bytes; requesting application/json causes Spring MVC
            // to reject the request with 406 before the controller can serve the image.
            conn.setRequestProperty("Accept", "image/*")
            val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes() } ?: byteArrayOf()
            val contentType = conn.contentType.orEmpty()
            if (conn.responseCode !in 200..299 || !contentType.startsWith("image/")) {
                val message = runCatching {
                    JSONObject(body.toString(Charsets.UTF_8)).let {
                        it.optionalText("error") ?: it.optionalText("message") ?: it.optionalText("detail")
                    }
                }.getOrNull() ?: "Could not open this image. Please try again."
                throw IllegalStateException(message)
            }
            body
        } finally { conn.disconnect() }
    }

    suspend fun upload(type: String, front: Uri, back: Uri?): MyDocument = withContext(Dispatchers.IO) {
        val frontFile = compressImage(front)
        val backFile = try { back?.let(::compressImage) } catch (error: Exception) {
            frontFile.delete()
            throw error
        }
        val boundary = "askvocate-${UUID.randomUUID()}"
        val conn = try {
            connection("/$type", "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(64 * 1024)
            }
        } catch (error: Exception) {
            frontFile.delete(); backFile?.delete()
            throw error
        }
        try {
            conn.outputStream.buffered().use { out ->
                fun part(name: String, file: File) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$name.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
                    file.inputStream().use { input -> input.copyTo(out) }
                    out.write("\r\n".toByteArray())
                }
                part("front", frontFile)
                if (backFile != null) part("back", backFile)
                out.write("--$boundary--\r\n".toByteArray())
            }
            MyDocument.from(JSONObject(response(conn)))
        } finally {
            conn.disconnect()
            frontFile.delete()
            backFile?.delete()
        }
    }

    /** Normalize camera and gallery images into OCR-friendly JPEGs under 8 MiB each. */
    private fun compressImage(uri: Uri): File {
        val resolver = context.contentResolver
        val source = File.createTempFile("askvocate_source_", ".img", context.cacheDir)
        var keepSourceForUpload = false
        try {
            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open the selected image. Please choose it again.")
            input.use { stream ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > selectedImageLimit) {
                            throw IllegalStateException("This image is over 30 MB. Choose a smaller JPG, PNG or WebP photo.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            source.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
                throw IllegalStateException("Choose a JPG, PNG or WebP image up to 30 MB.")
            }
            val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxDimension / sample > 3600) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = source.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalStateException("Could not decode this image. Choose another JPG, PNG or WebP photo.")
            try {
                val rotation = runCatching {
                    source.inputStream().use { stream ->
                        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270
                            else -> 0
                        }
                    }
                }.getOrDefault(0)
                if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated !== bitmap) bitmap.recycle()
                    bitmap = rotated
                }
                val target = File.createTempFile("askvocate_doc_", ".jpg", context.cacheDir)
                try {
                    while (true) {
                        for (quality in listOf(90, 85, 80, 75, 70)) {
                            FileOutputStream(target, false).use { stream ->
                                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                                    throw IllegalStateException("Could not compress this image.")
                                }
                            }
                            if (target.length() <= compressedImageLimit) return target
                        }
                        if (maxOf(bitmap.width, bitmap.height) <= 1400) break
                        val scaled = Bitmap.createScaledBitmap(bitmap,
                            (bitmap.width * .82f).toInt().coerceAtLeast(1),
                            (bitmap.height * .82f).toInt().coerceAtLeast(1), true)
                        if (scaled !== bitmap) bitmap.recycle()
                        bitmap = scaled
                    }
                    // Let the backend perform the final compression if the device cannot get it
                    // below the client-side target without further degrading the document.
                    target.delete()
                    keepSourceForUpload = true
                    return source
                } catch (error: Exception) {
                    target.delete()
                    throw error
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } finally {
            if (!keepSourceForUpload) source.delete()
        }
    }
}
