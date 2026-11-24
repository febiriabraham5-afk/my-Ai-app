package com.example.ai

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "VoiceLiveManager"

data class VoiceTranscriptLine(
    val id: Long = System.nanoTime(),
    val speaker: String, // "You" or "Luminary"
    val text: String
)

enum class LiveVoiceStatus {
    IDLE,
    CONNECTING,
    LIVE,
    ERROR
}

@OptIn(PublicPreviewAPI::class)
class VoiceLiveManager(private val scope: CoroutineScope) {

    private var activeSession: LiveSession? = null
    private var conversationJob: Job? = null

    private val liveModelCandidates = listOf(
        "gemini-2.5-flash-native-audio-preview-12-2025",
        "gemini-2.5-flash-native-audio-preview-09-2025",
        "gemini-live-2.5-flash-preview"
    )

    @SuppressLint("MissingPermission")
    fun startLiveConversation(
        voiceName: String,
        onStatusChanged: (LiveVoiceStatus, String?) -> Unit,
        onTranscriptUpdate: (userText: String?, modelText: String?) -> Unit
    ) {
        stopLiveConversation()
        onStatusChanged(LiveVoiceStatus.CONNECTING, "Connecting to Gemini Live voice...")

        conversationJob = scope.launch(Dispatchers.IO) {
            var session: LiveSession? = null
            var lastError: Throwable? = null

            for (modelName in liveModelCandidates) {
                try {
                    val liveModel = Firebase.ai.liveModel(
                        modelName = modelName,
                        generationConfig = liveGenerationConfig {
                            responseModality = ResponseModality.AUDIO
                            speechConfig = SpeechConfig(voice = Voice(voiceName))
                            inputAudioTranscription = AudioTranscriptionConfig()
                            outputAudioTranscription = AudioTranscriptionConfig()
                        },
                        systemInstruction = content {
                            text("You are Luminary in a real-time spoken voice conversation. Keep replies natural, warm, concise, and helpful.")
                        }
                    )
                    session = liveModel.connect()
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    lastError = t
                    Log.w(TAG, "Live connect failed for $modelName: ${t.message}")
                }
            }

            if (session == null) {
                onStatusChanged(
                    LiveVoiceStatus.ERROR,
                    lastError?.localizedMessage ?: "Unable to connect to Gemini Live Audio."
                )
                return@launch
            }

            activeSession = session
            onStatusChanged(LiveVoiceStatus.LIVE, null)

            try {
                val config = liveAudioConversationConfig {
                    enableInterruptions = true
                    transcriptHandler = { inputTranscription, outputTranscription ->
                        val userStr = inputTranscription?.text?.takeIf { it.isNotBlank() }
                        val modelStr = outputTranscription?.text?.takeIf { it.isNotBlank() }
                        if (userStr != null || modelStr != null) {
                            onTranscriptUpdate(userStr, modelStr)
                        }
                    }
                    goAwayHandler = {
                        onStatusChanged(LiveVoiceStatus.IDLE, "Live session ended by server.")
                    }
                }
                session.startAudioConversation(config)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Audio conversation failed", t)
                onStatusChanged(
                    LiveVoiceStatus.ERROR,
                    t.localizedMessage ?: "Live audio stream interrupted."
                )
            }
        }
    }

    suspend fun sendTextHintToLiveSession(text: String) {
        val s = activeSession ?: return
        runCatching { s.sendTextRealtime(text) }
    }

    fun stopLiveConversation() {
        val s = activeSession
        activeSession = null
        conversationJob?.cancel()
        conversationJob = null
        if (s != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { s.stopAudioConversation() }
                runCatching { s.close() }
            }
        }
    }
}
