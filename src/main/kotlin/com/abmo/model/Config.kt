package com.abmo.model

import com.abmo.common.Constants.DEFAULT_CONCURRENT_DOWNLOAD_LIMIT
import java.io.File

data class Config(
    val url: String,
    val resolution: String,
    var outputFile: File?,
    val headers: Map<String, String> = emptyMap(),
    val connections: Int = DEFAULT_CONCURRENT_DOWNLOAD_LIMIT,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
)
