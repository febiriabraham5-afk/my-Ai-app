package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AnswerMode
import com.example.ai.ChatHistoryTurn
import com.example.ai.LuminaryAiService
import com.example.data.LuminaryDatabase
import com.example.data.LuminaryRepository
import com.example.data.MessageEntity
import com.example.data.ThreadEntity
import com.example.ui.theme.BackgroundStyle
import com.example.ui.theme.ThemePreference
import com.google.android.gms.location.LocationServices
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private const val TAG = "LuminaryViewModel"

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Loading : GenerationState
    data class Streaming(
        val text: String,
        val modeLabel: String,
        val thoughts: String = "",
        val thinkingSeconds: Int = 0
    ) : GenerationState
    data class Error(
        val message: String,
        val retryQuestion: String,
        val retryImagePath: String?
    ) : GenerationState
}

data class AttachedImage(
    val bitmap: Bitmap,
    val filePath: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class LuminaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LuminaryRepository by lazy {
        val db = LuminaryDatabase.getInstance(application)
        LuminaryRepository(application, db)
    }

    private val aiService = LuminaryAiService()

    init {
        runCatching {
            FirebaseAppCheck.getInstance().getAppCheckToken(false)
                .addOnFailureListener { e ->
                    Log.w(TAG, "App Check token pre-warm failed: ${e.message}")
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            aiService.warmUp()
        }
    }

    private val _activeThreadId = MutableStateFlow<Long?>(null)
    val activeThreadId: StateFlow<Long?> = _activeThreadId.asStateFlow()

    val threads: StateFlow<List<ThreadEntity>> = repository.threads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = _activeThreadId
        .flatMapLatest { threadId ->
            if (threadId == null) flowOf(emptyList()) else repository.observeMessages(threadId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarkedMessages: StateFlow<List<MessageEntity>> = repository.bookmarkedMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedMode: StateFlow<AnswerMode> = repository.preferredMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnswerMode.BALANCED)

    val themePreference: StateFlow<ThemePreference> = repository.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference.SYSTEM)

    val backgroundStyle: StateFlow<BackgroundStyle> = repository.backgroundStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackgroundStyle.AMBIENT)

    private val _useGoogleSearch = MutableStateFlow(true)
    val useGoogleSearch: StateFlow<Boolean> = _useGoogleSearch.asStateFlow()

    private val _useGoogleMaps = MutableStateFlow(true)
    val useGoogleMaps: StateFlow<Boolean> = _useGoogleMaps.asStateFlow()

    private val _locationBias = MutableStateFlow<Pair<Double, Double>?>(null)
    val locationBias: StateFlow<Pair<Double, Double>?> = _locationBias.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _followUps = MutableStateFlow<List<String>>(emptyList())
    val followUps: StateFlow<List<String>> = _followUps.asStateFlow()

    private val _attachedImage = MutableStateFlow<AttachedImage?>(null)
    val attachedImage: StateFlow<AttachedImage?> = _attachedImage.asStateFlow()

    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError: StateFlow<String?> = _attachmentError.asStateFlow()

    private val _ttsSpeakingMessageId = MutableStateFlow<Long?>(null)
    val ttsSpeakingMessageId: StateFlow<Long?> = _ttsSpeakingMessageId.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var followUpJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeak: Pair<Long, String>? = null

    fun toggleGoogleSearch() {
        _useGoogleSearch.value = !_useGoogleSearch.value
    }

    fun toggleGoogleMaps() {
        _useGoogleMaps.value = !_useGoogleMaps.value
    }

    @SuppressLint("MissingPermission")
    fun refreshDeviceLocation() {
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    _locationBias.value = loc.latitude to loc.longitude
                }
            }
        }
    }

    fun clearLocationBias() {
        _locationBias.value = null
    }

    private fun ensureTtsInitialized() {
        if (tts != null) return
        tts = TextToSpeech(getApplication()) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        _ttsSpeakingMessageId.value = null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _ttsSpeakingMessageId.value = null
                    }
                })
                pendingSpeak?.let { (id, text) ->
                    pendingSpeak = null
                    speakInternal(id, text)
                }
            }
        }
    }

    fun toggleSpeak(messageId: Long, rawText: String) {
        if (_ttsSpeakingMessageId.value == messageId) {
            stopSpeaking()
            return
        }
        val cleanText = stripMarkdownForSpeech(rawText)
        if (cleanText.isBlank()) return
        ensureTtsInitialized()
        if (ttsReady) {
            speakInternal(messageId, cleanText)
        } else {
            pendingSpeak = messageId to cleanText
            _ttsSpeakingMessageId.value = messageId
        }
    }

    private fun speakInternal(messageId: Long, cleanText: String) {
        _ttsSpeakingMessageId.value = messageId
        tts?.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "luminary_${UUID.randomUUID()}"
        )
    }

    fun stopSpeaking() {
        tts?.stop()
        pendingSpeak = null
        _ttsSpeakingMessageId.value = null
    }

    fun selectMode(mode: AnswerMode) {
        viewModelScope.launch {
            repository.savePreferredMode(mode)
        }
    }

    fun cycleThemePreference() {
        val next = when (themePreference.value) {
            ThemePreference.SYSTEM -> ThemePreference.DARK
            ThemePreference.DARK -> ThemePreference.LIGHT
            ThemePreference.LIGHT -> ThemePreference.SYSTEM
        }
        viewModelScope.launch {
            repository.saveThemePreference(next)
        }
    }

    fun selectBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch {
            repository.saveBackgroundStyle(style)
        }
    }

    fun attachImageFromUri(uri: Uri) {
        viewModelScope.launch {
            _attachmentError.value = null
            val result = repository.decodeAndPersistAttachment(uri)
            if (result != null) {
                _attachedImage.value = AttachedImage(result.first, result.second)
            } else {
                _attachmentError.value = getApplication<Application>().getString(R.string.error_image_load)
            }
        }
    }

    fun clearAttachment() {
        _attachedImage.value = null
        _attachmentError.value = null
    }

    fun dismissAttachmentError() {
        _attachmentError.value = null
    }

    fun startNewQuestion() {
        activeGenerationJob?.cancel()
        followUpJob?.cancel()
        stopSpeaking()
        _activeThreadId.value = null
        _generationState.value = GenerationState.Idle
        _followUps.value = emptyList()
        _attachedImage.value = null
        _attachmentError.value = null
    }

    fun openThread(threadId: Long) {
        if (_activeThreadId.value == threadId) return
        activeGenerationJob?.cancel()
        followUpJob?.cancel()
        stopSpeaking()
        _activeThreadId.value = threadId
        _generationState.value = GenerationState.Idle
        _followUps.value = emptyList()
        _attachedImage.value = null
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            if (_activeThreadId.value == threadId) {
                startNewQuestion()
            }
            repository.deleteThread(threadId)
        }
    }

    fun toggleBookmark(message: MessageEntity) {
        viewModelScope.launch {
            repository.setBookmarked(message.id, !message.isBookmarked)
        }
    }

    fun askQuestion(rawQuestion: String, overrideMode: AnswerMode? = null) {
        val attachment = _attachedImage.value
        val trimmed = rawQuestion.trim()
        if (trimmed.isBlank() && attachment == null) return

        SoundFeedback.playSendChime(getApplication())

        val effectiveQuestion = trimmed.ifBlank { "What is in this image?" }
        val mode = overrideMode ?: selectedMode.value
        if (overrideMode != null) {
            selectMode(overrideMode)
        }

        _attachedImage.value = null
        _attachmentError.value = null
        submitPrompt(
            question = effectiveQuestion,
            imageBitmap = attachment?.bitmap,
            imagePath = attachment?.filePath,
            mode = mode,
            insertUserMessage = true
        )
    }

    fun retryLastError() {
        val err = _generationState.value as? GenerationState.Error ?: return
        viewModelScope.launch {
            val bitmap = repository.loadBitmapFromPath(err.retryImagePath)
            submitPrompt(
                question = err.retryQuestion,
                imageBitmap = bitmap,
                imagePath = err.retryImagePath,
                mode = selectedMode.value,
                insertUserMessage = false
            )
        }
    }

    fun regenerateLastAnswer() {
        val currentMessages = messages.value
        val lastUserMsg = currentMessages.lastOrNull { it.role == "user" } ?: return
        val lastModelMsg = currentMessages.lastOrNull()
        viewModelScope.launch {
            if (lastModelMsg != null && lastModelMsg.role == "model") {
                repository.deleteMessage(lastModelMsg.id)
            }
            val bitmap = repository.loadBitmapFromPath(lastUserMsg.imagePath)
            submitPrompt(
                question = lastUserMsg.text,
                imageBitmap = bitmap,
                imagePath = lastUserMsg.imagePath,
                mode = selectedMode.value,
                insertUserMessage = false
            )
        }
    }

    fun stopGenerating() {
        val currentStreaming = _generationState.value as? GenerationState.Streaming
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        val threadId = _activeThreadId.value
        if (currentStreaming != null && currentStreaming.text.isNotBlank() && threadId != null) {
            viewModelScope.launch {
                repository.insertMessage(
                    threadId = threadId,
                    role = "model",
                    text = currentStreaming.text,
                    modeLabel = currentStreaming.modeLabel,
                    thoughts = currentStreaming.thoughts,
                    thinkingSeconds = currentStreaming.thinkingSeconds
                )
            }
        }
        _generationState.value = GenerationState.Idle
    }

    private fun submitPrompt(
        question: String,
        imageBitmap: Bitmap?,
        imagePath: String?,
        mode: AnswerMode,
        insertUserMessage: Boolean
    ) {
        activeGenerationJob?.cancel()
        followUpJob?.cancel()
        stopSpeaking()
        _followUps.value = emptyList()

        activeGenerationJob = viewModelScope.launch {
            _generationState.value = GenerationState.Loading
            var resolvedThreadId = _activeThreadId.value

            try {
                if (resolvedThreadId == null) {
                    resolvedThreadId = repository.createThread(
                        title = question.take(72),
                        mode = mode
                    )
                    _activeThreadId.value = resolvedThreadId
                } else {
                    repository.touchThread(resolvedThreadId, mode)
                }

                val priorMessages = repository.getMessagesOnce(resolvedThreadId)
                val historyTurns = priorMessages
                    .let { list ->
                        if (!insertUserMessage && list.lastOrNull()?.role == "user") {
                            list.dropLast(1)
                        } else {
                            list
                        }
                    }
                    .takeLast(8)
                    .map { msg ->
                        ChatHistoryTurn(role = msg.role, text = msg.text, image = null)
                    }

                if (insertUserMessage) {
                    repository.insertMessage(
                        threadId = resolvedThreadId,
                        role = "user",
                        text = question,
                        imagePath = imagePath
                    )
                }

                var finalAnswer = ""
                var finalThoughts = ""
                var finalThinkingSec = 0
                aiService.askStreaming(
                    question = question,
                    attachedImage = imageBitmap,
                    history = historyTurns,
                    mode = mode,
                    useGoogleSearch = _useGoogleSearch.value,
                    useGoogleMaps = _useGoogleMaps.value,
                    locationLatLng = _locationBias.value
                ).collect { update ->
                    finalAnswer = update.answerText
                    finalThoughts = update.thoughtSummary
                    finalThinkingSec = update.thinkingSeconds
                    _generationState.value = GenerationState.Streaming(
                        text = update.answerText,
                        modeLabel = mode.label,
                        thoughts = update.thoughtSummary,
                        thinkingSeconds = update.thinkingSeconds
                    )
                }

                if (finalAnswer.isNotBlank()) {
                    repository.insertMessage(
                        threadId = resolvedThreadId,
                        role = "model",
                        text = finalAnswer,
                        modeLabel = mode.label,
                        thoughts = finalThoughts,
                        thinkingSeconds = finalThinkingSec
                    )
                    _generationState.value = GenerationState.Idle
                    fetchFollowUps(question, finalAnswer)
                } else {
                    _generationState.value = GenerationState.Error(
                        message = "The AI returned an empty response. Please try again.",
                        retryQuestion = question,
                        retryImagePath = imagePath
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _generationState.value = GenerationState.Error(
                    message = "Request timed out after 60 seconds. Check your connection and try again.",
                    retryQuestion = question,
                    retryImagePath = imagePath
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                _generationState.value = GenerationState.Error(
                    message = e.localizedMessage ?: "Unable to get an answer right now. Please try again.",
                    retryQuestion = question,
                    retryImagePath = imagePath
                )
            }
        }
    }

    private fun fetchFollowUps(question: String, answer: String) {
        followUpJob = viewModelScope.launch {
            val suggestions = aiService.generateFollowUps(question, answer)
            if (suggestions.isNotEmpty()) {
                _followUps.value = suggestions
            }
        }
    }

    private fun stripMarkdownForSpeech(markdown: String): String {
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), " Code snippet omitted. ")
            .replace(Regex("\\[([^\\]]+)]\\(https?://[^)]+\\)"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
