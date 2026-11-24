package com.example.ai

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

data class VideoGenerationResult(
    val videoFilePath: String,
    val modelUsed: String,
    val summary: String
)

class VideoStudioService(private val context: Context) {

    private val veoModels = listOf(
        "veo-3.1-generate-preview",
        "veo-3.0-generate-preview",
        "veo-2.0-generate-001"
    )

    suspend fun generateVideo(
        prompt: String,
        sourceImage: Bitmap? = null,
        aspectRatio: String = "16:9",
        onProgress: (String) -> Unit
    ): VideoGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = runCatching { FirebaseApp.getInstance().options.apiKey }.getOrNull().orEmpty()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Firebase project API key is unavailable. Check google-services.json.")
        }
        val certSha1 = getSigningCertSha1()

        val imagePayload = sourceImage?.let { bmp ->
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            JSONObject().apply {
                put("bytesBase64Encoded", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
                put("mimeType", "image/jpeg")
            }
        }

        val instanceObj = JSONObject().apply {
            put("prompt", prompt.ifBlank { "Smooth cinematic camera motion bringing this scene to life" })
            if (imagePayload != null) {
                put("image", imagePayload)
            }
        }
        val requestBody = JSONObject().apply {
            put("instances", JSONArray().put(instanceObj))
            put("parameters", JSONObject().apply {
                put("aspectRatio", aspectRatio)
                put("personGeneration", "allow_adult")
            })
        }

        var operationName: String? = null
        var usedModel = veoModels.first()
        var lastError: String? = null

        for (modelName in veoModels) {
            onProgress("Submitting to $modelName...")
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:predictLongRunning"
            val (code, body) = postJson(endpoint, requestBody.toString(), apiKey, certSha1)
            if (code in 200..299) {
                val json = JSONObject(body)
                val op = json.optString("name")
                if (op.isNotBlank()) {
                    operationName = op
                    usedModel = modelName
                    break
                }
            }
            lastError = extractApiError(body, code)
            if (code == 401 || code == 403) break
        }

        val opName = operationName ?: throw IllegalStateException(
            lastError ?: "Veo video generation could not be started on this project."
        )

        var videoUri: String? = null
        val maxPolls = 45
        for (attempt in 1..maxPolls) {
            delay(4_000L)
            onProgress("Rendering video with $usedModel (${attempt * 4}s)...")
            val opUrl = "https://generativelanguage.googleapis.com/v1beta/$opName"
            val (code, body) = getJson(opUrl, apiKey, certSha1)
            if (code !in 200..299) {
                throw IllegalStateException(extractApiError(body, code))
            }
            val opJson = JSONObject(body)
            if (opJson.has("error")) {
                val errMsg = opJson.getJSONObject("error").optString("message", "Video generation failed.")
                throw IllegalStateException(errMsg)
            }
            if (opJson.optBoolean("done", false)) {
                val resp = opJson.optJSONObject("response")
                val genResp = resp?.optJSONObject("generateVideoResponse")
                val samples = genResp?.optJSONArray("generatedSamples")
                val firstSample = samples?.optJSONObject(0)
                val videoObj = firstSample?.optJSONObject("video")
                videoUri = videoObj?.optString("uri")
                if (videoUri.isNullOrBlank()) {
                    val reasons = genResp?.optJSONArray("raiMediaFilteredReasons")
                    val reasonMsg = reasons?.optString(0).orEmpty()
                    throw IllegalStateException(
                        if (reasonMsg.isNotBlank()) "Video filtered: $reasonMsg"
                        else "Veo finished without returning a video URL."
                    )
                }
                break
            }
        }

        val resolvedUri = videoUri ?: throw IllegalStateException("Video rendering timed out while waiting for Veo.")
        onProgress("Downloading rendered MP4...")
        val outDir = File(context.filesDir, "videos").apply { mkdirs() }
        val outFile = File(outDir, "veo_${UUID.randomUUID()}.mp4")
        downloadFile(resolvedUri, outFile, apiKey, certSha1)

        VideoGenerationResult(
            videoFilePath = outFile.absolutePath,
            modelUsed = usedModel,
            summary = if (sourceImage != null) "Animated image with $usedModel" else "Generated video with $usedModel"
        )
    }

    private fun postJson(
        urlStr: String,
        jsonBody: String,
        apiKey: String,
        certSha1: String?
    ): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("X-Android-Package", context.packageName)
            if (!certSha1.isNullOrBlank()) setRequestProperty("X-Android-Cert", certSha1)
        }
        return try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun getJson(urlStr: String, apiKey: String, certSha1: String?): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("X-Android-Package", context.packageName)
            if (!certSha1.isNullOrBlank()) setRequestProperty("X-Android-Cert", certSha1)
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFile(urlStr: String, dest: File, apiKey: String, certSha1: String?) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 25_000
            readTimeout = 60_000
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("X-Android-Package", context.packageName)
            if (!certSha1.isNullOrBlank()) setRequestProperty("X-Android-Cert", certSha1)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(extractApiError(err, code))
            }
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun extractApiError(body: String, code: Int): String {
        val parsed = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return if (!parsed.isNullOrBlank()) {
            "Veo API ($code): $parsed"
        } else {
            "Veo API error ($code). Ensure Veo / Gemini video generation is enabled for this project."
        }
    }

    private fun getSigningCertSha1(): String? {
        return runCatching {
            val pm = context.packageManager
            val sigBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return null
            val md = MessageDigest.getInstance("SHA-1")
            md.digest(sigBytes).joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
