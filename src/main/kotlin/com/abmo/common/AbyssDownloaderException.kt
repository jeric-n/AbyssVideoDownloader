package com.abmo.common

class AbyssDownloaderException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
