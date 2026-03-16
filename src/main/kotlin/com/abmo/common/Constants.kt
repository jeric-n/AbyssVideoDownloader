package com.abmo.common

object Constants {

    /**
     * The default maximum number of concurrent downloads allowed.
     */
    const val DEFAULT_CONCURRENT_DOWNLOAD_LIMIT = 4
    const val DEFAULT_RETRY_ATTEMPTS = 3
    const val DEFAULT_RETRY_DELAY_MS = 1_000L
    const val DEFAULT_FRAGMENT_SIZE_IN_BYTES = 2_097_152L

    /**
     * Toggle for verbose logging.
     * Set to `true` to enable detailed logs, `false` to disable.
     */
    var VERBOSE = false

    const val ABYSS_BASE_URL = "https://abysscdn.com"
    val abyssDefaultHeaders = mapOf(
        "Referer" to "$ABYSS_BASE_URL/",
        "Origin" to ABYSS_BASE_URL
    )

}
