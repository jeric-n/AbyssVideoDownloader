package com.abmo.model

import com.abmo.common.Constants.DEFAULT_RETRY_ATTEMPTS

data class RetryPolicy(
    val maxAttempts: Int?
) {
    companion object {
        val DEFAULT = RetryPolicy(DEFAULT_RETRY_ATTEMPTS)
        val INFINITE = RetryPolicy(null)
    }

    val displayValue: String
        get() = maxAttempts?.toString() ?: "inf"
}
