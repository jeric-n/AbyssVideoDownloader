package com.abmo.services

import com.abmo.common.AbyssDownloaderException
import com.abmo.common.Constants.DEFAULT_RETRY_ATTEMPTS
import com.abmo.common.Constants.DEFAULT_RETRY_DELAY_MS
import com.abmo.common.Logger
import com.abmo.model.HttpResponse
import com.github.zhkl0228.impersonator.ImpersonatorFactory
import com.mashape.unirest.http.Unirest
import okhttp3.OkHttpClient
import okhttp3.OkHttpClientFactory
import okhttp3.Request

class HttpClientManager {

    private val isWindows by lazy { isWindowsOS() }
    private val okHttpClient: OkHttpClient by lazy { createImpersonatedClient() }

    fun makeHttpRequest(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        Logger.debug("Initiating HTTP request to $url")
        return executeWithRetry("fetch metadata from $url") {
            if (isWindows) {
                makeTextRequestWithUnirest(url, headers)
            } else {
                makeTextRequestWithOkHttp(url, headers)
            }
        }
    }

    fun downloadBinary(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        Logger.debug("Downloading binary content from $url")
        return executeWithRetry("download segment from $url") {
            if (isWindows) {
                makeBinaryRequestWithUnirest(url, headers)
            } else {
                makeBinaryRequestWithOkHttp(url, headers)
            }
        }
    }

    private fun isWindowsOS(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        return osName.contains("windows")
    }

    private fun createImpersonatedClient(): OkHttpClient {
        val api = ImpersonatorFactory.ios()
        api.newSSLContext(null, null)
        val factory = OkHttpClientFactory.create(api)
        return factory.newHttpClient()
    }

    private fun makeTextRequestWithOkHttp(url: String, headers: Map<String, String>): HttpResponse {
        val request = Request.Builder()
            .url(url)
            .applyHeaders(headers)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            validateStatus(url, response.code, body)
            return HttpResponse(body = body, statusCode = response.code)
        }
    }

    private fun makeBinaryRequestWithOkHttp(url: String, headers: Map<String, String>): ByteArray {
        val request = Request.Builder()
            .url(url)
            .applyHeaders(headers)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw AbyssDownloaderException("Received an empty response body from $url.")
            validateStatus(url, response.code, null)
            return body.bytes()
        }
    }

    private fun makeTextRequestWithUnirest(url: String, headers: Map<String, String>): HttpResponse {
        return try {
            val response = Unirest.get(url)
                .headers(headers)
                .asString()

            Logger.debug("Received response with status ${response.status}", response.status !in 200..299)
            validateStatus(url, response.status, response.body)
            HttpResponse(body = response.body, statusCode = response.status)
        } catch (e: Exception) {
            throw AbyssDownloaderException("Failed to request $url: ${e.message}", e)
        }
    }

    private fun makeBinaryRequestWithUnirest(url: String, headers: Map<String, String>): ByteArray {
        return try {
            val response = Unirest.get(url)
                .headers(headers)
                .asBinary()

            Logger.debug("Received response with status ${response.status}", response.status !in 200..299)
            validateStatus(url, response.status, null)
            response.rawBody.use { it.readBytes() }
        } catch (e: Exception) {
            throw AbyssDownloaderException("Failed to download binary content from $url: ${e.message}", e)
        }
    }

    private fun validateStatus(url: String, statusCode: Int, body: String?) {
        if (statusCode in 200..299) {
            return
        }

        val message = buildString {
            append("Request to $url failed with HTTP $statusCode")
            describeStatusCode(statusCode)?.let { append(" ($it)") }
            if (!body.isNullOrBlank()) {
                append(". Response preview: ")
                append(body.take(160).replace('\n', ' ').trim())
            }
        }

        throw AbyssDownloaderException(message)
    }

    private fun describeStatusCode(statusCode: Int): String? {
        return when (statusCode) {
            400 -> "the request was rejected as invalid"
            401 -> "authentication is required"
            403 -> "access was forbidden, usually because the required headers are missing or expired"
            404 -> "the requested resource was not found"
            429 -> "too many requests were sent"
            in 500..599 -> "the remote server returned an internal error"
            else -> null
        }
    }

    private fun <T> executeWithRetry(
        operationName: String,
        maxAttempts: Int = DEFAULT_RETRY_ATTEMPTS,
        initialDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
        block: () -> T
    ): T {
        require(maxAttempts > 0) { "maxAttempts must be greater than zero." }

        var delayMs = initialDelayMs
        var lastError: Exception? = null

        for (attemptIndex in 0 until maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                val attempt = attemptIndex + 1
                if (attempt == maxAttempts) {
                    break
                }

                Logger.warn(
                    "$operationName failed on attempt $attempt/$maxAttempts: ${e.message}. Retrying..."
                )
                Thread.sleep(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(8_000L)
            }
        }

        throw AbyssDownloaderException(
            "$operationName failed after $maxAttempts attempts. ${lastError?.message ?: "No additional details."}",
            lastError
        )
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder = apply {
        headers.forEach { (key, value) -> header(key, value) }
    }
}
