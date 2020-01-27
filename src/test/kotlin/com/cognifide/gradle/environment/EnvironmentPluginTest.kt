package com.cognifide.gradle.environment

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

class EnvironmentPluginTest {
    @Test fun `plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cognifide.gradle.environment")

        // Verify the result
        assertNotNull(project.tasks.findByName("greeting"))
    }
}
