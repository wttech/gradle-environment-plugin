package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask

open class EnvironmentRestart : EnvironmentDefaultTask() {

    init {
        description = "Restart virtualized development environment."
    }

    companion object {
        const val NAME = "environmentRestart"
    }
}
