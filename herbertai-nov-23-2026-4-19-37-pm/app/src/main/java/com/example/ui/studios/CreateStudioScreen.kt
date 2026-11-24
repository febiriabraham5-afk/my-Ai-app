package com.example.ui.studios

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai.ImageAspectOption
import com.example.data.CreationEntity
import com.example.ui.ImageStudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.VideoStudioUiState
import java.io.File

@Composable
fun CreateStudioScreen(
    studioViewModel: StudioViewModel,
    imageState: ImageStudioUiState,
    videoState: VideoStudioUiState,
    creations: List<CreationEntity>,
    modifier: Modifier = Modifier
) {
    var subTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Image, 1 = Video

    val imageEditPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) studioViewModel.attachSourceImageForEdit(uri)
    }

    val videoImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) studioViewModel.attachVideoSourceImage(uri)
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
                    icon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("create_subtab_image")
                ) {
                    Text("Images")
                }
                SegmentedButton(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("create_subtab_video")
                ) {
                    Text("Video (Veo)")
                }
            }
        }

        if (subTab == 0) {
            item {
                ImageStudioContent(
                    state = imageState,
                    onPromptChange = studioViewModel::updateImagePrompt,
                    onSelectAspect = studioViewModel::selectImageAspect,
                    onTogglePro = studioViewModel::toggleProImageQuality,
                    onPickSourceImage = {
                        imageEditPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClearSourceImage = studioViewModel::clearImageEditSource,
                    onGenerate = studioViewModel::generateOrEditImage,
                    onEditGenerated = studioViewModel::useGeneratedImageAsEditSource,
                    onAnimateInVideo = { bmp ->
                        studioViewModel.sendGeneratedImageToVideoStudio(bmp)
                        subTab = 1
                    }
                )
            }

            val savedImages = creations.filter { it.type == "IMAGE" && !it.filePath.isNullOrBlank() }
            if (savedImages.isNotEmpty()) {
                item {
                    Text(
                        text = "Saved Images (${savedImages.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(savedImages, key = { it.id }) { item ->
                            val bmp = remember(item.filePath) {
                                item.filePath?.let { p ->
                                    val f = File(p)
                                    if (f.exists()) BitmapFactory.decodeFile(p) else null
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.width(170.dp)
                            ) {
                                Column {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clickable {
                                                    studioViewModel.sendGeneratedImageToVideoStudio(bmp)
                                                    subTab = 1
                                                }
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { studioViewModel.deleteCreation(item) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = "Delete image",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                VideoStudioContent(
                    state = videoState,
                    onPromptChange = studioViewModel::updateVideoPrompt,
                    onSelectAspect = studioViewModel::selectVideoAspect,
                    onPickSourceImage = {
                        videoImagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClearSourceImage = studioViewModel::clearVideoSourceImage,
                    onGenerate = studioViewModel::generateVideo
                )
            }

            val savedVideos = creations.filter { it.type == "VIDEO" && !it.filePath.isNullOrBlank() }
            if (savedVideos.isNotEmpty()) {
                item {
                    Text(
                        text = "Saved Videos (${savedVideos.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(savedVideos, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(item.metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = { studioViewModel.deleteCreation(item) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete video")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageStudioContent(
    state: ImageStudioUiState,
    onPromptChange: (String) -> Unit,
    onSelectAspect: (ImageAspectOption) -> Unit,
    onTogglePro: () -> Unit,
    onPickSourceImage: () -> Unit,
    onClearSourceImage: () -> Unit,
    onGenerate: () -> Unit,
    onEditGenerated: () -> Unit,
    onAnimateInVideo: (android.graphics.Bitmap) -> Unit
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
            Text(
                text = if (state.sourceImage != null) "Edit Image with Gemini" else "Create Image with Gemini",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Powered by Nano Banana 2.1 & Gemini 3 Pro Image via Firebase AI Logic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.sourceImage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = state.sourceImage.asImageBitmap(),
                            contentDescription = "Source photo for editing",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Editing Reference Attached", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Describe changes such as style, lighting, colors, or objects.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onClearSourceImage,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_edit_source_image")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear reference image")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = {
                    Text(
                        if (state.sourceImage != null) "Describe how to edit this image..."
                        else "Describe the image you want to create..."
                    )
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_prompt_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ImageAspectOption.entries.forEach { opt ->
                    FilterChip(
                        selected = state.aspectOption == opt,
                        onClick = { onSelectAspect(opt) },
                        label = { Text(opt.label) },
                        modifier = Modifier.testTag("aspect_${opt.label}")
                    )
                }
                FilterChip(
                    selected = state.useProQuality,
                    onClick = onTogglePro,
                    label = { Text(if (state.useProQuality) "Pro Quality" else "Fast (Nano Banana)") },
                    modifier = Modifier.testTag("toggle_pro_image")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onPickSourceImage,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_image_to_edit_btn")
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.sourceImage == null) "Photo to Edit" else "Change Photo")
                }

                Button(
                    onClick = onGenerate,
                    enabled = !state.isGenerating,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("generate_image_btn")
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Creating...")
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.sourceImage != null) "Apply Edit" else "Create")
                    }
                }
            }

            if (state.error != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (state.generatedImage != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        bitmap = state.generatedImage.asImageBitmap(),
                        contentDescription = "Generated image result",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    if (state.modelUsed.isNotBlank()) {
                        Text(
                            text = "Generated with ${state.modelUsed}${if (state.caption.isNotBlank()) " • ${state.caption}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onEditGenerated,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("edit_generated_image_btn")
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit This")
                        }
                        Button(
                            onClick = { onAnimateInVideo(state.generatedImage) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("animate_generated_image_btn")
                        ) {
                            Icon(Icons.Outlined.SlowMotionVideo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Animate Video")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoStudioContent(
    state: VideoStudioUiState,
    onPromptChange: (String) -> Unit,
    onSelectAspect: (String) -> Unit,
    onPickSourceImage: () -> Unit,
    onClearSourceImage: () -> Unit,
    onGenerate: () -> Unit
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
            Text(
                text = if (state.sourceImage != null) "Animate Image into Video (Veo)" else "Generate Video from Text (Veo)",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Generate high-definition clips from a text prompt or animate an existing image with Veo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.sourceImage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = state.sourceImage.asImageBitmap(),
                            contentDescription = "Keyframe image to animate",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Image-to-Video Keyframe Ready", style = MaterialTheme.typography.labelLarge)
                            Text("Describe the camera motion & animation.", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = onClearSourceImage,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_video_source_image")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove keyframe")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = {
                    Text(
                        if (state.sourceImage != null) "Describe motion & camera movement..."
                        else "Describe your video scene, lighting & motion..."
                    )
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_prompt_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("16:9", "9:16").forEach { ratio ->
                    FilterChip(
                        selected = state.aspectRatio == ratio,
                        onClick = { onSelectAspect(ratio) },
                        label = { Text(ratio) },
                        modifier = Modifier.testTag("video_aspect_$ratio")
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onPickSourceImage,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_video_keyframe_btn")
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.sourceImage == null) "Animate Image" else "Swap Image")
                }

                Button(
                    onClick = onGenerate,
                    enabled = !state.isGenerating,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("generate_video_btn")
                ) {
                    Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isGenerating) "Rendering..." else "Generate Video")
                }
            }

            if (state.isGenerating) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = state.progressMessage.ifBlank { "Rendering with Veo..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (state.error != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!state.videoFilePath.isNullOrBlank() && File(state.videoFilePath).exists()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (state.aspectRatio == "9:16") 9f / 16f else 16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setOnPreparedListener { mp: MediaPlayer ->
                                        mp.isLooping = true
                                        start()
                                    }
                                    setVideoURI(Uri.fromFile(File(state.videoFilePath)))
                                }
                            },
                            update = { vv ->
                                val targetUri = Uri.fromFile(File(state.videoFilePath))
                                if (vv.tag != state.videoFilePath) {
                                    vv.tag = state.videoFilePath
                                    vv.setVideoURI(targetUri)
                                    vv.start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (state.modelUsed.isNotBlank()) {
                        Text(
                            text = "Rendered with ${state.modelUsed}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
