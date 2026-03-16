import com.abmo.util.CliArguments
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliArgumentsTest {

    @Test
    fun `getHeaders parses valid headers and ignores malformed values`() {
        val arguments = CliArguments(
            arrayOf(
                "video-id",
                "-H", "Authorization: Bearer token",
                "--header", "InvalidHeader",
                "--header", "Referer: https://example.com"
            )
        )

        assertEquals(
            mapOf(
                "Authorization" to "Bearer token",
                "Referer" to "https://example.com"
            ),
            arguments.getHeaders()
        )
    }

    @Test
    fun `getOutputFileName supports short and long flags`() {
        assertEquals("video.mp4", CliArguments(arrayOf("id", "-o", "video.mp4")).getOutputFileName())
        assertEquals("video.mp4", CliArguments(arrayOf("id", "--output", "video.mp4")).getOutputFileName())
        assertNull(CliArguments(arrayOf("id")).getOutputFileName())
    }

    @Test
    fun `getRetryPolicy parses finite and infinite retry values`() {
        assertEquals(5, CliArguments(arrayOf("id", "--retry", "5")).getRetryPolicy().maxAttempts)
        assertNull(CliArguments(arrayOf("id", "--retry", "inf")).getRetryPolicy().maxAttempts)
    }

    @Test
    fun `getVideoIdsOrUrlsWithResolutions ignores flag arguments and reads files`(@TempDir tempDir: Path) {
        val inputFile = tempDir.resolve("videos.txt")
        inputFile.writeText(
            """
            id1
            id2 m
            # comment
            https://example.com/video l
            """.trimIndent()
        )

        val arguments = CliArguments(
            arrayOf(
                inputFile.toString(),
                "-c", "5",
                "--retry", "inf",
                "-o", tempDir.resolve("output.mp4").toString(),
                "--verbose"
            )
        )

        assertEquals(
            listOf(
                "id1" to "h",
                "id2" to "m",
                "https://example.com/video" to "l"
            ),
            arguments.getVideoIdsOrUrlsWithResolutions()
        )
        assertTrue(arguments.getRetryPolicy().maxAttempts == null)
    }
}
