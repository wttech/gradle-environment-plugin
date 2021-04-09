package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.environment.EnvironmentException

class ContainerManager(private val docker: Docker) {

    private val common = docker.environment.common

    val defined = common.obj.list<Container> {
        set(common.obj.provider {
            when {
                discover.get() -> discover()
                else -> listOf()
            }
        })
    }

    @Suppress("TooGenericExceptionCaught")
    private fun discover(): List<Container> {
        val composeTemplateFile = docker.composeTemplateFile.get().asFile
        return try {
            val yml = composeTemplateFile.inputStream().buffered().use { Formats.asYml(it) }
            val names = yml.get("services").fieldNames().asSequence().toList()
            names.map { Container(docker, it) }
        } catch (e: Exception) {
            throw EnvironmentException("Cannot discover containers from template file '$composeTemplateFile'! Cause: ${e.message}", e)
        }
    }

    val discover = common.obj.boolean {
        convention(true)
        common.prop.boolean("docker.container.discover")?.let { set(it) }
    }

    val dependent = common.obj.boolean {
        convention(true)
        common.prop.boolean("docker.container.dependent")?.let { set(it) }
    }

    /**
     * Define container.
     */
    fun define(name: String, definition: Container.() -> Unit): Container {
        return Container(docker, name).apply { definition(); defined.add(this) }
    }

    fun define(vararg names: String) = define(names.asIterable())

    fun define(names: Iterable<String>) = names.forEach { define(it) {} }

    /**
     * Shorthand for defining container by string invocation.
     */
    operator fun String.invoke(definition: Container.() -> Unit) = define(this, definition)

    /**
     * Get defined container by name.
     */
    fun named(name: String): Container = defined.get().firstOrNull { it.name == name }
            ?: throw DockerException("Container named '$name' is not defined!")

    /**
     * Get defined container by name.
     */
    operator fun get(name: String) = named(name)

    /**
     * Do action for undefined container.
     */
    fun use(name: String, action: Container.() -> Unit) {
        Container(docker, name).apply(action)
    }

    /**
     * Checks if all containers are running.
     */
    val running: Boolean get() = defined.get().all { it.running }

    /**
     * Checks if all containers are up (running and configured).
     */
    val up: Boolean get() = defined.get().all { it.up }

    fun resolve() {
        common.progress(defined.get().size) {
            step = "Resolving container(s)"
            common.parallel.each(defined.get()) { container ->
                increment(container.name) { container.resolve() }
            }
        }
    }

    fun up() {
        common.progress(defined.get().size) {
            step = "Configuring container(s)"

            if (dependent.get()) {
                defined.get().forEach { container ->
                    increment(container.name) { container.up() }
                }
            } else {
                common.parallel.each(defined.get()) { container ->
                    increment(container.name) { container.up() }
                }
            }
        }
    }

    fun reload() {
        common.progress(defined.get().size) {
            step = "Reloading container(s)"

            if (dependent.get()) {
                defined.get().forEach { container ->
                    increment(container.name) { container.reload() }
                }
            } else {
                common.parallel.each(defined.get()) { container ->
                    increment(container.name) { container.reload() }
                }
            }
        }
    }
}
