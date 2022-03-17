package com.cognifide.gradle.environment.docker.runtime

import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.environment.EnvironmentExtension
import org.gradle.internal.os.OperatingSystem

class Desktop(environment: EnvironmentExtension) : Base(environment) {

    override val name: String get() = NAME

    override val hostIp: String get() = environment.prop.string("docker.desktop.hostIp")
        ?: "127.0.0.1"

    override val hostInternalIp: String get() = environment.prop.string("docker.desktop.hostInternalIp")
        ?: detectHostInternalIp() ?: "172.17.0.1"

    override val safeVolumes: Boolean get() = environment.prop.boolean("docker.desktop.safeVolumes")
        ?: !OperatingSystem.current().isWindows

    override fun determinePath(path: String) = Formats.normalizePath(path)

    companion object {
        const val NAME = "desktop"
    }
}
