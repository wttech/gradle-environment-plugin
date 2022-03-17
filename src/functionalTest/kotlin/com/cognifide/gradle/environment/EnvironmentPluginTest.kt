package com.cognifide.gradle.environment

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

class EnvironmentPluginTest {

    @Test
    @Disabled // TODO fix classpath somehow
    fun `can run task`() {
        // Setup the test build
        val projectDir = File("build/functionalTest").apply {
            deleteRecursively()
            mkdirs()
            resolve("settings.gradle.kts").writeText("")
            resolve("build.gradle.kts").writeText(
                """
                plugins {
                    id("com.cognifide.environment")
                }
                """.trimIndent()
            )
        }

        // Run the build
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("environmentHosts")
            .withProjectDir(projectDir)
            .build()

        // Verify the result
        assertEquals(TaskOutcome.SUCCESS, result.task(":environmentHosts")?.outcome)
    }
}
