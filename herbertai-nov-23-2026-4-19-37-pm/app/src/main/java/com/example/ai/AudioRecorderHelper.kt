package com.example.ai

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AudioRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File {
        stopAndDiscard()
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${UUID.randomUUID()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        outputFile = file
        return file
    }

    suspend fun stopAndReadBytes(): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        val mr = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        if (file == null || !file.exists()) return@withContext null
        val bytes = runCatching { file.readBytes() }.getOrNull()
        runCatching { file.delete() }
        if (bytes == null || bytes.isEmpty()) return@withContext null
        bytes to "audio/mp4"
    }

    fun stopAndDiscard() {
        val mr = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        if (file != null) {
            runCatching { file.delete() }
        }
    }
}
