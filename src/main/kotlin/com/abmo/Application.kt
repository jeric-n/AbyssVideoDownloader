package com.abmo

import com.abmo.common.Constants
import com.abmo.common.Constants.ABYSS_BASE_URL
import com.abmo.common.Logger
import com.abmo.model.Config
import com.abmo.model.video.Source
import com.abmo.services.ProviderDispatcher
import com.abmo.services.VideoDownloader
import com.abmo.util.CliArguments
import com.abmo.util.extractReferer
import com.abmo.util.isValidPath
import com.abmo.util.isValidUrl
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.system.exitProcess

class Application(private val args: Array<String>) : KoinComponent {

    private val videoDownloader: VideoDownloader by inject()
    private val providerDispatcher: ProviderDispatcher by inject()
    private val cliArguments: CliArguments by inject { parametersOf(args) }

    suspend fun run() {
        val outputFileName = cliArguments.getOutputFileName()
        val userHeaders = cliArguments.getHeaders().orEmpty()
        val numberOfConnections = cliArguments.getParallelConnections()
        val videoRequests = cliArguments.getVideoIdsOrUrlsWithResolutions()
        Constants.VERBOSE = cliArguments.isVerboseEnabled()

        if (outputFileName != null && !isValidPath(outputFileName)) {
            exitProcess(0)
        }

        if (outputFileName != null && videoRequests.size > 1) {
            Logger.error("A single output file cannot be reused for multiple videos. Omit '-o' or download one video at a time.")
            exitProcess(0)
        }

        videoRequests.forEach { (videoUrl, resolution) ->
            processVideoRequest(videoUrl, resolution, outputFileName, userHeaders, numberOfConnections)
            if (videoRequests.size > 1) {
                println("-----------------------------------------")
            }
        }
    }

    private suspend fun processVideoRequest(
        videoUrl: String,
        resolution: String,
        outputFileName: String?,
        userHeaders: Map<String, String>,
        numberOfConnections: Int
    ) {
        val provider = providerDispatcher.getProviderForUrl(videoUrl)
        val videoId = provider.getVideoID(videoUrl)

        if (videoId.isNullOrBlank()) {
            Logger.error("Could not extract a video ID from '$videoUrl'. The source page may have changed or is unsupported.")
            return
        }

        val headers = buildRequestHeaders(videoUrl, userHeaders)
        val abyssUrl = "$ABYSS_BASE_URL/?v=$videoId"

        try {
            val videoMetadata = videoDownloader.getVideoMetaData(abyssUrl, headers)
            val selectedSource = selectSource(videoMetadata.sources, resolution)

            if (selectedSource?.label == null) {
                val availableResolutions = videoMetadata.sources
                    ?.mapNotNull { it?.label }
                    ?.distinct()
                    ?.joinToString(", ")
                    ?: "none"
                Logger.error("No downloadable source was found for '$videoId'. Available resolutions: $availableResolutions.")
                return
            }

            val outputFile = resolveOutputFile(outputFileName, videoId, selectedSource.label)
            val config = Config(
                url = abyssUrl,
                resolution = selectedSource.label,
                outputFile = outputFile,
                headers = headers,
                connections = numberOfConnections
            )

            Logger.info("Processing video '$videoId' at resolution ${selectedSource.label}...")
            videoDownloader.downloadSegmentsInParallel(config, videoMetadata)
        } catch (e: Exception) {
            Logger.error("Failed to process '$videoId': ${e.message ?: "Unexpected error."}")
            Logger.debug(e.stackTraceToString(), isError = true)
        }
    }

    private fun buildRequestHeaders(videoUrl: String, userHeaders: Map<String, String>): Map<String, String> {
        val defaultHeaders = if (videoUrl.isValidUrl()) {
            videoUrl.extractReferer()?.let { mapOf("Referer" to it) }.orEmpty()
        } else {
            emptyMap()
        }

        return defaultHeaders + userHeaders
    }

    private fun selectSource(sources: List<Source?>?, resolution: String): Source? {
        val availableSources = sources.orEmpty()
            .filterNotNull()
            .filter { !it.label.isNullOrBlank() && it.size != null }
            .sortedBy { it.size }

        if (availableSources.isEmpty()) {
            return null
        }

        return when (resolution.lowercase()) {
            "l" -> availableSources.first()
            "m" -> availableSources[(availableSources.size - 1) / 2]
            "h" -> availableSources.last()
            else -> availableSources.last()
        }
    }

    private fun resolveOutputFile(outputFileName: String?, videoId: String, resolutionLabel: String): File {
        if (outputFileName != null) {
            return File(outputFileName)
        }

        val defaultFileName = "${videoId}_${resolutionLabel}_${System.currentTimeMillis()}.mp4"
        Logger.warn("No output file specified. Saving to the current directory as '$defaultFileName'.")
        return File(".", defaultFileName)
    }
}
