package com.cognifide.gradle.environment

import org.gradle.api.GradleException

open class EnvironmentException : GradleException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
