package com.cognifide.gradle.environment

import com.cognifide.gradle.common.CommonDefaultPlugin
import org.gradle.api.Project

class EnvironmentCommonPlugin : CommonDefaultPlugin() {

    override fun Project.configureProject() {
        setupExtension()
    }

    private fun Project.setupExtension() {
        extensions.create(EnvironmentExtension.NAME, EnvironmentExtension::class.java, this)
    }

    companion object {
        const val ID = "com.cognifide.environment.common"
    }
}
