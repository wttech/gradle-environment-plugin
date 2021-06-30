package com.cognifide.gradle.environment

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.build.Retry
import com.cognifide.gradle.common.health.HealthChecker
import com.cognifide.gradle.common.health.HealthStatus
import com.cognifide.gradle.common.utils.using
import com.cognifide.gradle.environment.docker.Docker
import com.cognifide.gradle.environment.hosts.HostOptions
import org.gradle.api.Project
import java.io.Serializable

open class EnvironmentExtension(val project: Project) : Serializable {

    val common = CommonExtension.of(project)

    val logger = project.logger

    val obj = common.obj

    val prop = common.prop

    /**
     * Path in which local environment will be stored.
     */
    val rootDir = obj.dir {
        convention(obj.projectDir(".gradle/environment"))
        prop.file("environment.rootDir")?.let { set(it) }
    }

    /**
     * Path for temporary files needed to set up environment like:
     * generated SSL certificates, unpacked archive contents, etc.
     */
    val buildDir = obj.buildDir("environment")

    /**
     * Convention directory for storing environment specific configuration files.
     */
    val sourceDir = obj.dir {
        convention(obj.projectDir("src/environment"))
        prop.file("environment.sourceDir")?.let { set(it) }
    }

    /**
     * Configures Docker related options.
     */
    fun docker(options: Docker.() -> Unit) = docker.using(options)

    val docker by lazy { Docker(this) }

    /**
     * Configures environment service health checks.
     */
    fun checks(options: HealthChecker.() -> Unit) = healthChecks(options)

    fun healthChecks(options: HealthChecker.() -> Unit) = healthChecker.using(options)

    val healthChecker by lazy { HealthChecker(common) }

    /**
     * Defines hosts to be appended to system specific hosts file.
     */
    fun hosts(options: HostOptions.() -> Unit) = hosts.using(options)

    val hosts by lazy { HostOptions(this) }

    val created: Boolean get() = rootDir.get().asFile.exists()

    val running: Boolean get() = docker.running

    val up: Boolean get() = docker.up

    val upToDate: Boolean get() = docker.upToDate

    fun resolve() {
        docker.containers.resolve()
    }

    fun up() {
        if (up) {
            logger.info("Environment is already running!")
            return
        }

        logger.info("Turning on: $this")
        docker.init()
        docker.up()
        docker.checkUp()

        logger.info("Turned on: $this")
    }

    fun down() {
        if (!running) {
            logger.info("Environment is not yet running!")
            return
        }

        logger.info("Turning off: $this")
        docker.down()
        logger.info("Turned off: $this")
    }

    fun restart() {
        down()
        up()
    }

    fun destroy() {
        logger.info("Destroying: $this")
        rootDir.get().asFile.deleteRecursively()
        logger.info("Destroyed: $this")
    }

    fun check(verbose: Boolean = true, retry: Retry? = null): List<HealthStatus> {
        if (!up) {
            throw EnvironmentException("Cannot check environment as it is not up!")
        }

        logger.info("Checking $this")

        return when {
            retry != null -> healthChecker.start(verbose, retry)
            else -> healthChecker.start(verbose)
        }
    }

    fun reload() {
        if (!up) {
            throw EnvironmentException("Cannot reload environment as it is not up!")
        }

        logger.info("Reloading $this")
        docker.reload()
        logger.info("Reloaded $this")
    }

    override fun toString(): String = "Environment(root=${rootDir.get()}, up=$up)"

    companion object {
        const val NAME = "environment"

        fun of(project: Project): EnvironmentExtension {
            return project.extensions.findByType(EnvironmentExtension::class.java)
                    ?: throw EnvironmentException("${project.displayName.capitalize()} must have plugin applied: ${EnvironmentPlugin.ID}")
        }
    }
}

val Project.environment get() = EnvironmentExtension.of(this)
