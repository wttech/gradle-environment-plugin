package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentResolve : EnvironmentDefaultTask() {

    @TaskAction
    fun resolve() {
        logger.info("Resolving environment files")
        environment.resolve()
        logger.info("Resolved environment files")
    }

    init {
        description = "Resolves environment files from remote sources before running other tasks"
    }

    companion object {
        const val NAME = "environmentResolve"
    }
}
