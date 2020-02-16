package com.cognifide.gradle.environment

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.environment.docker.Docker
import com.cognifide.gradle.environment.health.HealthChecker
import com.cognifide.gradle.environment.health.HealthStatus
import com.cognifide.gradle.environment.hosts.HostOptions
import org.gradle.api.Project
import java.io.Serializable

open class EnvironmentExtension(val project: Project) : Serializable {

    val common = CommonExtension.of(project)

    val logger = project.logger

    val obj = common.obj

    val prop = common.prop

    /**
     * Path in which local AEM environment will be stored.
     */
    val rootDir = obj.dir {
        convention(obj.projectDir(".environment"))
        prop.file("environment.rootDir")?.let { set(it) }
    }

    /**
     * Convention directory for storing environment specific configuration files.
     */
    val sourceDir = obj.dir {
        convention(obj.projectDir("src/environment"))
        prop.file("environment.sourceDir")?.let { set(it) }
    }

    val docker = Docker(this)

    fun docker(options: Docker.() -> Unit) {
        docker.apply(options)
    }

    var healthChecker = HealthChecker(this)

    val hosts = HostOptions(this)

    val created: Boolean get() = rootDir.get().asFile.exists()

    val running: Boolean get() = docker.running

    val up: Boolean
        get() = docker.up

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

    fun check(verbose: Boolean = true): List<HealthStatus> {
        if (!up) {
            throw EnvironmentException("Cannot check environment as it is not up!")
        }

        logger.info("Checking $this")

        return healthChecker.check(verbose)
    }

    fun reload() {
        if (!up) {
            throw EnvironmentException("Cannot reload environment as it is not up!")
        }

        logger.info("Reloading $this")

        docker.reload()

        logger.info("Reloaded $this")
    }

    /**
     * Defines hosts to be appended to system specific hosts file.
     */
    fun hosts(options: HostOptions.() -> Unit) {
        hosts.apply(options)
    }

    /**
     * Configures environment service health checks.
     */
    fun healthChecks(options: HealthChecker.() -> Unit) {
        healthChecker.apply(options)
    }

    override fun toString(): String {
        return "Environment(root=$rootDir, up=$up)"
    }

    companion object {
        const val NAME = "environment"

        fun of(project: Project): EnvironmentExtension {
            return project.extensions.findByType(EnvironmentExtension::class.java)
                    ?: throw EnvironmentException("${project.displayName.capitalize()} must have plugin applied: ${EnvironmentPlugin.ID}")
        }
    }
}

val Project.environment get() = EnvironmentExtension.of(this)