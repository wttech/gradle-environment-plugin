package com.cognifide.gradle.environment.health

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.build.Retry
import com.cognifide.gradle.common.http.HttpClient
import com.cognifide.gradle.common.utils.Formats
import org.apache.http.HttpStatus

class HealthChecker(val common: CommonExtension) {

    private val logger = common.logger

    private val prop = common.prop

    private val checks = mutableListOf<HealthCheck>()

    private var httpOptions: HttpClient.() -> Unit = {
        connectionRetries.apply {
            convention(false)
            prop.boolean("healthChecker.http.connectionRetries")?.let { set(it) }
        }
        connectionTimeout.apply {
            convention(3_000)
            prop.int("healthChecker.http.connectionTimeout")?.let { set(it) }
        }
    }

    var retry = common.retry { afterSquaredSecond(prop.long("healthChecker.retry") ?: 10) }

    fun check(name: String, check: () -> Unit) {
        checks += HealthCheck(name, check)
    }

    fun String.invoke(check: () -> Unit) = check(this, check)

    // Evaluation

    @Suppress("ComplexMethod")
    fun check(verbose: Boolean = true, retry: Retry = this.retry): List<HealthStatus> {
        var all = listOf<HealthStatus>()
        var passed = listOf<HealthStatus>()
        var failed = listOf<HealthStatus>()
        val count by lazy { "${passed.size}/${all.size} (${Formats.percent(passed.size, all.size)})" }

        common.progress(checks.size) {
            try {
                retry.withSleep<Unit, HealthException> { no ->
                    reset()

                    step = when {
                        no > 1 -> "Health rechecking (attempt ${no - 1}/${retry.times}, ${failed.size} ${if (failed.size == 1) "check" else "checks"} failed)"
                        else -> "Health checking"
                    }

                    all = common.parallel.map(checks) { check ->
                        increment(check.name) {
                            check.perform()
                        }
                    }.toList()
                    passed = all.filter { it.succeed }
                    failed = all - passed

                    if (verbose) {
                        logger.info(failed.sortedBy { it.check.name }.joinToString("\n"))
                    }

                    if (failed.isNotEmpty()) {
                        throw HealthException("There are failed environment health checks. Retrying...")
                    }
                }

                logger.lifecycle("Environment health check(s) succeed: $count")
            } catch (e: HealthException) {
                val message = "Environment health check(s) failed. Success ratio: $count:\n" +
                        all.sortedWith(compareBy({ it.succeed }, { it.check.name })).joinToString("\n")
                if (!verbose) {
                    logger.error(message)
                } else {
                    throw HealthException(message)
                }
            }
        }

        return all
    }

    // Shorthand methods for defining health checks

    /**
     * Check URL using specified criteria (HTTP options and e.g text & status code assertions).
     */
    fun http(checkName: String, url: String, statusCode: Int = HttpStatus.SC_OK) {
        http(checkName, url) { respondsWith(statusCode) }
    }

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
