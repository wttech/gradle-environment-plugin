package com.cognifide.gradle.environment.health

class HealthStatus(val check: HealthCheck, val cause: Exception? = null) {

    val passed: Boolean get() = cause == null

    val status: String
        get() = when (cause) {
            null -> "OK"
            else -> "FAIL | ${cause.message}"
        }

    override fun toString() = "$check | $status".trim()
}
