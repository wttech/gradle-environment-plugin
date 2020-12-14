package com.cognifide.gradle.environment.health

class HealthStatus(val check: HealthCheck, val cause: Exception? = null) {

    val passed: Boolean get() = cause == null

    val indicator get() = if (passed) "+" else "-"

    val details: String get() = if (cause != null) "$check | ${cause.message}" else check.toString()

    val status: String get() = ("[$indicator] $details").trim()

    override fun toString() = status
}
