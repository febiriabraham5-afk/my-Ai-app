package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AudioRecorderHelper
import com.example.ai.ImageAspectOption
import com.example.ai.ImageStudioService
import com.example.ai.LiveVoiceStatus
import com.example.ai.LuminaryAiService
import com.example.ai.VideoStudioService
import com.example.ai.VoiceLiveManager
import com.example.ai.VoiceTranscriptLine
import com.example.data.CreationEntity
import com.example.data.LuminaryDatabase
import com.example.data.LuminaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LuminaryRepository by lazy {
        LuminaryRepository(application, LuminaryDatabase.getInstance(application))
    }

    private val aiService = LuminaryAiService()
    private val imageService = ImageStudioService()
    private val videoService = VideoStudioService(application)
    private val voiceLiveManager = VoiceLiveManager(viewModelScope)
    private val audioRecorder = AudioRecorderHelper(application)

    val creations: StateFlow<List<CreationEntity>> = repository.creations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _imageState = MutableStateFlow(ImageStudioUiState())
    val imageState: StateFlow<ImageStudioUiState> = _imageState.asStateFlow()

    private val _videoState = MutableStateFlow(VideoStudioUiState())
    val videoState: StateFlow<VideoStudioUiState> = _videoState.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceStudioUiState())
    val voiceState: StateFlow<VoiceStudioUiState> = _voiceState.asStateFlow()

    private val _threeDState = MutableStateFlow(ThreeDStudioUiState())
    val threeDState: StateFlow<ThreeDStudioUiState> = _threeDState.asStateFlow()

    private val _sqlState = MutableStateFlow(SqlStudioUiState())
    val sqlState: StateFlow<SqlStudioUiState> = _sqlState.asStateFlow()

    private var pendingAudioData: Pair<ByteArray, String>? = null
    private var recordingTimerJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        refreshSqlTables()
        runSqlQuery(_sqlState.value.sqlQuery)
    }

    // ---- Image Studio ----

    fun updateImagePrompt(text: String) {
        _imageState.update { it.copy(prompt = text, error = null) }
    }

    fun selectImageAspect(option: ImageAspectOption) {
        _imageState.update { it.copy(aspectOption = option) }
    }

    fun toggleProImageQuality() {
        _imageState.update { it.copy(useProQuality = !it.useProQuality) }
    }

    fun attachSourceImageForEdit(uri: Uri) {
        viewModelScope.launch {
            val loaded = repository.decodeAndPersistAttachment(uri)
            if (loaded != null) {
                _imageState.update { it.copy(sourceImage = loaded.first, error = null) }
            } else {
                _imageState.update { it.copy(error = "Could not load selected photo.") }
            }
        }
    }

    fun useGeneratedImageAsEditSource() {
        val gen = _imageState.value.generatedImage ?: return
        _imageState.update { it.copy(sourceImage = gen, error = null) }
    }

    fun clearImageEditSource() {
        _imageState.update { it.copy(sourceImage = null) }
    }

    fun generateOrEditImage() {
        val st = _imageState.value
        val prompt = st.prompt.trim()
        if (prompt.isBlank() && st.sourceImage == null) {
            _imageState.update { it.copy(error = "Describe an image or attach a photo to edit.") }
            return
        }
        viewModelScope.launch {
            _imageState.update { it.copy(isGenerating = true, error = null) }
            try {
                val res = imageService.createOrEditImage(
                    prompt = prompt,
                    sourceImage = st.sourceImage,
                    aspectOption = st.aspectOption,
                    useProQuality = st.useProQuality
                )
                val savedPath = repository.persistGeneratedBitmap(res.bitmap, "img")
                repository.saveCreation(
                    type = "IMAGE",
                    title = (prompt.ifBlank { "Image Edit" }).take(60),
                    prompt = prompt,
                    content = res.caption,
                    filePath = savedPath,
                    metadata = "${res.modelUsed} | ${st.aspectOption.label}"
                )
                refreshSqlTables()
                _imageState.update {
                    it.copy(
                        isGenerating = false,
                        generatedImage = res.bitmap,
                        generatedPath = savedPath,
                        caption = res.caption,
                        modelUsed = res.modelUsed,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _imageState.update {
                    it.copy(isGenerating = false, error = t.localizedMessage ?: "Image generation failed.")
                }
            }
        }
    }

    fun sendGeneratedImageToVideoStudio(bitmap: Bitmap) {
        _videoState.update { it.copy(sourceImage = bitmap, error = null) }
    }

    // ---- Video Studio (Veo Text-to-Video & Image-to-Video) ----

    fun updateVideoPrompt(text: String) {
        _videoState.update { it.copy(prompt = text, error = null) }
    }

    fun selectVideoAspect(aspect: String) {
        _videoState.update { it.copy(aspectRatio = aspect) }
    }

    fun attachVideoSourceImage(uri: Uri) {
        viewModelScope.launch {
            val loaded = repository.decodeAndPersistAttachment(uri)
            if (loaded != null) {
                _videoState.update { it.copy(sourceImage = loaded.first, error = null) }
            } else {
                _videoState.update { it.copy(error = "Could not load selected image.") }
            }
        }
    }

    fun clearVideoSourceImage() {
        _videoState.update { it.copy(sourceImage = null) }
    }

    fun generateVideo() {
        val st = _videoState.value
        if (st.prompt.isBlank() && st.sourceImage == null) {
            _videoState.update { it.copy(error = "Enter a video prompt or attach an image to animate.") }
            return
        }
        viewModelScope.launch {
            _videoState.update {
                it.copy(isGenerating = true, progressMessage = "Starting Veo job...", error = null)
            }
            try {
                val res = videoService.generateVideo(
                    prompt = st.prompt.trim(),
                    sourceImage = st.sourceImage,
                    aspectRatio = st.aspectRatio,
                    onProgress = { msg ->
                        _videoState.update { it.copy(progressMessage = msg) }
                    }
                )
                repository.saveCreation(
                    type = "VIDEO",
                    title = (st.prompt.ifBlank { "Animated Image" }).take(60),
                    prompt = st.prompt,
                    content = res.summary,
                    filePath = res.videoFilePath,
                    metadata = "${res.modelUsed} | ${st.aspectRatio}"
                )
                refreshSqlTables()
                _videoState.update {
                    it.copy(
                        isGenerating = false,
                        progressMessage = "",
                        videoFilePath = res.videoFilePath,
                        modelUsed = res.modelUsed,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _videoState.update {
                    it.copy(
                        isGenerating = false,
                        progressMessage = "",
                        error = t.localizedMessage ?: "Video generation failed."
                    )
                }
            }
        }
    }

    // ---- Voice Conversations (Live + Push-to-Talk Turn Fallback) & Audio Transcription ----

    fun selectVoiceName(name: String) {
        _voiceState.update { it.copy(selectedVoice = name) }
    }

    fun toggleLiveVoiceConversation() {
        val status = _voiceState.value.liveStatus
        if (status == LiveVoiceStatus.LIVE || status == LiveVoiceStatus.CONNECTING) {
            stopLiveVoiceConversation()
        } else {
            startLiveVoiceConversation()
        }
    }

    fun startLiveVoiceConversation() {
        val voiceName = _voiceState.value.selectedVoice
        voiceLiveManager.startLiveConversation(
            voiceName = voiceName,
            onStatusChanged = { newStatus, message ->
                _voiceState.update {
                    it.copy(liveStatus = newStatus, liveStatusMessage = message)
                }
            },
            onTranscriptUpdate = { userText, modelText ->
                _voiceState.update { cur ->
                    val nextList = cur.liveTranscripts.toMutableList()
                    if (!userText.isNullOrBlank()) {
                        appendOrMergeTranscript(nextList, "You", userText)
                    }
                    if (!modelText.isNullOrBlank()) {
                        appendOrMergeTranscript(nextList, "Luminary", modelText)
                    }
                    cur.copy(liveTranscripts = nextList)
                }
            }
        )
    }

    fun stopLiveVoiceConversation() {
        voiceLiveManager.stopLiveConversation()
        val lines = _voiceState.value.liveTranscripts
        if (lines.isNotEmpty()) {
            viewModelScope.launch {
                val transcriptDump = lines.joinToString("\n") { "${it.speaker}: ${it.text}" }
                repository.saveCreation(
                    type = "VOICE",
                    title = "Live Voice Conversation",
                    prompt = "Gemini Live (${_voiceState.value.selectedVoice})",
                    content = transcriptDump
                )
                refreshSqlTables()
            }
        }
        _voiceState.update {
            it.copy(liveStatus = LiveVoiceStatus.IDLE, liveStatusMessage = null)
        }
    }

    private fun appendOrMergeTranscript(
        list: MutableList<VoiceTranscriptLine>,
        speaker: String,
        delta: String
    ) {
        val last = list.lastOrNull()
        if (last != null && last.speaker == speaker) {
            list[list.lastIndex] = last.copy(text = (last.text + " " + delta).trim())
        } else {
            list.add(VoiceTranscriptLine(speaker = speaker, text = delta.trim()))
        }
    }

    fun startPushToTalkTurn() {
        runCatching {
            audioRecorder.startRecording()
            _voiceState.update { it.copy(isPushToTalkRecording = true, liveStatusMessage = "Listening... tap again to send") }
        }.onFailure { t ->
            _voiceState.update { it.copy(liveStatusMessage = "Mic error: ${t.localizedMessage}") }
        }
    }

    fun finishPushToTalkTurn() {
        viewModelScope.launch {
            _voiceState.update {
                it.copy(isPushToTalkRecording = false, isVoiceTurnProcessing = true, liveStatusMessage = "Thinking...")
            }
            val recorded = audioRecorder.stopAndReadBytes()
            if (recorded == null) {
                _voiceState.update {
                    it.copy(isVoiceTurnProcessing = false, liveStatusMessage = "No audio captured.")
                }
                return@launch
            }
            try {
                val (bytes, mime) = recorded
                val prompt = """
                    Listen to the user's spoken question. Respond in two parts separated by '|||':
                    Part 1: Brief accurate transcription of what the user said.
                    Part 2: A natural, concise conversational spoken answer (no markdown symbols).
                """.trimIndent()
                val replyText = aiService.runPushToTalkTurn(bytes, mime, prompt)
                val parts = replyText.split("|||")
                val userTranscript = parts.getOrNull(0)?.trim().takeUnless { it.isNullOrBlank() } ?: "[Voice message]"
                val answer = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: replyText.trim()

                _voiceState.update { cur ->
                    val next = cur.liveTranscripts.toMutableList()
                    next.add(VoiceTranscriptLine(speaker = "You", text = userTranscript))
                    next.add(VoiceTranscriptLine(speaker = "Luminary", text = answer))
                    cur.copy(
                        liveTranscripts = next,
                        isVoiceTurnProcessing = false,
                        liveStatusMessage = "Speaking reply..."
                    )
                }
                speakVoiceReply(answer)
            } catch (t: Throwable) {
                _voiceState.update {
                    it.copy(
                        isVoiceTurnProcessing = false,
                        liveStatusMessage = t.localizedMessage ?: "Voice turn failed."
                    )
                }
            }
        }
    }

    private fun speakVoiceReply(text: String) {
        if (tts == null) {
            tts = TextToSpeech(getApplication()) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_${UUID.randomUUID()}")
                }
            }
        } else if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_${UUID.randomUUID()}")
        }
    }

    fun toggleTimestamps() {
        _voiceState.update { it.copy(includeTimestamps = !it.includeTimestamps) }
    }

    fun toggleSummarizeTranscript() {
        _voiceState.update { it.copy(summarizeTranscript = !it.summarizeTranscript) }
    }

    fun startAudioRecordingForTranscription() {
        runCatching {
            audioRecorder.startRecording()
            recordingTimerJob?.cancel()
            _voiceState.update {
                it.copy(isRecordingTranscript = true, recordingSeconds = 0, transcribeError = null)
            }
            recordingTimerJob = viewModelScope.launch {
                while (true) {
                    delay(1_000L)
                    _voiceState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                }
            }
        }.onFailure { t ->
            _voiceState.update {
                it.copy(transcribeError = t.localizedMessage ?: "Could not start microphone.")
            }
        }
    }

    fun stopAudioRecordingAndTranscribe() {
        recordingTimerJob?.cancel()
        viewModelScope.launch {
            _voiceState.update { it.copy(isRecordingTranscript = false) }
            val recorded = audioRecorder.stopAndReadBytes()
            if (recorded == null) {
                _voiceState.update { it.copy(transcribeError = "Recording was empty.") }
                return@launch
            }
            pendingAudioData = recorded
            _voiceState.update {
                it.copy(
                    pendingAudioName = "Mic recording (${it.recordingSeconds}s)",
                    hasPendingAudio = true
                )
            }
            transcribePendingAudio()
        }
    }

    fun attachAudioFileUri(uri: Uri) {
        viewModelScope.launch {
            val loaded = repository.readUriBytes(uri)
            if (loaded != null) {
                pendingAudioData = loaded
                _voiceState.update {
                    it.copy(
                        pendingAudioName = uri.lastPathSegment ?: "Selected audio",
                        hasPendingAudio = true,
                        transcribeError = null
                    )
                }
            } else {
                _voiceState.update { it.copy(transcribeError = "Could not read selected audio file.") }
            }
        }
    }

    fun transcribePendingAudio() {
        val data = pendingAudioData ?: run {
            _voiceState.update { it.copy(transcribeError = "Record or upload an audio file first.") }
            return
        }
        val st = _voiceState.value
        viewModelScope.launch {
            _voiceState.update { it.copy(isTranscribing = true, transcribeError = null) }
            try {
                val transcript = aiService.transcribeAudio(
                    audioBytes = data.first,
                    mimeType = data.second,
                    includeTimestamps = st.includeTimestamps,
                    summarize = st.summarizeTranscript
                )
                repository.saveCreation(
                    type = "TRANSCRIPT",
                    title = (st.pendingAudioName ?: "Audio Transcript").take(60),
                    prompt = st.pendingAudioName ?: "Recorded Audio",
                    content = transcript
                )
                refreshSqlTables()
                _voiceState.update {
                    it.copy(isTranscribing = false, transcriptResult = transcript)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _voiceState.update {
                    it.copy(
                        isTranscribing = false,
                        transcribeError = t.localizedMessage ?: "Audio transcription failed."
                    )
                }
            }
        }
    }

    // ---- Three.js 3D Studio ----

    fun updateThreeDPrompt(text: String) {
        _threeDState.update { it.copy(prompt = text, error = null) }
    }

    fun updateThreeDCode(code: String) {
        _threeDState.update {
            it.copy(generatedCode = code, sceneVersion = it.sceneVersion + 1)
        }
    }

    fun toggleThreeDAutoRotate() {
        _threeDState.update { it.copy(autoRotate = !it.autoRotate) }
    }

    fun toggleThreeDWireframe() {
        _threeDState.update { it.copy(wireframe = !it.wireframe) }
    }

    fun toggleThreeDCodeEditor() {
        _threeDState.update { it.copy(showCodeEditor = !it.showCodeEditor) }
    }

    fun generateThreeDScene(customPrompt: String? = null, refineExisting: Boolean = false) {
        val st = _threeDState.value
        val prompt = (customPrompt ?: st.prompt).trim()
        if (prompt.isBlank()) {
            _threeDState.update { it.copy(error = "Describe the 3D scene you want to build.") }
            return
        }
        viewModelScope.launch {
            _threeDState.update { it.copy(isGenerating = true, prompt = prompt, error = null) }
            try {
                val code = aiService.generateThreeJsScene(
                    prompt = prompt,
                    existingCode = if (refineExisting) st.generatedCode else null
                )
                repository.saveCreation(
                    type = "THREEJS",
                    title = prompt.take(60),
                    prompt = prompt,
                    content = code
                )
                refreshSqlTables()
                _threeDState.update {
                    it.copy(
                        isGenerating = false,
                        generatedCode = code,
                        sceneVersion = it.sceneVersion + 1,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _threeDState.update {
                    it.copy(isGenerating = false, error = t.localizedMessage ?: "Failed to generate 3D scene.")
                }
            }
        }
    }

    fun loadSavedThreeDScene(creation: CreationEntity) {
        if (creation.type != "THREEJS" || creation.content.isBlank()) return
        _threeDState.update {
            it.copy(
                prompt = creation.prompt,
                generatedCode = creation.content,
                sceneVersion = it.sceneVersion + 1,
                error = null
            )
        }
    }

    // ---- SQL Database Studio ----

    fun refreshSqlTables() {
        viewModelScope.launch {
            val tables = repository.listSqlTables()
            _sqlState.update { it.copy(tables = tables) }
        }
    }

    fun updateSqlQuery(sql: String) {
        _sqlState.update { it.copy(sqlQuery = sql) }
    }

    fun updateSqlNlPrompt(text: String) {
        _sqlState.update { it.copy(nlPrompt = text) }
    }

    fun inspectSqlTable(tableName: String) {
        val safe = tableName.replace("\"", "\"\"")
        val sql = "SELECT * FROM \"$safe\" LIMIT 50;"
        _sqlState.update { it.copy(selectedTable = tableName, sqlQuery = sql) }
        runSqlQuery(sql)
    }

    fun runSqlQuery(sqlOverride: String? = null) {
        val sql = (sqlOverride ?: _sqlState.value.sqlQuery).trim()
        if (sql.isBlank()) return
        viewModelScope.launch {
            _sqlState.update { it.copy(isRunningSql = true) }
            val result = repository.executeRawSql(sql)
            val tables = repository.listSqlTables()
            _sqlState.update {
                it.copy(isRunningSql = false, lastResult = result, tables = tables)
            }
        }
    }

    fun generateSqlFromNaturalLanguage() {
        val request = _sqlState.value.nlPrompt.trim()
        if (request.isBlank()) return
        viewModelScope.launch {
            _sqlState.update { it.copy(isGeneratingSql = true) }
            try {
                val schema = repository.getSqlSchemaSummary()
                val generatedSql = aiService.generateSqlQuery(request, schema)
                _sqlState.update {
                    it.copy(isGeneratingSql = false, sqlQuery = generatedSql)
                }
                if (generatedSql.trimStart().uppercase(Locale.ROOT).startsWith("SELECT")) {
                    runSqlQuery(generatedSql)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _sqlState.update {
                    it.copy(
                        isGeneratingSql = false,
                        lastResult = com.example.data.SqlExecutionResult(
                            sql = it.sqlQuery,
                            error = t.localizedMessage ?: "Failed to generate SQL."
                        )
                    )
                }
            }
        }
    }

    fun deleteCreation(creation: CreationEntity) {
        viewModelScope.launch {
            repository.deleteCreation(creation)
            refreshSqlTables()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceLiveManager.stopLiveConversation()
        audioRecorder.stopAndDiscard()
        recordingTimerJob?.cancel()
        tts?.stop()
        tts?.shutdown()
    }
}
