package com.cognifide.gradle.environment.docker.runtime

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.Runtime

abstract class Base(protected val environment: EnvironmentExtension) : Runtime {

    protected val logger = environment.project.logger

    override fun toString(): String = name.toLowerCase()
}
