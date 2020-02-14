package com.cognifide.gradle.environment

import com.cognifide.gradle.common.CommonDefaultPlugin
import com.cognifide.gradle.common.RuntimePlugin
import com.cognifide.gradle.common.tasks.runtime.*
import com.cognifide.gradle.environment.tasks.*
import org.gradle.api.Project

class EnvironmentPlugin : CommonDefaultPlugin() {

    override fun Project.configureProject() {
        setupDependentPlugins()
        setupExtension()
        setupTasks()
    }

    private fun Project.setupDependentPlugins() {
        plugins.apply(RuntimePlugin::class.java)
    }

    private fun Project.setupExtension() {
        extensions.create(EnvironmentExtension.NAME, EnvironmentExtension::class.java, this)
    }

    private fun Project.setupTasks() = tasks {
        register(EnvironmentDown.NAME, EnvironmentDown::class.java)

        register(EnvironmentUp.NAME, EnvironmentUp::class.java) {
            mustRunAfter(EnvironmentResolve.NAME, EnvironmentDown.NAME, EnvironmentDestroy.NAME)
        }
        register<EnvironmentRestart>(EnvironmentRestart.NAME) {
            dependsOn(EnvironmentDown.NAME, EnvironmentUp.NAME)
        }
        register<EnvironmentDestroy>(EnvironmentDestroy.NAME) {
            dependsOn(EnvironmentDown.NAME)
        }
        register<EnvironmentResetup>(EnvironmentResetup.NAME) {
            dependsOn(EnvironmentDestroy.NAME, EnvironmentUp.NAME)
        }

        register<EnvironmentDev>(EnvironmentDev.NAME) {
            mustRunAfter(EnvironmentUp.NAME)
        }
        register<EnvironmentAwait>(EnvironmentAwait.NAME) {
            mustRunAfter(EnvironmentUp.NAME)
        }
        register<EnvironmentReload>(EnvironmentReload.NAME) {
            mustRunAfter(EnvironmentUp.NAME)
        }
        register<EnvironmentHosts>(EnvironmentHosts.NAME)
        register<EnvironmentResolve>(EnvironmentResolve.NAME)

        // Runtime lifecycle
        
        named<Up>(Up.NAME) {
            dependsOn(EnvironmentUp.NAME)
        }
        named<Down>(Down.NAME) {
            dependsOn(EnvironmentDown.NAME)
        }
        named<Destroy>(Destroy.NAME) {
            dependsOn(EnvironmentDestroy.NAME)
        }
        named<Restart>(Restart.NAME) {
            dependsOn(EnvironmentRestart.NAME)
        }
        named<Setup>(Setup.NAME) {
            dependsOn(EnvironmentUp.NAME)
        }
        named<Resetup>(Resetup.NAME) {
            dependsOn(EnvironmentResetup.NAME)
        }
        named<Resolve>(Resolve.NAME) {
            dependsOn(EnvironmentResolve.NAME)
        }
        named<Await>(Await.NAME) {
            dependsOn(EnvironmentAwait.NAME)
        }
    }

    companion object {
        const val ID = "com.cognifide.environment"
    }

}
