package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentUp : EnvironmentDefaultTask() {

    init {
        description = "Turns on virtualized environment"
    }

    @TaskAction
    fun up() {
        if (environment.up) {
            logger.lifecycle("Environment up", "Cannot turn on as it is already up")
            return
        }

        environment.up()

        common.notifier.lifecycle("Environment up", "Turned on with success")
    }

    companion object {
        const val NAME = "environmentUp"
    }
}
