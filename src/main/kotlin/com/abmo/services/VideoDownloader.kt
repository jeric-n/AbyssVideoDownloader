package com.abmo.services

import com.abmo.common.AbyssDownloaderException
import com.abmo.common.Constants.DEFAULT_FRAGMENT_SIZE_IN_BYTES
import com.abmo.common.Constants.abyssDefaultHeaders
import com.abmo.common.Logger
import com.abmo.crypto.CryptoHelper
import com.abmo.model.Config
import com.abmo.model.Datas
import com.abmo.model.RetryPolicy
import com.abmo.model.SimpleVideo
import com.abmo.model.video.Mp4
import com.abmo.model.video.Video
import com.abmo.model.video.toSimpleVideo
import com.abmo.util.displayProgressBar
import com.abmo.util.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class VideoDownloader: KoinComponent {

    private val cryptoHelper: CryptoHelper by inject()
    private val httpClientManager: HttpClientManager by inject()

    suspend fun downloadSegmentsInParallel(config: Config, videoMetadata: Mp4?) {
        val simpleVideo = videoMetadata
            ?.toSimpleVideo(config.resolution)
            ?.takeIf { it.size != null && it.url != null && it.resId != null && it.md5_id != null }
            ?: throw AbyssDownloaderException(
                "Resolution '${config.resolution}' is not available or the metadata is incomplete."
            )

        val fragmentSize = simpleVideo.partSize?.takeIf { it > 0 } ?: DEFAULT_FRAGMENT_SIZE_IN_BYTES
        val segmentTokens = generateSegmentTokens(simpleVideo, fragmentSize)
        if (segmentTokens.isEmpty()) {
            throw AbyssDownloaderException("No downloadable segments were generated for '${simpleVideo.slug}'.")
        }

        val resumeState = initializeDownloadTempDir(config, simpleVideo, segmentTokens.size, fragmentSize)
        val segmentsToDownload = segmentTokens.filterKeys { it in resumeState.missingSegments }
        val semaphore = Semaphore(config.connections)
        val totalSegments = segmentTokens.size
        val mediaSize = simpleVideo.size ?: totalSegments * fragmentSize
        val downloadedSegments = AtomicInteger(resumeState.existingSegments.size)
        val totalBytesDownloaded = AtomicLong(resumeState.existingBytes)
        val startTime = System.currentTimeMillis()

        coroutineScope {
            val downloadJobs = segmentsToDownload.map { (index, segmentToken) ->
                val segmentUrl = "${simpleVideo.url}/sora/${simpleVideo.size}/$segmentToken"
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val segmentBytes = requestSegment(
                            segmentUrl,
                            index,
                            abyssDefaultHeaders + config.headers,
                            config.retryPolicy
                        )
                        File(resumeState.tempFolder, "segment_$index").writeBytes(segmentBytes)
                        totalBytesDownloaded.addAndGet(segmentBytes.size.toLong())
                    }
                    downloadedSegments.incrementAndGet()
                }
            }

            val progressJob = launch {
                var lastUpdateTime = System.currentTimeMillis()
                while (isActive) {
                    lastUpdateTime = displayProgressBar(
                        mediaSize,
                        totalSegments,
                        totalBytesDownloaded.get(),
                        downloadedSegments.get(),
                        startTime,
                        lastUpdateTime
                    )
                    delay(1_000)
                }
            }

            try {
                downloadJobs.awaitAll()
            } finally {
                progressJob.cancel()
            }
        }

        println()
        Logger.debug("All segments have been downloaded successfully!")
        Logger.info("Merging segments into MP4 file...")
        config.outputFile?.let { mergeSegmentsIntoMp4File(resumeState.tempFolder, it) }
    }

    fun getVideoMetaData(
        url: String,
        headers: Map<String, String> = emptyMap(),
        retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
    ): Mp4 {
        val response = httpClientManager.makeHttpRequest(url, headers, retryPolicy)
        val encryptedData = response.body
            ?: throw AbyssDownloaderException("The metadata response for $url did not include a body.")
        return parseEncryptedMp4MetadataFromHtml(encryptedData)
    }

    private fun parseEncryptedMp4MetadataFromHtml(html: String): Mp4 {
        val jsCode = Jsoup.parse(html)
            .select("script")
            .find { it.html().contains("datas") }
            ?.html()
            ?: throw AbyssDownloaderException("No encoded media metadata was found in the provided HTML.")

        val datasRegex = Regex("""const\s+datas\s*=\s*"([^"]*)"""")
        val datas = datasRegex.find(jsCode)?.groups?.get(1)?.value
            ?: throw AbyssDownloaderException("The encoded metadata payload could not be extracted from the page.")

        val decodedDatas = try {
            String(Base64.getDecoder().decode(datas), Charsets.ISO_8859_1)
        } catch (e: IllegalArgumentException) {
            throw AbyssDownloaderException("The metadata payload is not valid Base64 data.", e)
        }

        val mediaMetadata = try {
            decodedDatas.toObject<Datas>()
        } catch (e: Exception) {
            throw AbyssDownloaderException("The metadata payload could not be parsed.", e)
        }

        val encryptedMediaMetadata = mediaMetadata.media
            ?: throw AbyssDownloaderException("The metadata payload did not contain encrypted media data.")

        val mediaKey = "${mediaMetadata.user_id}:${mediaMetadata.slug}:${mediaMetadata.md5_id}"
        val decryptionKey = cryptoHelper.getKey(mediaKey).toByteArray()

        return try {
            cryptoHelper.decryptString(encryptedMediaMetadata, decryptionKey)
                .toObject<Video>()
                .mp4
                ?.copy(slug = mediaMetadata.slug, md5_id = mediaMetadata.md5_id)
                ?: throw AbyssDownloaderException("No MP4 sources were found in the decrypted metadata.")
        } catch (e: AbyssDownloaderException) {
            throw e
        } catch (e: Exception) {
            throw AbyssDownloaderException("Failed to decrypt the media metadata.", e)
        }
    }

    private fun mergeSegmentsIntoMp4File(segmentFolderPath: File, output: File) {
        val segmentFiles = segmentFolderPath.listFiles { file -> file.name.startsWith("segment_") }
            ?.toList()
            ?.sortedBy { it.name.removePrefix("segment_").toIntOrNull() }
            ?: emptyList()

        if (segmentFiles.isEmpty()) {
            throw AbyssDownloaderException("No downloaded segments were found in ${segmentFolderPath.absolutePath}.")
        }

        output.outputStream().buffered().use { outputStream ->
            segmentFiles.forEach { segment -> outputStream.write(segment.readBytes()) }
        }

        Logger.success("Segments merged successfully.")

        segmentFiles.forEach { file ->
            if (!file.delete()) {
                Logger.warn("Failed to delete temporary segment '${file.absolutePath}'.")
            }
        }

        if (!segmentFolderPath.delete()) {
            Logger.warn("Failed to delete temporary folder '${segmentFolderPath.absolutePath}'.")
        }
    }

    private fun initializeDownloadTempDir(
        config: Config,
        simpleVideo: SimpleVideo,
        totalSegments: Int,
        fragmentSize: Long
    ): ResumeState {
        val tempFolderName = "temp_${simpleVideo.slug}_${simpleVideo.label}"
        val tempFolder = File(config.outputFile?.parentFile, tempFolderName)

        if (!tempFolder.exists()) {
            Logger.info("Creating temporary folder $tempFolderName")
            println()
            tempFolder.mkdirs()
            return ResumeState(tempFolder, (0 until totalSegments).toSet(), emptySet(), 0L)
        }

        Logger.info("Resuming download from temporary folder: $tempFolderName.")
        println()

        val existingSegments = mutableSetOf<Int>()
        var existingBytes = 0L

        tempFolder.listFiles { file -> file.isFile && file.name.matches(Regex("segment_\\d+")) }
            ?.forEach { file ->
                val index = file.name.removePrefix("segment_").toIntOrNull() ?: return@forEach
                if (index !in 0 until totalSegments) {
                    file.delete()
                    return@forEach
                }
                val isLastSegment = index == totalSegments - 1
                val isValidSize = file.length() == fragmentSize || (isLastSegment && file.length() in 1 until fragmentSize)

                if (isValidSize) {
                    existingSegments += index
                    existingBytes += file.length()
                } else {
                    file.delete()
                }
            }

        val missingSegments = (0 until totalSegments).filterNot(existingSegments::contains).toSet()
        return ResumeState(tempFolder, missingSegments, existingSegments, existingBytes)
    }

    private fun generateRanges(size: Long, step: Long): List<LongRange> {
        if (size <= 0) {
            return emptyList()
        }

        val ranges = mutableListOf<LongRange>()
        var start = 0L

        while (start < size) {
            val end = minOf(start + step, size)
            ranges += start until end
            start = end
        }

        return ranges
    }

    private fun generateSegmentTokens(simpleVideo: SimpleVideo, fragmentSize: Long): Map<Int, String> {
        Logger.debug("Generating segment request tokens.")
        val size = simpleVideo.size
            ?: throw AbyssDownloaderException("The selected video source does not include its size.")
        val md5Id = simpleVideo.md5_id
            ?: throw AbyssDownloaderException("The selected video source does not include its media id.")
        val resId = simpleVideo.resId
            ?: throw AbyssDownloaderException("The selected video source does not include its resolution id.")
        val encryptionKey = cryptoHelper.getKey(size)

        val fragmentList = generateRanges(size, fragmentSize).mapIndexed { index, _ ->
            val path = "/mp4/$md5Id/$resId/$size/$fragmentSize/$index"
            val encryptedBody = cryptoHelper.encryptAESCTR(path, encryptionKey)
            index to doubleEncodeToBase64(encryptedBody)
        }.toMap()

        Logger.debug("${fragmentList.size} request tokens generated.")
        return fragmentList
    }

    private fun doubleEncodeToBase64(input: String): String {
        val firstPass = Base64.getEncoder()
            .encodeToString(input.toByteArray(Charsets.ISO_8859_1))
            .replace("=", "")

        return Base64.getEncoder()
            .encodeToString(firstPass.toByteArray())
            .replace("=", "")
    }

    private fun requestSegment(
        url: String,
        index: Int,
        headers: Map<String, String>,
        retryPolicy: RetryPolicy
    ): ByteArray {
        Logger.debug("[$index] Starting segment request to $url")
        return try {
            val segmentBytes = httpClientManager.downloadBinary(url, headers, retryPolicy)
            Logger.debug("[$index] Downloaded ${segmentBytes.size} bytes.")
            segmentBytes
        } catch (e: Exception) {
            throw AbyssDownloaderException("Failed to download segment $index from $url. ${e.message}", e)
        }
    }

    private data class ResumeState(
        val tempFolder: File,
        val missingSegments: Set<Int>,
        val existingSegments: Set<Int>,
        val existingBytes: Long
    )
}
