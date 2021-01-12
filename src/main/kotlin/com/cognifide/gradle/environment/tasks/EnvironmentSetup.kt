package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask

open class EnvironmentSetup : EnvironmentDefaultTask() {

    init {
        description = "Sets up virtualized development environment."
    }

    companion object {
        const val NAME = "environmentSetup"
    }
}
