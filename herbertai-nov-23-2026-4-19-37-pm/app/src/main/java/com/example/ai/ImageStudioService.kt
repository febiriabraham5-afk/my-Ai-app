package com.example.ai

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class ImageAspectOption(val label: String, val sdkValue: AspectRatio) {
    SQUARE("1:1", AspectRatio.SQUARE_1x1),
    LANDSCAPE("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT("9:16", AspectRatio.PORTRAIT_9x16),
    FOUR_THREE("4:3", AspectRatio.LANDSCAPE_4x3),
    THREE_FOUR("3:4", AspectRatio.PORTRAIT_3x4)
}

data class ImageGenerationResult(
    val bitmap: Bitmap,
    val caption: String,
    val modelUsed: String
)

class ImageStudioService {

    companion object {
        const val MODEL_DEFAULT = "gemini-nano-banana-2.1"
        const val MODEL_PRO = "gemini-3-pro-image"
        private const val IMAGE_TIMEOUT_MS = 60_000L
    }

    suspend fun createOrEditImage(
        prompt: String,
        sourceImage: Bitmap? = null,
        aspectOption: ImageAspectOption = ImageAspectOption.SQUARE,
        useProQuality: Boolean = false
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val primaryModel = if (useProQuality) MODEL_PRO else MODEL_DEFAULT
        val fallbackModel = if (useProQuality) MODEL_DEFAULT else MODEL_PRO
        val modelsToTry = listOf(primaryModel, fallbackModel)

        val requestContent = content {
            if (sourceImage != null) {
                image(sourceImage)
            }
            text(
                if (sourceImage != null) {
                    prompt.ifBlank { "Enhance lighting, clarity, and artistic polish while keeping the subject intact." }
                } else {
                    prompt
                }
            )
        }

        var lastError: Throwable? = null
        for (modelName in modelsToTry) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        responseModalities = listOf(ResponseModality.IMAGE, ResponseModality.TEXT)
                        imageConfig = ImageConfig.builder()
                            .setAspectRatio(aspectOption.sdkValue)
                            .build()
                    }
                )
                val response = withTimeout(IMAGE_TIMEOUT_MS) {
                    model.generateContent(requestContent)
                }
                val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
                val bitmap = parts.filterIsInstance<ImagePart>().firstOrNull()?.image
                val caption = response.text?.trim().orEmpty()
                if (bitmap != null) {
                    return@withContext ImageGenerationResult(
                        bitmap = bitmap,
                        caption = caption,
                        modelUsed = modelName
                    )
                }
                lastError = IllegalStateException("Model $modelName returned no image data.${if (caption.isNotBlank()) " Message: $caption" else ""}")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    lastError = IllegalStateException("Image generation timed out after 60 seconds. Please try again.")
                } else {
                    throw e
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Failed to generate image.")
    }
}
