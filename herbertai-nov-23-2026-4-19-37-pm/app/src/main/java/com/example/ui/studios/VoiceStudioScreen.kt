package com.example.ui.studios

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Transcribe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.LiveVoiceStatus
import com.example.data.CreationEntity
import com.example.ui.StudioViewModel
import com.example.ui.VoiceStudioUiState
import com.example.ui.components.MarkdownText

@Composable
fun VoiceStudioScreen(
    studioViewModel: StudioViewModel,
    voiceState: VoiceStudioUiState,
    creations: List<CreationEntity>,
    onAskInChatbot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var subTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Live Voice, 1 = Transcribe
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        }
        pendingAudioAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            action()
        } else {
            pendingAudioAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) studioViewModel.attachAudioFileUri(uri)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 780.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("voice_subtab_live")
                ) {
                    Text("Voice Chat")
                }
                SegmentedButton(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Outlined.Transcribe, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("voice_subtab_transcribe")
                ) {
                    Text("Transcribe Audio")
                }
            }
        }

        if (subTab == 0) {
            item {
                LiveVoiceCard(
                    state = voiceState,
                    onSelectVoice = studioViewModel::selectVoiceName,
                    onToggleLive = {
                        if (voiceState.liveStatus == LiveVoiceStatus.LIVE || voiceState.liveStatus == LiveVoiceStatus.CONNECTING) {
                            studioViewModel.stopLiveVoiceConversation()
                        } else {
                            runWithMicPermission { studioViewModel.startLiveVoiceConversation() }
                        }
                    },
                    onTogglePushToTalk = {
                        if (voiceState.isPushToTalkRecording) {
                            studioViewModel.finishPushToTalkTurn()
                        } else {
                            runWithMicPermission { studioViewModel.startPushToTalkTurn() }
                        }
                    }
                )
            }

            if (voiceState.liveTranscripts.isNotEmpty()) {
                item {
                    Text(
                        text = "Conversation Transcript",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(voiceState.liveTranscripts, key = { it.id }) { line ->
                    val isUser = line.speaker == "You"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = line.speaker,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        } else {
            item {
                TranscribeAudioCard(
                    state = voiceState,
                    onToggleRecord = {
                        if (voiceState.isRecordingTranscript) {
                            studioViewModel.stopAudioRecordingAndTranscribe()
                        } else {
                            runWithMicPermission { studioViewModel.startAudioRecordingForTranscription() }
                        }
                    },
                    onPickAudioFile = { audioFilePicker.launch(arrayOf("audio/*")) },
                    onToggleTimestamps = studioViewModel::toggleTimestamps,
                    onToggleSummary = studioViewModel::toggleSummarizeTranscript,
                    onTranscribe = studioViewModel::transcribePendingAudio
                )
            }

            if (voiceState.transcriptResult.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Transcript Output", style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(voiceState.transcriptResult)) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy transcript")
                                    }
                                    TextButton(onClick = {
                                        onAskInChatbot("Analyze and answer questions about this audio transcript:\n\n${voiceState.transcriptResult}")
                                    }) {
                                        Text("Ask Chat")
                                    }
                                }
                            }
                            MarkdownText(markdown = voiceState.transcriptResult)
                        }
                    }
                }
            }

            val savedTranscripts = creations.filter { it.type == "TRANSCRIPT" }
            if (savedTranscripts.isNotEmpty()) {
                item {
                    Text(
                        text = "Saved Transcripts (${savedTranscripts.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(savedTranscripts, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = item.content.take(240),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceCard(
    state: VoiceStudioUiState,
    onSelectVoice: (String) -> Unit,
    onToggleLive: () -> Unit,
    onTogglePushToTalk: () -> Unit
) {
    val isLive = state.liveStatus == LiveVoiceStatus.LIVE
    val pulse = rememberInfiniteTransition(label = "voice_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isLive || state.isPushToTalkRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "orb_scale"
    )

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text("Voice Conversations", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Real-time bidirectional Gemini Live audio chat or tap-to-talk voice turns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        if (isLive || state.isPushToTalkRecording) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                Icon(
                    imageVector = if (isLive) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                    contentDescription = "Voice status orb",
                    tint = if (isLive || state.isPushToTalkRecording) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }

            if (!state.liveStatusMessage.isNullOrBlank()) {
                Text(
                    text = state.liveStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.liveStatus == LiveVoiceStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf("FENRIR", "PUCK", "KORE", "CHARON", "AOEDE").forEach { voice ->
                    FilterChip(
                        selected = state.selectedVoice == voice,
                        onClick = { onSelectVoice(voice) },
                        enabled = !isLive,
                        label = { Text(voice.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.testTag("voice_select_$voice")
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onToggleLive,
                    colors = if (isLive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("toggle_live_voice_btn")
                ) {
                    Icon(
                        if (isLive) Icons.Outlined.CallEnd else Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (state.liveStatus) {
                            LiveVoiceStatus.CONNECTING -> "Connecting..."
                            LiveVoiceStatus.LIVE -> "End Live"
                            else -> "Live Audio"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onTogglePushToTalk,
                    enabled = !isLive && !state.isVoiceTurnProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("push_to_talk_voice_btn")
                ) {
                    Icon(
                        if (state.isPushToTalkRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            state.isPushToTalkRecording -> "Send Turn"
                            state.isVoiceTurnProcessing -> "Replying..."
                            else -> "Voice Turn"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscribeAudioCard(
    state: VoiceStudioUiState,
    onToggleRecord: () -> Unit,
    onPickAudioFile: () -> Unit,
    onToggleTimestamps: () -> Unit,
    onToggleSummary: () -> Unit,
    onTranscribe: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Transcribe Audio with Gemini", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Record directly from your microphone or select any audio file (MP3, M4A, WAV, OGG).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.includeTimestamps,
                    onClick = onToggleTimestamps,
                    label = { Text("Timestamps & Speakers") }
                )
                FilterChip(
                    selected = state.summarizeTranscript,
                    onClick = onToggleSummary,
                    label = { Text("Key Takeaways") }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onToggleRecord,
                    colors = if (state.isRecordingTranscript) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("record_audio_btn")
                ) {
                    Icon(
                        if (state.isRecordingTranscript) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.isRecordingTranscript) "Stop (${state.recordingSeconds}s)"
                        else "Record Mic"
                    )
                }

                OutlinedButton(
                    onClick = onPickAudioFile,
                    enabled = !state.isRecordingTranscript,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("upload_audio_btn")
                ) {
                    Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Audio")
                }
            }

            if (state.hasPendingAudio && !state.pendingAudioName.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Ready: ${state.pendingAudioName}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onTranscribe,
                            enabled = !state.isTranscribing,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("run_transcribe_btn")
                        ) {
                            if (state.isTranscribing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Transcribing...")
                            } else {
                                Text("Transcribe")
                            }
                        }
                    }
                }
            }

            if (state.transcribeError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = state.transcribeError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
