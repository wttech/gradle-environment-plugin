package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import com.cognifide.gradle.environment.reloader.Reloader
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

open class EnvironmentDev : EnvironmentDefaultTask() {

    @Internal
    val reloader = Reloader(environment)

    @TaskAction
    fun dev() {
        if (!environment.running) {
            logger.error("Environment is not running.")
            return
        }

        if (!reloader.configured) {
            logger.warn("None of Docker containers have configured watched directory!")
            return
        }

        common.progressLogger {
            // Whatever on parent logger to be able to pin children loggers from other threads
            progress("Watching files")

            reloader.start()
        }
    }

    init {
        description = "Turns on environment development mode (interactive e.g HTTPD configuration reloading on file changes)"
    }

    companion object {
        const val NAME = "environmentDev"
    }
}
