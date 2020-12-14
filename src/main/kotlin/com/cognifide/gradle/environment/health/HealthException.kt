package com.cognifide.gradle.environment.health

import com.cognifide.gradle.environment.EnvironmentException

open class HealthException : EnvironmentException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
