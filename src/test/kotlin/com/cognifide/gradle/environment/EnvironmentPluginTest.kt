package com.cognifide.gradle.environment

import com.cognifide.gradle.common.utils.using
import com.cognifide.gradle.environment.tasks.*
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test

class EnvironmentPluginTest {

    @Test
    fun `plugin registers extension and tasks`() = ProjectBuilder.builder().build().using {
        plugins.apply(EnvironmentPlugin.ID)

        extensions.getByName(EnvironmentExtension.NAME)
        tasks.getByName(EnvironmentAwait.NAME)
        tasks.getByName(EnvironmentDestroy.NAME)
        tasks.getByName(EnvironmentDev.NAME)
        tasks.getByName(EnvironmentDown.NAME)
        tasks.getByName(EnvironmentHosts.NAME)
        tasks.getByName(EnvironmentReload.NAME)
        tasks.getByName(EnvironmentResetup.NAME)
        tasks.getByName(EnvironmentResolve.NAME)
        tasks.getByName(EnvironmentRestart.NAME)
        tasks.getByName(EnvironmentUp.NAME)
    }
}
