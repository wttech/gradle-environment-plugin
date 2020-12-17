package com.cognifide.gradle.environment.health

class HealthStatus(val check: HealthCheck, val cause: Exception? = null) {

    val succeed: Boolean get() = cause == null

    val status: String get() = (if (succeed) "$check | Succeed" else "$check | ${cause!!.message}").trim()

    override fun toString() = status
}
