package com.cognifide.gradle.environment.health

import com.cognifide.gradle.common.http.HttpClient
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.EnvironmentException
import org.apache.http.HttpStatus

class HealthChecker(val environment: EnvironmentExtension) {

    private val common = environment.common

    private val logger = common.logger

    private val prop = common.prop

    private val checks = mutableListOf<HealthCheck>()

    private var httpOptions: HttpClient.() -> Unit = {
        connectionRetries.apply {
            convention(false)
            prop.boolean("environment.health.http.connectionRetries")?.let { set(it) }
        }
        connectionTimeout.apply {
            convention(1000)
            prop.int("environment.health.http.connectionTimeout")?.let { set(it) }
        }
    }

    var retry = common.retry { afterSquaredSecond(prop.long("environment.health.retry") ?: 3) }

    fun check(name: String, check: () -> Unit) {
        checks += HealthCheck(name, check)
    }

    fun String.invoke(check: () -> Unit) = check(this, check)

    // Evaluation

    @Suppress("ComplexMethod")
    fun check(verbose: Boolean = true): List<HealthStatus> {
        var all = listOf<HealthStatus>()
        var passed = listOf<HealthStatus>()
        var failed = listOf<HealthStatus>()
        val count by lazy { "${passed.size}/${all.size} (${Formats.percent(passed.size, all.size)})" }

        common.progress(checks.size) {
            try {
                retry.withSleep<Unit, EnvironmentException> { no ->
                    reset()

                    step = when {
                        no > 1 -> "Health rechecking (attempt $no/${retry.times}, ${failed.size} ${if (failed.size == 1) "check" else "checks"} failed)"
                        else -> "Health checking"
                    }

                    all = common.parallel.map(checks) { check ->
                        increment(check.name) {
                            check.perform()
                        }
                    }.toList()
                    passed = all.filter { it.passed }
                    failed = all - passed

                    if (failed.isNotEmpty()) {
                        throw EnvironmentException("There are failed environment health checks. Retrying...")
                    }
                }

                val message = "Environment health check(s) succeed: $count"
                if (!verbose) {
                    logger.lifecycle(message)
                } else {
                    logger.info(message)
                }
            } catch (e: EnvironmentException) {
                val message = "Environment health check(s) failed. Success ratio: $count:\n${all.joinToString("\n")}"
                if (!verbose) {
                    logger.error(message)
                } else {
                    throw EnvironmentException(message)
                }
            }
        }

        return all
    }

    // Shorthand methods for defining health checks

    /**
     * Check URL using specified criteria (HTTP options and e.g text & status code assertions).
     */
    fun http(checkName: String, url: String, containedText: String, statusCode: Int = HttpStatus.SC_OK) {
        http(checkName, url) { containsText(containedText, statusCode) }
    }

    fun http(checkName: String, url: String, criteria: HttpCheck.() -> Unit) = check(checkName) {
        common.http {
            val check = HttpCheck(url).apply(criteria)

            apply(httpOptions)
            apply(check.options)

            request(check.method, check.url) { response ->
                check.checks.forEach { it(response) }
            }
        }
    }

    fun http(options: HttpClient.() -> Unit) {
        this.httpOptions = options
    }
}
