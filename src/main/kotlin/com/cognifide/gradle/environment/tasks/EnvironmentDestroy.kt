package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentDestroy : EnvironmentDefaultTask() {

    @TaskAction
    fun destroy() {
        if (!environment.created) {
            logger.lifecycle("Environment cannot be destroyed as it is not created")
            return
        }

        common.progressIndicator {
            message = "Destroying environment"
            environment.destroy()
        }

        common.notifier.lifecycle("Environment destroyed", "Destroyed with success.")
    }

    init {
        description = "Destroys virtualized environment."
        checkForce()
    }

    companion object {
        const val NAME = "environmentDestroy"
    }
}
