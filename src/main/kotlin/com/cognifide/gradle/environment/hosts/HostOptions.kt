package com.cognifide.gradle.environment.hosts

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.EnvironmentException
import org.gradle.internal.os.OperatingSystem
import java.io.Serializable

/**
 * Manages host definitions in case of different purposes indicated by tags.
 */
class HostOptions(environment: EnvironmentExtension) : Serializable {

    var defined = mutableListOf<Host>()

    val appendix: String
        get() = defined.joinToString("\n") { it.text }

    val osFile = when {
        OperatingSystem.current().isWindows -> """C:\Windows\System32\drivers\etc\hosts"""
        else -> "/etc/hosts"
    }

    var ipDefault = environment.docker.runtime.hostIp

    fun define(url: String, options: Host.() -> Unit = {}) {
        defined.add(Host(url).apply { ip = ipDefault; options() })
    }

    fun define(vararg urls: String, options: Host.() -> Unit = {}) = define(urls.asIterable(), options)

    fun define(urls: Iterable<String>, options: Host.() -> Unit = {}) = urls.forEach { define(it, options) }

    fun find(vararg tags: String) = find(tags.asIterable())

    fun find(tags: Iterable<String>) = all(tags).first()

    fun all(vararg tags: String) = all(tags.asIterable())

    fun all(tags: Iterable<String>) = defined.filter { h -> tags.all { t -> h.tags.contains(t) } }.ifEmpty {
        throw EnvironmentException("Environment has no hosts tagged with '${tags.joinToString(",")}'!")
    }

    companion object {

        const val TAG_AUTHOR = "author"

        const val TAG_PUBLISH = "publish"

        const val TAG_OTHER = "other"
    }
}
