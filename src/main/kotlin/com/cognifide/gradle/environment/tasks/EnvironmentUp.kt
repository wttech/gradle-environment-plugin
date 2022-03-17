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
            if (!environment.upToDate) {
                logger.lifecycle(
                    listOf(
                        "Turning down the environment as it is not up-to-date!",
                        "Compose file needs update: ${environment.docker.composeFile.get().asFile}"
                    ).joinToString("\n")
                )
                environment.down()
            } else {
                logger.lifecycle("Skipping turning on the environment as it is already up-to-date!")
                return
            }
        }

        environment.up()

        common.notifier.lifecycle("Environment up", "Turned on with success.")
    }

    companion object {
        const val NAME = "environmentUp"
    }
}
