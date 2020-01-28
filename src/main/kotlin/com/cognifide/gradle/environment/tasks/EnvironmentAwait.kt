package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentAwait : EnvironmentDefaultTask() {

    @TaskAction
    fun await() {
        environment.check()
    }

    init {
        description = "Await for healthy condition of environment."
    }

    companion object {
        const val NAME = "environmentAwait"
    }
}
