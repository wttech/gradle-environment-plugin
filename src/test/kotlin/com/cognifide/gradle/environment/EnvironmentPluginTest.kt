package com.cognifide.gradle.environment

import com.cognifide.gradle.common.utils.using
import com.cognifide.gradle.environment.tasks.EnvironmentAwait
import com.cognifide.gradle.environment.tasks.EnvironmentDestroy
import com.cognifide.gradle.environment.tasks.EnvironmentDev
import com.cognifide.gradle.environment.tasks.EnvironmentDown
import com.cognifide.gradle.environment.tasks.EnvironmentHosts
import com.cognifide.gradle.environment.tasks.EnvironmentReload
import com.cognifide.gradle.environment.tasks.EnvironmentResetup
import com.cognifide.gradle.environment.tasks.EnvironmentResolve
import com.cognifide.gradle.environment.tasks.EnvironmentRestart
import com.cognifide.gradle.environment.tasks.EnvironmentUp
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

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
