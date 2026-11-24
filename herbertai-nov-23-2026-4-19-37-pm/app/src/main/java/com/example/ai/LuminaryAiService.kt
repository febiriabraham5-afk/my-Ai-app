package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.retrievalConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LuminaryAiService"

// Supported Firebase AI Logic models
private const val DEFAULT_MODEL = "gemini-3.8-flash"
private const val FAST_MODEL = "gemini-3.5-flash-lite"
private const val PRO_MODEL = "gemini-4-argon"

private const val FIRST_TOKEN_TIMEOUT_MS = 15_000L
private const val DEEP_DIVE_FIRST_TOKEN_TIMEOUT_MS = 22_000L
private const val FALLBACK_FIRST_TOKEN_TIMEOUT_MS = 18_000L
private const val PRIMARY_STREAM_TIMEOUT_MS = 60_000L
private const val FALLBACK_STREAM_TIMEOUT_MS = 45_000L
private const val UTILITY_TIMEOUT_MS = 45_000L
private const val SUGGESTION_TIMEOUT_MS = 30_000L
private const val RETRY_BACKOFF_MS = 1_100L
private const val MAX_GROUNDING_SOURCES = 6

data class ChatHistoryTurn(
    val role: String,
    val text: String,
    val image: Bitmap? = null
)

data class StreamAnswerUpdate(
    val answerText: String,
    val thoughtSummary: String = "",
    val thinkingSeconds: Int = 0
)

private data class StreamAttemptSpec(
    val modelName: String,
    val useThinkingConfig: Boolean,
    val enableGoogleSearch: Boolean,
    val enableGoogleMaps: Boolean,
    val firstTokenTimeoutMs: Long,
    val overallTimeoutMs: Long
)

class LuminaryAiService {

    private val baseSystemPrompt = """
        You are Luminary, a sharp, transparent, and versatile AI assistant combining the clarity of Gemini, structured reasoning of DeepSeek, and direct wit and precision of Grok.
        When presenting your work:
        - Start with a crisp, direct answer or brief **Key Takeaway** summary when helpful before diving into details.
        - Structure multi-part responses with clear `##` / `###` headings, numbered step-by-step reasoning for derivations/problem-solving, and clean Markdown comparison tables (`| Column | Column |`) whenever comparing options, specs, timelines, or pros and cons.
        - Use fenced code blocks with language tags for code, commands, or formulas.
        - When Google Search or Google Maps tools are enabled, actively use them for real-time information, news, weather, live scores, prices, places, addresses, hours, ratings, and directions. Always respect the current date/time and location context provided.
        - Never fabricate citations, place details, or uncertain facts; state uncertainty clearly.
    """.trimIndent()

    private val ungroundedSystemPrompt = """
        You are Luminary, a sharp, transparent, and versatile AI assistant combining the clarity of Gemini, structured reasoning of DeepSeek, and directness of Grok.
        Live web/maps grounding is disabled or temporarily unavailable for this attempt. Use the current date/time provided for context, lead with a clear direct answer, organize complex work with headings, numbered steps, and Markdown comparison tables (`| Col | Col |`), and note clearly if real-time web or Maps data is needed.
    """.trimIndent()

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private val followUpSchema = Schema.obj(
        properties = mapOf(
            "followUps" to Schema.array(
                items = Schema.string(description = "Short natural follow-up question under 9 words"),
                description = "3 relevant follow-up questions the user might want to ask next"
            )
        )
    )

    private val suggestionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = followUpSchema
            }
        )
    }

    fun warmUp(defaultMode: AnswerMode = AnswerMode.BALANCED) {
        runCatching {
            getOrCreateModel(
                modelName = DEFAULT_MODEL,
                mode = defaultMode,
                useThinkingConfig = true,
                enableGoogleSearch = true,
                enableGoogleMaps = false,
                locationLatLng = null
            )
            getOrCreateModel(
                modelName = FAST_MODEL,
                mode = defaultMode,
                useThinkingConfig = false,
                enableGoogleSearch = true,
                enableGoogleMaps = false,
                locationLatLng = null
            )
        }.onFailure { t ->
            Log.w(TAG, "Model warm-up skipped: ${t.message}")
        }
    }

    private fun getOrCreateModel(
        modelName: String,
        mode: AnswerMode,
        useThinkingConfig: Boolean,
        enableGoogleSearch: Boolean,
        enableGoogleMaps: Boolean,
        locationLatLng: Pair<Double, Double>?
    ): GenerativeModel {
        val locKey = if (enableGoogleMaps && locationLatLng != null) {
            String.format(Locale.US, "%.2f,%.2f", locationLatLng.first, locationLatLng.second)
        } else {
            "none"
        }
        val cacheKey = "$modelName:${mode.name}:$useThinkingConfig:$enableGoogleSearch:$enableGoogleMaps:$locKey"
        return modelCache.getOrPut(cacheKey) {
            val config = if (useThinkingConfig) {
                generationConfig {
                    thinkingConfig = thinkingConfig {
                        thinkingLevel = when (mode) {
                            AnswerMode.DEEP_DIVE -> ThinkingLevel.MEDIUM
                            AnswerMode.ELI5 -> ThinkingLevel.MINIMAL
                            AnswerMode.CONCISE, AnswerMode.BALANCED -> ThinkingLevel.LOW
                        }
                        includeThoughts = true
                    }
                }
            } else {
                null
            }
            val tools = buildList {
                if (enableGoogleSearch) add(Tool.googleSearch())
                if (enableGoogleMaps) add(Tool.googleMaps())
            }
            val toolConfig = if (enableGoogleMaps && locationLatLng != null) {
                ToolConfig(
                    retrievalConfig = retrievalConfig {
                        latLng = LatLng(locationLatLng.first, locationLatLng.second)
                    }
                )
            } else {
                null
            }
            val sysPrompt = if (tools.isNotEmpty()) baseSystemPrompt else ungroundedSystemPrompt
            Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = config,
                tools = tools,
                toolConfig = toolConfig,
                systemInstruction = content {
                    text("$sysPrompt\n\nResponse style requirement: ${mode.instructionSuffix}")
                }
            )
        }
    }

    private fun sanitizeHistory(history: List<ChatHistoryTurn>): List<Content> {
        val validTurns = mutableListOf<ChatHistoryTurn>()
        for (turn in history) {
            if (turn.text.isBlank() && turn.image == null) continue
            val expectedRole = if (validTurns.size % 2 == 0) "user" else "model"
            if (turn.role == expectedRole) {
                validTurns.add(turn)
            } else if (turn.role == "user" && validTurns.lastOrNull()?.role == "user") {
                validTurns[validTurns.lastIndex] = turn
            }
        }
        if (validTurns.lastOrNull()?.role == "user") {
            validTurns.removeAt(validTurns.lastIndex)
        }
        return validTurns.map { turn ->
            content(role = turn.role) {
                if (turn.image != null && turn.role == "user") {
                    image(turn.image)
                }
                if (turn.text.isNotBlank()) {
                    text(turn.text)
                }
            }
        }
    }

    private fun currentTimestampContext(locationLatLng: Pair<Double, Double>?): String {
        val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm z", Locale.getDefault())
        val locNote = if (locationLatLng != null) {
            String.format(Locale.US, " | User approx coords: %.4f, %.4f", locationLatLng.first, locationLatLng.second)
        } else {
            ""
        }
        return "[Current local date and time: ${formatter.format(Date())}$locNote]"
    }

    private fun collectSources(
        response: GenerateContentResponse,
        webSources: LinkedHashMap<String, String>,
        mapSources: LinkedHashMap<String, String>
    ) {
        val chunks = response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks ?: return
        for (chunk in chunks) {
            val web = chunk.web
            if (web != null) {
                val uri = web.uri?.trim().orEmpty()
                if ((uri.startsWith("https://", ignoreCase = true) || uri.startsWith("http://", ignoreCase = true)) &&
                    !webSources.containsKey(uri)
                ) {
                    val safeTitle = web.title
                        ?.replace("[", "(")
                        ?.replace("]", ")")
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                        .orEmpty()
                    webSources[uri] = safeTitle.ifBlank { uri }
                }
            }
            val maps = chunk.maps
            if (maps != null) {
                val safeTitle = maps.title
                    ?.replace("[", "(")
                    ?.replace("]", ")")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()
                val rawUri = maps.uri?.trim().orEmpty()
                val resolvedUri = when {
                    rawUri.startsWith("http", ignoreCase = true) -> rawUri
                    !maps.placeId.isNullOrBlank() || safeTitle.isNotBlank() -> {
                        val q = URLEncoder.encode(safeTitle.ifBlank { "Google Maps Place" }, "UTF-8")
                        val pid = maps.placeId?.let { "&query_place_id=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                        "https://www.google.com/maps/search/?api=1&query=$q$pid"
                    }
                    else -> ""
                }
                if (resolvedUri.isNotBlank() && !mapSources.containsKey(resolvedUri)) {
                    mapSources[resolvedUri] = safeTitle.ifBlank { "View on Google Maps" }
                }
            }
        }
    }

    private fun formatSourcesMarkdown(
        webSources: Map<String, String>,
        mapSources: Map<String, String>
    ): String {
        if (webSources.isEmpty() && mapSources.isEmpty()) return ""
        return buildString {
            if (mapSources.isNotEmpty()) {
                append("\n\n### Google Maps Places\n")
                mapSources.entries.take(MAX_GROUNDING_SOURCES).forEach { (uri, title) ->
                    append("- [$title]($uri)\n")
                }
            }
            if (webSources.isNotEmpty()) {
                append("\n\n### Sources\n")
                webSources.entries.take(MAX_GROUNDING_SOURCES).forEach { (uri, title) ->
                    append("- [$title]($uri)\n")
                }
            }
        }.trimEnd()
    }

    private fun isQuotaOrBillingError(t: Throwable): Boolean = isQuotaError(t)

    private fun isHighDemandOrTransientError(t: Throwable): Boolean = isTransientHighDemandError(t)

    internal companion object {
        fun isQuotaError(t: Throwable): Boolean {
            var cur: Throwable? = t
            while (cur != null) {
                val msg = cur.message.orEmpty().lowercase(Locale.ROOT)
                if (msg.contains("quota") ||
                    msg.contains("429") ||
                    msg.contains("resource_exhausted") ||
                    msg.contains("resource exhausted") ||
                    msg.contains("billing") ||
                    msg.contains("rate limit") ||
                    msg.contains("limit: 0")
                ) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }

        fun isTransientHighDemandError(t: Throwable): Boolean {
            var cur: Throwable? = t
            while (cur != null) {
                val msg = cur.message.orEmpty().lowercase(Locale.ROOT)
                if (msg.contains("high demand") ||
                    msg.contains("unavailable") ||
                    msg.contains("503") ||
                    msg.contains("overloaded") ||
                    msg.contains("capacity") ||
                    msg.contains("try again later") ||
                    msg.contains("timed out") ||
                    cur is TimeoutCancellationException
                ) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }

        fun extractFencedCode(raw: String, languages: List<String>): String {
            val trimmed = raw.trim()
            val langPattern = languages.joinToString("|") { Regex.escape(it) }
            val fencedRegex = Regex("```(?:$langPattern)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            val match = fencedRegex.find(trimmed)
            if (match != null) {
                return match.groupValues[1].trim()
            }
            if (languages.any { it.equals("javascript", ignoreCase = true) || it.equals("js", ignoreCase = true) }) {
                val inlineScriptMatch = Regex("<script(?![^>]*\\bsrc=)[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
                    .find(trimmed)
                if (inlineScriptMatch != null) {
                    return inlineScriptMatch.groupValues[1].trim()
                }
            }
            return trimmed
                .replace(Regex("^```(?:$langPattern)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```$"), "")
                .trim()
        }
    }

    private suspend fun generateUtilityText(
        input: Content,
        systemInstructionText: String? = null,
        models: List<String> = listOf(FAST_MODEL, DEFAULT_MODEL, PRO_MODEL),
        timeoutMs: Long = UTILITY_TIMEOUT_MS
    ): String {
        val uniqueModels = models.distinct()
        var lastError: Throwable? = null
        var sawHighDemand = false

        suspend fun tryModel(modelName: String): String? {
            val config = if (modelName == DEFAULT_MODEL) {
                generationConfig {
                    thinkingConfig = thinkingConfig {
                        thinkingLevel = ThinkingLevel.MINIMAL
                    }
                }
            } else {
                null
            }
            val model = Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = config,
                systemInstruction = systemInstructionText?.let { text -> content { text(text) } }
            )
            val response = withTimeout(timeoutMs) {
                model.generateContent(input)
            }
            return response.text?.trim()?.takeIf { it.isNotBlank() }
        }

        for (modelName in uniqueModels) {
            try {
                val out = tryModel(modelName)
                if (out != null) return out
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    lastError = e
                    sawHighDemand = true
                    Log.w(TAG, "Utility generation timed out on $modelName after ${timeoutMs}ms")
                } else {
                    throw e
                }
            } catch (t: Throwable) {
                lastError = t
                if (isHighDemandOrTransientError(t)) {
                    sawHighDemand = true
                }
                Log.w(TAG, "Utility generation failed on $modelName: ${t.message}")
            }
        }

        if (sawHighDemand) {
            delay(RETRY_BACKOFF_MS)
            for (retryModel in listOf(FAST_MODEL, DEFAULT_MODEL)) {
                try {
                    val out = tryModel(retryModel)
                    if (out != null) return out
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) {
                        lastError = e
                    } else {
                        throw e
                    }
                } catch (t: Throwable) {
                    lastError = t
                    Log.w(TAG, "Utility retry failed on $retryModel: ${t.message}")
                }
            }
        }

        val err = lastError
        if (err != null) {
            Log.e(TAG, "All utility generation attempts failed", err)
        }
        if (err != null && isQuotaOrBillingError(err)) {
            throw IllegalStateException(
                "Gemini API quota exceeded for this project or billing tier. Please check your plan and billing details.",
                err
            )
        }
        if (err != null && isHighDemandOrTransientError(err)) {
            throw IllegalStateException(
                "Gemini models are temporarily experiencing high demand. Please tap Retry in a few seconds.",
                err
            )
        }
        throw err ?: IllegalStateException("The AI returned an empty response.")
    }

    private fun extractCodeBlock(raw: String, languages: List<String>): String =
        extractFencedCode(raw, languages)

    private fun buildStreamAttempts(
        mode: AnswerMode,
        useGoogleSearch: Boolean,
        useGoogleMaps: Boolean
    ): List<StreamAttemptSpec> {
        val primaryFirstTokenTimeout = if (mode == AnswerMode.DEEP_DIVE) {
            DEEP_DIVE_FIRST_TOKEN_TIMEOUT_MS
        } else {
            FIRST_TOKEN_TIMEOUT_MS
        }
        return buildList {
            if (useGoogleSearch || useGoogleMaps) {
                add(
                    StreamAttemptSpec(
                        modelName = DEFAULT_MODEL,
                        useThinkingConfig = true,
                        enableGoogleSearch = useGoogleSearch,
                        enableGoogleMaps = useGoogleMaps,
                        firstTokenTimeoutMs = primaryFirstTokenTimeout,
                        overallTimeoutMs = PRIMARY_STREAM_TIMEOUT_MS
                    )
                )
                if (useGoogleMaps && useGoogleSearch) {
                    add(
                        StreamAttemptSpec(
                            modelName = DEFAULT_MODEL,
                            useThinkingConfig = true,
                            enableGoogleSearch = true,
                            enableGoogleMaps = false,
                            firstTokenTimeoutMs = FALLBACK_FIRST_TOKEN_TIMEOUT_MS,
                            overallTimeoutMs = FALLBACK_STREAM_TIMEOUT_MS
                        )
                    )
                }
                add(
                    StreamAttemptSpec(
                        modelName = FAST_MODEL,
                        useThinkingConfig = false,
                        enableGoogleSearch = useGoogleSearch,
                        enableGoogleMaps = false,
                        firstTokenTimeoutMs = FALLBACK_FIRST_TOKEN_TIMEOUT_MS,
                        overallTimeoutMs = FALLBACK_STREAM_TIMEOUT_MS
                    )
                )
            } else {
                add(
                    StreamAttemptSpec(
                        modelName = DEFAULT_MODEL,
                        useThinkingConfig = true,
                        enableGoogleSearch = false,
                        enableGoogleMaps = false,
                        firstTokenTimeoutMs = primaryFirstTokenTimeout,
                        overallTimeoutMs = PRIMARY_STREAM_TIMEOUT_MS
                    )
                )
            }
            add(
                StreamAttemptSpec(
                    modelName = DEFAULT_MODEL,
                    useThinkingConfig = false,
                    enableGoogleSearch = false,
                    enableGoogleMaps = false,
                    firstTokenTimeoutMs = FALLBACK_FIRST_TOKEN_TIMEOUT_MS,
                    overallTimeoutMs = FALLBACK_STREAM_TIMEOUT_MS
                )
            )
            add(
                StreamAttemptSpec(
                    modelName = FAST_MODEL,
                    useThinkingConfig = false,
                    enableGoogleSearch = false,
                    enableGoogleMaps = false,
                    firstTokenTimeoutMs = FALLBACK_FIRST_TOKEN_TIMEOUT_MS,
                    overallTimeoutMs = FALLBACK_STREAM_TIMEOUT_MS
                )
            )
            add(
                StreamAttemptSpec(
                    modelName = PRO_MODEL,
                    useThinkingConfig = false,
                    enableGoogleSearch = false,
                    enableGoogleMaps = false,
                    firstTokenTimeoutMs = FALLBACK_FIRST_TOKEN_TIMEOUT_MS,
                    overallTimeoutMs = FALLBACK_STREAM_TIMEOUT_MS
                )
            )
        }
    }

    fun askStreaming(
        question: String,
        attachedImage: Bitmap?,
        history: List<ChatHistoryTurn>,
        mode: AnswerMode,
        useGoogleSearch: Boolean = true,
        useGoogleMaps: Boolean = true,
        locationLatLng: Pair<Double, Double>? = null
    ): Flow<StreamAnswerUpdate> = channelFlow {
        val sdkHistory = sanitizeHistory(history)
        val userMessageContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            val effectivePrompt = question.ifBlank {
                "Describe and explain what is shown in this image."
            }
            text("${currentTimestampContext(locationLatLng)}\n\n$effectivePrompt")
        }

        val attempts = buildStreamAttempts(
            mode = mode,
            useGoogleSearch = useGoogleSearch,
            useGoogleMaps = useGoogleMaps
        )

        var lastError: Throwable? = null
        var groundingQuotaFailed = false
        var sawHighDemand = false

        suspend fun runAttempt(spec: StreamAttemptSpec): Boolean {
            currentCoroutineContext().ensureActive()

            val accumulated = StringBuilder()
            val thoughtAccumulated = StringBuilder()
            val webSources = LinkedHashMap<String, String>()
            val mapSources = LinkedHashMap<String, String>()
            var firstTokenReceived = false
            var streamFailure: Throwable? = null
            var watchdogTimeoutReason: String? = null
            val startMs = System.currentTimeMillis()
            var answerStartedMs: Long? = null

            fun currentThinkingSeconds(): Int {
                val end = answerStartedMs ?: System.currentTimeMillis()
                return ((end - startMs) / 1000L).toInt().coerceAtLeast(if (thoughtAccumulated.isNotEmpty()) 1 else 0)
            }

            val chat = getOrCreateModel(
                modelName = spec.modelName,
                mode = mode,
                useThinkingConfig = spec.useThinkingConfig,
                enableGoogleSearch = spec.enableGoogleSearch,
                enableGoogleMaps = spec.enableGoogleMaps,
                locationLatLng = locationLatLng
            ).startChat(history = sdkHistory)

            val streamJob = launch {
                try {
                    chat.sendMessageStream(userMessageContent).collect { chunk ->
                        collectSources(chunk, webSources, mapSources)
                        val thoughtDelta = chunk.thoughtSummary.orEmpty()
                        val answerDelta = chunk.text.orEmpty()

                        if (thoughtDelta.isNotEmpty()) {
                            firstTokenReceived = true
                            thoughtAccumulated.append(thoughtDelta)
                        }
                        if (answerDelta.isNotEmpty()) {
                            firstTokenReceived = true
                            if (answerStartedMs == null) {
                                answerStartedMs = System.currentTimeMillis()
                            }
                            accumulated.append(answerDelta)
                        }
                        if (thoughtDelta.isNotEmpty() || answerDelta.isNotEmpty()) {
                            send(
                                StreamAnswerUpdate(
                                    answerText = accumulated.toString(),
                                    thoughtSummary = thoughtAccumulated.toString().trim(),
                                    thinkingSeconds = currentThinkingSeconds()
                                )
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    streamFailure = t
                }
            }

            val firstTokenWatchdog = launch {
                delay(spec.firstTokenTimeoutMs)
                if (!firstTokenReceived && streamJob.isActive) {
                    watchdogTimeoutReason = "Model ${spec.modelName} timed out before first token (${spec.firstTokenTimeoutMs}ms)."
                    streamJob.cancel(CancellationException("FirstTokenTimeout"))
                }
            }
            val overallWatchdog = launch {
                delay(spec.overallTimeoutMs)
                if (streamJob.isActive) {
                    watchdogTimeoutReason = "Model ${spec.modelName} timed out after ${spec.overallTimeoutMs}ms."
                    streamJob.cancel(CancellationException("OverallStreamTimeout"))
                }
            }

            try {
                streamJob.join()
            } finally {
                firstTokenWatchdog.cancel()
                overallWatchdog.cancel()
            }

            currentCoroutineContext().ensureActive()

            if (accumulated.isNotBlank()) {
                val sourcesSection = formatSourcesMarkdown(webSources, mapSources)
                if (sourcesSection.isNotBlank()) {
                    accumulated.append(sourcesSection)
                    send(
                        StreamAnswerUpdate(
                            answerText = accumulated.toString(),
                            thoughtSummary = thoughtAccumulated.toString().trim(),
                            thinkingSeconds = currentThinkingSeconds()
                        )
                    )
                } else if (!spec.enableGoogleSearch && !spec.enableGoogleMaps && groundingQuotaFailed) {
                    accumulated.append("\n\n> *Answered without live Search/Maps grounding because grounding quota or tool availability is currently limited.*")
                    send(
                        StreamAnswerUpdate(
                            answerText = accumulated.toString(),
                            thoughtSummary = thoughtAccumulated.toString().trim(),
                            thinkingSeconds = currentThinkingSeconds()
                        )
                    )
                }
                return true
            }

            val failure = streamFailure ?: watchdogTimeoutReason?.let { IllegalStateException(it) }
            if (failure != null) {
                lastError = failure
                if ((spec.enableGoogleSearch || spec.enableGoogleMaps) && isQuotaOrBillingError(failure)) {
                    groundingQuotaFailed = true
                }
                if (isHighDemandOrTransientError(failure)) {
                    sawHighDemand = true
                }
                Log.w(TAG, "Attempt ${spec.modelName} (search=${spec.enableGoogleSearch}, maps=${spec.enableGoogleMaps}, thinking=${spec.useThinkingConfig}) failed: ${failure.message}")
            }
            return false
        }

        for (spec in attempts) {
            if (runAttempt(spec)) return@channelFlow
        }

        if (sawHighDemand) {
            delay(RETRY_BACKOFF_MS)
            val retrySpec = StreamAttemptSpec(
                modelName = FAST_MODEL,
                useThinkingConfig = false,
                enableGoogleSearch = false,
                enableGoogleMaps = false,
                firstTokenTimeoutMs = FALLBACK_FIRST_TOKEN_TIMEOUT_MS,
                overallTimeoutMs = FALLBACK_STREAM_TIMEOUT_MS
            )
            if (runAttempt(retrySpec)) return@channelFlow
        }

        val finalErr = lastError
        if (finalErr != null) {
            Log.e(TAG, "All streaming chat attempts failed", finalErr)
        }
        if (finalErr != null && isQuotaOrBillingError(finalErr)) {
            throw IllegalStateException(
                "Gemini API quota exceeded for this project or billing tier. Please check your plan and billing details in Google Cloud / AI Studio, or try again shortly.",
                finalErr
            )
        }
        if (finalErr != null && isHighDemandOrTransientError(finalErr)) {
            throw IllegalStateException(
                "Gemini models are temporarily experiencing high demand. Please retry in a few seconds.",
                finalErr
            )
        }
        throw finalErr ?: IllegalStateException("The AI returned an empty response. Please try rephrasing your question.")
    }.flowOn(Dispatchers.IO)

    suspend fun runPushToTalkTurn(
        audioBytes: ByteArray,
        mimeType: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val input = content {
            inlineData(audioBytes, mimeType)
            text(prompt)
        }
        generateUtilityText(
            input = input,
            models = listOf(DEFAULT_MODEL, FAST_MODEL, PRO_MODEL),
            timeoutMs = UTILITY_TIMEOUT_MS
        )
    }

    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String,
        includeTimestamps: Boolean,
        summarize: Boolean
    ): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("Transcribe the attached audio accurately in its original language. ")
            if (includeTimestamps) {
                append("Include approximate [MM:SS] timestamps and speaker labels (e.g. Speaker 1:) where speaker changes occur. ")
            }
            if (summarize) {
                append("After the full transcript, include a concise '### Key Takeaways' bullet summary.")
            }
        }
        val input = content {
            inlineData(audioBytes, mimeType)
            text(prompt)
        }
        generateUtilityText(
            input = input,
            models = listOf(DEFAULT_MODEL, FAST_MODEL, PRO_MODEL),
            timeoutMs = UTILITY_TIMEOUT_MS
        )
    }

    suspend fun generateThreeJsScene(
        prompt: String,
        existingCode: String? = null
    ): String = withContext(Dispatchers.IO) {
        val instruction = """
            You are an expert Three.js WebGL creative coder.
            Write ONLY clean JavaScript code (no markdown fences, no HTML tags) to build an interactive Three.js 3D scene.
            The host HTML page ALREADY provides global `THREE` and `THREE.OrbitControls`, plus a full-screen WebGL container `<div id="stage"></div>`.
            Your script MUST:
            1. Create `scene`, `camera = new THREE.PerspectiveCamera(...)`, `renderer = new THREE.WebGLRenderer({ antialias: true })`.
            2. Attach `renderer.domElement` into `document.getElementById('stage')`.
            3. Create `controls = new THREE.OrbitControls(camera, renderer.domElement)` with `controls.enableDamping = true`.
            4. Add rich lighting, shadows or emissive materials, geometry/particles/shaders matching the user request, and smooth `requestAnimationFrame` animation.
            5. Register `window.__luminaryScene = { scene, camera, renderer, controls, setAutoRotate(v) { controls.autoRotate = v; }, setWireframe(v) { scene.traverse(o => { if (o.isMesh && o.material) { const mats = Array.isArray(o.material) ? o.material : [o.material]; mats.forEach(m => { m.wireframe = v; }); } }); }, resetCamera() { camera.position.copy(initialCamPos); controls.target.set(0,0,0); controls.update(); } };` where `initialCamPos` is `camera.position.clone()`.
            6. Handle `window.addEventListener('resize', ...)` cleanly.
        """.trimIndent()

        val fullPrompt = if (!existingCode.isNullOrBlank()) {
            "Modify or enhance this existing Three.js scene according to the request.\nRequest: $prompt\n\nExisting code:\n$existingCode"
        } else {
            "Create a visually striking, interactive Three.js 3D scene for: $prompt"
        }

        val raw = generateUtilityText(
            input = content { text(fullPrompt) },
            systemInstructionText = instruction,
            models = listOf(FAST_MODEL, DEFAULT_MODEL, PRO_MODEL),
            timeoutMs = UTILITY_TIMEOUT_MS
        )
        val cleaned = extractCodeBlock(raw, listOf("javascript", "js"))
        if (cleaned.isBlank()) {
            throw IllegalStateException("Could not generate Three.js scene.")
        }
        cleaned
    }

    suspend fun generateSqlQuery(
        naturalLanguageRequest: String,
        schemaSummary: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are a SQLite SQL expert. Given the SQLite database schema below, write ONE valid SQLite query or statement that fulfills the user's request.
            Return ONLY the raw SQL statement with no markdown code fences and no explanation.
            
            Database Schema:
            $schemaSummary
            
            User Request:
            $naturalLanguageRequest
        """.trimIndent()

        val raw = generateUtilityText(
            input = content { text(prompt) },
            models = listOf(FAST_MODEL, DEFAULT_MODEL, PRO_MODEL),
            timeoutMs = SUGGESTION_TIMEOUT_MS
        )
        val cleaned = extractCodeBlock(raw, listOf("sql"))
        if (cleaned.isBlank()) {
            throw IllegalStateException("Could not generate SQL query.")
        }
        cleaned
    }

    suspend fun generateFollowUps(question: String, answer: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val snippet = answer.take(1200)
            val prompt = """
                Based on this Q&A, suggest 3 concise, interesting follow-up questions the user might ask next (each under 9 words, no numbering).
                Question: $question
                Answer: $snippet
            """.trimIndent()

            val response = withTimeout(SUGGESTION_TIMEOUT_MS) {
                suggestionModel.generateContent(prompt)
            }
            val rawJson = response.text ?: return@withContext emptyList()
            val json = JSONObject(rawJson)
            val arr = json.optJSONArray("followUps") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }.take(3)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) emptyList() else throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate follow-up suggestions", e)
            emptyList()
        }
    }
}
