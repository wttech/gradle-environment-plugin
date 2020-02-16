package com.cognifide.gradle.environment.hosts

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.EnvironmentException
import org.gradle.internal.os.OperatingSystem
import java.io.Serializable

/**
 * Manages host definitions in case of different purposes indicated by tags.
 */
class HostOptions(environment: EnvironmentExtension) : Serializable {

    val docker = environment.docker

    val common = environment.common

    val defined = common.obj.list<Host> {
        convention(listOf())
    }

    val ipDefault = common.obj.string {
        convention(docker.runtime.map { it.hostIp })
    }

    val osFile = common.obj.string {
        convention(common.obj.provider {
            when {
                OperatingSystem.current().isWindows -> """C:\Windows\System32\drivers\etc\hosts"""
                else -> "/etc/hosts"
            }
        })
    }

    val appendix: String get() = defined.get().joinToString("\n") { it.text }

    operator fun String.invoke(options: Host.() -> Unit = {}) = define(this, options)

    fun define(url: String, options: Host.() -> Unit = {}) {
        defined.add(common.obj.provider { Host(url).apply { ip = ipDefault.get(); options() } })
    }

    fun define(vararg urls: String, options: Host.() -> Unit = {}) = define(urls.asIterable(), options)

    fun define(urls: Iterable<String>, options: Host.() -> Unit = {}) = urls.forEach { define(it, options) }

    fun find(vararg tags: String) = find(tags.asIterable())

    fun find(tags: Iterable<String>) = all(tags).firstOrNull()

    operator fun get(tag: String): Host = get(tag)

    fun get(vararg tags: String) = get(tags.asIterable())

    fun get(tags: Iterable<String>) = find(tags)
            ?: throw EnvironmentException("Environment has no host tagged with '${tags.joinToString(",")}'!")

    fun all(vararg tags: String) = all(tags.asIterable())

    fun all(tags: Iterable<String>) = defined.get().filter { h -> tags.all { t -> h.tags.contains(t) } }.ifEmpty {
        throw EnvironmentException("Environment has no hosts tagged with '${tags.joinToString(",")}'!")
    }
}
