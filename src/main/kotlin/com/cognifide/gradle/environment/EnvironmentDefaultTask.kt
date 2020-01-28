package com.cognifide.gradle.environment

import com.cognifide.gradle.common.CommonDefaultTask
import org.gradle.api.tasks.Internal

open class EnvironmentDefaultTask : CommonDefaultTask(), EnvironmentTask {

    @Internal
    final override val environment = EnvironmentExtension.of(project)

    init {
        group = EnvironmentTask.GROUP
    }
}
