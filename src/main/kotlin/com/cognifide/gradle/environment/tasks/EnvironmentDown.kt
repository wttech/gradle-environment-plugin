package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentDown : EnvironmentDefaultTask() {

    @TaskAction
    fun down() {
        if (!environment.running) {
            logger.lifecycle("Skipping turning off the environment as it is not running.")
            return
        }

        environment.down()

        common.notifier.lifecycle("Environment down", "Turned off with success")
    }

    init {
        description = "Turns off virtualized environment"
    }

    companion object {
        const val NAME = "environmentDown"
    }
}
