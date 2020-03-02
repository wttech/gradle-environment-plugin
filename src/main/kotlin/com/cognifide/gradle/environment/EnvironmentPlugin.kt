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
        val resolve = register<EnvironmentResolve>(EnvironmentResolve.NAME)

        val down = register<EnvironmentDown>(EnvironmentDown.NAME)
        val destroy = register<EnvironmentDestroy>(EnvironmentDestroy.NAME) {
            dependsOn(down)
        }
        val up = register<EnvironmentUp>(EnvironmentUp.NAME) {
            mustRunAfter(resolve, down, destroy)
        }
        val restart = register<EnvironmentRestart>(EnvironmentRestart.NAME) {
            dependsOn(down, up)
        }
        val resetup = register<EnvironmentResetup>(EnvironmentResetup.NAME) {
            dependsOn(destroy, up)
        }

        register<EnvironmentDev>(EnvironmentDev.NAME) {
            mustRunAfter(up)
        }
        val await = register<EnvironmentAwait>(EnvironmentAwait.NAME) {
            mustRunAfter(up)
        }
        register<EnvironmentReload>(EnvironmentReload.NAME) {
            mustRunAfter(up)
        }
        register<EnvironmentHosts>(EnvironmentHosts.NAME)


        // Runtime lifecycle

        named<Up>(Up.NAME) {
            dependsOn(up)
        }
        named<Down>(Down.NAME) {
            dependsOn(down)
        }
        named<Destroy>(Destroy.NAME) {
            dependsOn(destroy)
        }
        named<Restart>(Restart.NAME) {
            dependsOn(restart)
        }
        named<Setup>(Setup.NAME) {
            dependsOn(up)
        }
        named<Resetup>(Resetup.NAME) {
            dependsOn(resetup)
        }
        named<Resolve>(Resolve.NAME) {
            dependsOn(resolve)
        }
        named<Await>(Await.NAME) {
            dependsOn(await)
        }
    }

    companion object {
        const val ID = "com.cognifide.environment"
    }
}
