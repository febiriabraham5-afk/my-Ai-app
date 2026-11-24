package com.example

import com.example.ai.LuminaryAiService
import org.junit.Assert.*
import org.junit.Test

/**
 * Local JVM unit tests for Luminary AI utility helpers.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun extractFencedCode_stripsMarkdownAndSurroundingProse() {
    val raw = """
      Here is your Three.js scene:
      ```javascript
      const scene = new THREE.Scene();
      console.log('ready');
      ```
      Enjoy orbiting!
    """.trimIndent()
    val extracted = LuminaryAiService.extractFencedCode(raw, listOf("javascript", "js"))
    assertEquals("const scene = new THREE.Scene();\nconsole.log('ready');", extracted)
  }

  @Test
  fun extractFencedCode_handlesPlainCodeWithoutFences() {
    val raw = "SELECT * FROM creations ORDER BY createdAt DESC;"
    assertEquals(raw, LuminaryAiService.extractFencedCode(raw, listOf("sql")))
  }

  @Test
  fun extractFencedCode_extractsInlineScriptTagForJavascript() {
    val raw = """
      <script src="https://example.com/three.min.js"></script>
      <script>
      const scene = new THREE.Scene();
      </script>
    """.trimIndent()
    val extracted = LuminaryAiService.extractFencedCode(raw, listOf("javascript", "js"))
    assertEquals("const scene = new THREE.Scene();", extracted)
  }

  @Test
  fun transientHighDemandError_detects503AndNestedCause() {
    val wrapped = RuntimeException(
      "Wrapper",
      IllegalStateException("This model is currently experiencing high demand. Spikes in demand are usually temporary. (503 UNAVAILABLE)")
    )
    assertTrue(LuminaryAiService.isTransientHighDemandError(wrapped))
    assertFalse(LuminaryAiService.isQuotaError(wrapped))
  }

  @Test
  fun quotaError_detects429AndBillingMessages() {
    val quotaErr = IllegalStateException("RESOURCE_EXHAUSTED (429): Quota exceeded")
    assertTrue(LuminaryAiService.isQuotaError(quotaErr))
    assertFalse(LuminaryAiService.isTransientHighDemandError(quotaErr))
  }
}
