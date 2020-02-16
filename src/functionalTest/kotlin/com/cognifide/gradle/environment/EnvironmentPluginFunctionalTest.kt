package com.cognifide.gradle.environment

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentPluginFunctionalTest {

    @Test
    fun `can run task`() {
        // Setup the test build
        val projectDir = File("build/functionalTest")
        projectDir.mkdirs()
        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText("""
            plugins {
                id('com.cognifide.environment')
            }
        """)

        // Run the build
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("environmentHosts")
        runner.withProjectDir(projectDir)
        val result = runner.build();

        // Verify the result
        assertEquals(TaskOutcome.SUCCESS, result.task(":environmentHosts")?.outcome)
    }
}
