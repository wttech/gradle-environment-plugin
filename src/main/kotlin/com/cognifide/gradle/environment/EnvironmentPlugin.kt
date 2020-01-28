package com.cognifide.gradle.environment

import com.cognifide.gradle.environment.tasks.*
import org.gradle.api.Plugin
import org.gradle.api.Project

class EnvironmentPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            setupExtension()
            setupTasks()
        }
    }

    private fun Project.setupExtension() {
        extensions.create(EnvironmentExtension.NAME, EnvironmentExtension::class.java)
    }

    private fun Project.setupTasks() {
        tasks.apply {
/*            register(EnvironmentDown.NAME, EnvironmentDown::class.java)

            register(EnvironmentUp.NAME, EnvironmentUp::class.java) {
                mustRunAfter(EnvironmentResolve.NAME, EnvironmentDown.NAME, EnvironmentDestroy.NAME)
                plugins.withId(InstancePlugin.ID) {
                    mustRunAfter(InstanceUp.NAME, InstanceSatisfy.NAME, InstanceProvision.NAME, InstanceSetup.NAME)
                }
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
                plugins.withId(InstancePlugin.ID) {
                    mustRunAfter(InstanceAwait.NAME)
                }
            }
            register<EnvironmentReload>(EnvironmentReload.NAME) {
                mustRunAfter(EnvironmentUp.NAME)
            }
            register<EnvironmentHosts>(EnvironmentHosts.NAME)
            register<EnvironmentResolve>(EnvironmentResolve.NAME)

            // Common lifecycle

            registerOrConfigure<Up>(Up.NAME) {
                dependsOn(EnvironmentUp.NAME)
            }
            registerOrConfigure<Down>(Down.NAME) {
                dependsOn(EnvironmentDown.NAME)
            }
            registerOrConfigure<Destroy>(Destroy.NAME) {
                dependsOn(EnvironmentDestroy.NAME)
            }
            registerOrConfigure<Restart>(Restart.NAME) {
                dependsOn(EnvironmentRestart.NAME)
            }
            registerOrConfigure<Setup>(Setup.NAME) {
                dependsOn(EnvironmentUp.NAME)
            }
            registerOrConfigure<Resetup>(Resetup.NAME) {
                dependsOn(EnvironmentResetup.NAME)
            }
            registerOrConfigure<Resolve>(Resolve.NAME) {
                dependsOn(EnvironmentResolve.NAME)
            }
            registerOrConfigure<Await>(Await.NAME) {
                dependsOn(EnvironmentAwait.NAME)
            }*/
        }
    }

    companion object {
        const val ID = "com.cognifide.aem.environment"
    }

}
