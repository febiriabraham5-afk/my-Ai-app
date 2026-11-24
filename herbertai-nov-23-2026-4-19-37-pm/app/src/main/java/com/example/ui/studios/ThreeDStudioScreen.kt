package com.example.ui.studios

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.CreationEntity
import com.example.ui.StudioViewModel
import com.example.ui.ThreeDStudioUiState

private val THREE_D_PRESETS = listOf(
    "Solar system orrery with glowing sun and orbiting rings" to "Solar System",
    "Bioluminescent cyberpunk crystal cavern with floating particles" to "Crystal Cavern",
    "Animated wireframe neon synthwave terrain grid and sunset sun" to "Cyber Terrain",
    "Glowing double-helix DNA strand rotating in deep space" to "DNA Helix"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThreeDStudioScreen(
    studioViewModel: StudioViewModel,
    state: ThreeDStudioUiState,
    creations: List<CreationEntity>,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(state.autoRotate, state.sceneVersion) {
        webViewRef?.evaluateJavascript(
            "window.__luminaryScene && window.__luminaryScene.setAutoRotate(${state.autoRotate});",
            null
        )
    }

    LaunchedEffect(state.wireframe, state.sceneVersion) {
        webViewRef?.evaluateJavascript(
            "window.__luminaryScene && window.__luminaryScene.setWireframe(${state.wireframe});",
            null
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 880.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ViewInAr, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text("Three.js 3D Scene Studio", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "Drag to orbit • Pinch to zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        val htmlDoc = remember(state.generatedCode, state.sceneVersion) {
                            buildThreeJsHtml(state.generatedCode)
                        }
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webChromeClient = WebChromeClient()
                                    webViewClient = WebViewClient()
                                    webViewRef = this
                                    loadDataWithBaseURL("https://luminary.local/", htmlDoc, "text/html", "UTF-8", null)
                                }
                            },
                            update = { wv ->
                                webViewRef = wv
                                if (wv.tag != state.sceneVersion) {
                                    wv.tag = state.sceneVersion
                                    wv.loadDataWithBaseURL("https://luminary.local/", htmlDoc, "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = state.autoRotate,
                            onClick = studioViewModel::toggleThreeDAutoRotate,
                            leadingIcon = { Icon(Icons.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Auto-Rotate") },
                            modifier = Modifier.testTag("three_d_autorotate")
                        )
                        FilterChip(
                            selected = state.wireframe,
                            onClick = studioViewModel::toggleThreeDWireframe,
                            label = { Text("Wireframe") },
                            modifier = Modifier.testTag("three_d_wireframe")
                        )
                        AssistChip(
                            onClick = {
                                webViewRef?.evaluateJavascript(
                                    "window.__luminaryScene && window.__luminaryScene.resetCamera();",
                                    null
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Reset Cam") },
                            modifier = Modifier.testTag("three_d_reset_cam")
                        )
                        FilterChip(
                            selected = state.showCodeEditor,
                            onClick = studioViewModel::toggleThreeDCodeEditor,
                            leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("JS Code") },
                            modifier = Modifier.testTag("three_d_code_toggle")
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        THREE_D_PRESETS.forEach { (prompt, label) ->
                            AssistChip(
                                onClick = { studioViewModel.generateThreeDScene(customPrompt = prompt, refineExisting = false) },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.prompt,
                        onValueChange = studioViewModel::updateThreeDPrompt,
                        label = { Text("Describe a 3D Three.js scene or refinement...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("three_d_prompt_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { studioViewModel.generateThreeDScene(refineExisting = true) },
                            enabled = !state.isGenerating,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("three_d_refine_btn")
                        ) {
                            Text("Refine Scene")
                        }
                        Button(
                            onClick = { studioViewModel.generateThreeDScene(refineExisting = false) },
                            enabled = !state.isGenerating,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("three_d_generate_btn")
                        ) {
                            if (state.isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("Building...")
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Build 3D")
                            }
                        }
                    }

                    if (state.error != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = state.error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { studioViewModel.generateThreeDScene(refineExisting = false) },
                                    enabled = !state.isGenerating,
                                    modifier = Modifier.testTag("three_d_retry_btn")
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    if (state.showCodeEditor) {
                        OutlinedTextField(
                            value = state.generatedCode,
                            onValueChange = studioViewModel::updateThreeDCode,
                            label = { Text("Three.js JavaScript") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 8,
                            maxLines = 16,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("three_d_code_editor")
                        )
                    }
                }
            }
        }

        val savedScenes = creations.filter { it.type == "THREEJS" }
        if (savedScenes.isNotEmpty()) {
            item {
                Text("Saved 3D Scenes (${savedScenes.size})", style = MaterialTheme.typography.titleMedium)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(savedScenes, key = { it.id }) { scene ->
                        AssistChip(
                            onClick = { studioViewModel.loadSavedThreeDScene(scene) },
                            label = { Text(scene.title) }
                        )
                    }
                }
            }
        }
    }
}

private fun buildThreeJsHtml(userScript: String): String {
    val safeScript = userScript.replace("</script>", "<\\/script>")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"/>
          <style>
            html, body, #stage { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #0b101b; font-family: sans-serif; }
            #err { display: none; position: fixed; inset: 12px; padding: 12px; background: rgba(105,0,5,0.92); color: #ffdad6; border-radius: 10px; font-size: 13px; overflow: auto; }
          </style>
        </head>
        <body>
          <div id="stage"></div>
          <div id="err"></div>
          <script>
            function showThreeErr(msg) {
              var box = document.getElementById('err');
              box.style.display = 'block';
              box.textContent = '3D Scene Error: ' + msg;
            }
            window.onerror = function(msg) { showThreeErr(msg); };
            function loadScriptWithFallback(urls, idx, onReady) {
              if (idx >= urls.length) {
                showThreeErr('Three.js library could not load. Check internet connectivity.');
                return;
              }
              var s = document.createElement('script');
              s.src = urls[idx];
              s.onload = onReady;
              s.onerror = function() { loadScriptWithFallback(urls, idx + 1, onReady); };
              document.head.appendChild(s);
            }
            function startScene() {
              try {
                if (typeof THREE === 'undefined') {
                  throw new Error('Three.js library failed to initialize.');
                }
                $safeScript
              } catch (e) {
                showThreeErr(e && e.message ? e.message : e);
              }
            }
            loadScriptWithFallback([
              'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js',
              'https://cdn.jsdelivr.net/npm/three@0.128.0/build/three.min.js',
              'https://unpkg.com/three@0.128.0/build/three.min.js'
            ], 0, function() {
              loadScriptWithFallback([
                'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js',
                'https://unpkg.com/three@0.128.0/examples/js/controls/OrbitControls.js'
              ], 0, startScene);
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}
