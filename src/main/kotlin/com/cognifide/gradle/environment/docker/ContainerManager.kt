package com.cognifide.gradle.environment.docker

class ContainerManager(private val docker: Docker) {

    private val common = docker.environment.common

    val defined = mutableListOf<Container>()

    val dependent = common.obj.boolean {
        convention(true)
        common.prop.boolean("environment.docker.container.dependent")?.let { set(it) }
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
    fun named(name: String): Container = defined.firstOrNull { it.name == name }
            ?: throw DockerException("Container named '$name' is not defined!")

    /**
     * Do action for undefined container.
     */
    fun use(name: String, action: Container.() -> Unit) {
        Container(docker, name).apply(action)
    }

    /**
     * Checks if all containers are running.
     */
    val running: Boolean get() = defined.all { it.running }

    /**
     * Checks if all containers are up (running and configured).
     */
    val up: Boolean get() = defined.all { it.up }

    fun resolve() {
        common.progress {
            message = "Resolving container(s): ${defined.names}"
            common.parallel.each(defined) { it.resolve() }
        }
    }

    fun up() {
        common.progress {
            message = "Configuring container(s): ${defined.names}"
            if (dependent.get()) {
                defined.forEach { it.up() }
            } else {
                common.parallel.each(defined) { it.up() }
            }
        }
    }

    fun reload() {
        common.progress {
            message = "Reloading container(s): ${defined.names}"
            if (dependent.get()) {
                defined.forEach { it.reload() }
            } else {
                common.parallel.each(defined) { it.reload() }
            }
        }
    }
}
