package com.cognifide.gradle.environment.docker.runtime

import com.cognifide.gradle.environment.EnvironmentPlugin
import com.cognifide.gradle.environment.environment
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolboxTest {

    @Test
    fun shouldImitateCygpathProperly() {
        toolbox().apply {
            assertEquals(
                "/c/Users/krystian.panek/Projects/gradle-aem-multi/aem/.environment/distributions/mod_dispatcher.so",
                imitateCygpath("C:\\Users\\krystian.panek\\Projects\\gradle-aem-multi\\aem\\.environment\\distributions\\mod_dispatcher.so")
            )

            assertEquals(
                "/c/Users/krystian.panek/Projects/gradle-aem-multi/aem/.environment/distributions/mod_dispatcher.so",
                imitateCygpath("C:/Users/krystian.panek/Projects/gradle-aem-multi/aem/.environment/distributions/mod_dispatcher.so")
            )
        }
    }

    private fun toolbox() = ProjectBuilder.builder().build()
        .also { it.plugins.apply(EnvironmentPlugin.ID) }
        .environment
        .run { Toolbox(this) }
}
