package com.example.ui

import android.graphics.Bitmap
import com.example.ai.ImageAspectOption
import com.example.ai.LiveVoiceStatus
import com.example.ai.VoiceTranscriptLine
import com.example.data.SqlExecutionResult
import com.example.data.SqlTableInfo

enum class LuminaryTab(val label: String) {
    CHAT("Chatbot"),
    CREATE("Create"),
    VOICE("Voice"),
    THREE_D("3D"),
    SQL("SQL")
}

data class ImageStudioUiState(
    val prompt: String = "",
    val sourceImage: Bitmap? = null,
    val aspectOption: ImageAspectOption = ImageAspectOption.SQUARE,
    val useProQuality: Boolean = false,
    val isGenerating: Boolean = false,
    val generatedImage: Bitmap? = null,
    val generatedPath: String? = null,
    val caption: String = "",
    val modelUsed: String = "",
    val error: String? = null
)

data class VideoStudioUiState(
    val prompt: String = "",
    val sourceImage: Bitmap? = null,
    val aspectRatio: String = "16:9",
    val isGenerating: Boolean = false,
    val progressMessage: String = "",
    val videoFilePath: String? = null,
    val modelUsed: String = "",
    val error: String? = null
)

data class VoiceStudioUiState(
    val liveStatus: LiveVoiceStatus = LiveVoiceStatus.IDLE,
    val liveStatusMessage: String? = null,
    val selectedVoice: String = "FENRIR",
    val liveTranscripts: List<VoiceTranscriptLine> = emptyList(),
    val isRecordingTranscript: Boolean = false,
    val recordingSeconds: Int = 0,
    val pendingAudioName: String? = null,
    val hasPendingAudio: Boolean = false,
    val includeTimestamps: Boolean = true,
    val summarizeTranscript: Boolean = true,
    val isTranscribing: Boolean = false,
    val transcriptResult: String = "",
    val transcribeError: String? = null,
    val isPushToTalkRecording: Boolean = false,
    val isVoiceTurnProcessing: Boolean = false
)

data class ThreeDStudioUiState(
    val prompt: String = "",
    val generatedCode: String = DEFAULT_THREEJS_SCENE,
    val sceneVersion: Int = 1,
    val autoRotate: Boolean = true,
    val wireframe: Boolean = false,
    val showCodeEditor: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null
)

data class SqlStudioUiState(
    val tables: List<SqlTableInfo> = emptyList(),
    val selectedTable: String? = null,
    val sqlQuery: String = "SELECT id, type, title, createdAt FROM creations ORDER BY createdAt DESC LIMIT 25;",
    val nlPrompt: String = "",
    val isRunningSql: Boolean = false,
    val isGeneratingSql: Boolean = false,
    val lastResult: SqlExecutionResult? = null
)

val DEFAULT_THREEJS_SCENE = """
const stage = document.getElementById('stage');
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0b101b);
scene.fog = new THREE.FogExp2(0x0b101b, 0.045);

const camera = new THREE.PerspectiveCamera(50, window.innerWidth / window.innerHeight, 0.1, 100);
camera.position.set(0, 2.4, 6.2);
const initialCamPos = camera.position.clone();

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
stage.appendChild(renderer.domElement);

const controls = new THREE.OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.autoRotate = true;
controls.autoRotateSpeed = 1.4;

scene.add(new THREE.AmbientLight(0x8bb8ff, 0.65));
const goldLight = new THREE.PointLight(0xf5b041, 3.2, 24);
goldLight.position.set(3, 4, 3);
scene.add(goldLight);
const cyanLight = new THREE.PointLight(0x7fd1dc, 2.5, 24);
cyanLight.position.set(-3, -2, 3);
scene.add(cyanLight);

const prism = new THREE.Mesh(
  new THREE.OctahedronGeometry(1.35, 0),
  new THREE.MeshPhysicalMaterial({
    color: 0xf5b041,
    emissive: 0x3b2100,
    metalness: 0.25,
    roughness: 0.15,
    clearcoat: 1.0
  })
);
scene.add(prism);

const ring = new THREE.Mesh(
  new THREE.TorusGeometry(2.3, 0.055, 20, 96),
  new THREE.MeshStandardMaterial({ color: 0x7fd1dc, emissive: 0x164a52, metalness: 0.7, roughness: 0.2 })
);
ring.rotation.x = Math.PI * 0.36;
scene.add(ring);

const starGeo = new THREE.BufferGeometry();
const starCount = 420;
const positions = new Float32Array(starCount * 3);
for (let i = 0; i < starCount * 3; i++) positions[i] = (Math.random() - 0.5) * 28;
starGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({ color: 0xfff3d6, size: 0.07 }));
scene.add(stars);

window.__luminaryScene = {
  scene, camera, renderer, controls,
  setAutoRotate(v) { controls.autoRotate = v; },
  setWireframe(v) {
    scene.traverse(o => {
      if (o.isMesh && o.material) {
        const mats = Array.isArray(o.material) ? o.material : [o.material];
        mats.forEach(m => { m.wireframe = v; });
      }
    });
  },
  resetCamera() { camera.position.copy(initialCamPos); controls.target.set(0, 0, 0); controls.update(); }
};

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

const clock = new THREE.Clock();
(function animate() {
  requestAnimationFrame(animate);
  const t = clock.getElapsedTime();
  prism.rotation.y = t * 0.65;
  prism.position.y = Math.sin(t * 1.5) * 0.18;
  ring.rotation.z = t * 0.35;
  stars.rotation.y = t * 0.04;
  controls.update();
  renderer.render(scene, camera);
})();
""".trimIndent()
