import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.4.20"
    id("org.jetbrains.dokka") version "1.4.0-rc"
    id("com.gradle.plugin-publish") version "0.11.0"
    id("io.gitlab.arturbosch.detekt") version "1.6.0"
    id("net.researchgate.release") version "2.8.1"
    id("com.github.breadmoirai.github-release") version "2.2.10"
}

defaultTasks("build", "publishToMavenLocal")
description = "Gradle Environment Plugin"
group = "com.cognifide.gradle"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.20")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")

    implementation("com.cognifide.gradle:common-plugin:1.0.34")
    implementation("org.buildobjects:jproc:2.3.0")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("commons-io:commons-io:2.6")
    implementation("org.apache.httpcomponents:httpclient:4.5.12")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.10.3")
    compileOnly(project(":common"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.6.0")
}

val functionalTestSourceSet = sourceSets.create("functionalTest")
gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

val functionalTest by tasks.creating(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    dependsOn("detektFunctionalTest")
}

val check by tasks.getting(Task::class) {
    dependsOn(functionalTest)
}

tasks {
    jar {
        dependsOn(":hosts:jar")
        from(provider { zipTree((project(":common").tasks.getByName("jar") as Jar).archiveFile) })
        from(provider { project(":hosts").tasks.getByName("jar") })
    }

    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        dependsOn("classes")
        from(sourceSets["main"].allSource)
    }

    dokkaJavadoc {
        outputDirectory = "$buildDir/javadoc"
    }

    register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
        dependsOn("dokkaJavadoc")
        from("$buildDir/javadoc")
    }

    withType<Test>().configureEach {
        testLogging.showStandardStreams = true
        useJUnitPlatform()
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs = freeCompilerArgs + "-Xuse-experimental=kotlin.Experimental"
        }
    }

    named<Test>("test") {
        dependsOn("detektTest")
    }

    named<Task>("build") {
        dependsOn("sourcesJar", "javadocJar")
    }

    named<Task>("publishToMavenLocal") {
        dependsOn("sourcesJar", "javadocJar")
    }

    named("afterReleaseBuild") {
        dependsOn("publishPlugins")
    }

    named("githubRelease") {
        mustRunAfter("release")
    }

    register("fullRelease") {
        dependsOn("release", "githubRelease")
    }
}

detekt {
    config.from(file("detekt.yml"))
    parallel = true
    autoCorrect = true
    failFast = true
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
        }
    }
}

gradlePlugin {
    plugins {
        create("environment") {
            id = "com.cognifide.environment"
            implementationClass = "com.cognifide.gradle.environment.EnvironmentPlugin"
            displayName = "Environment Plugin"
            description = "Provides seamless Gradle integration with Docker & Swarm."
        }
        create("common") {
            id = "com.cognifide.environment.common"
            implementationClass = "com.cognifide.gradle.environment.EnvironmentCommonPlugin"
            displayName = "Environment Common Plugin"
        }
    }
}

val pluginTags = listOf("docker", "swarm", "environment", "docker-compose")

pluginBundle {
    website = "https://github.com/wttech/gradle-environment-plugin"
    vcsUrl = "https://github.com/wttech/gradle-environment-plugin.git"
    description = "Gradle Environment Plugin"
    tags = pluginTags
}

githubRelease {
    owner("wttech")
    repo("gradle-environment-plugin")
    token((project.findProperty("github.token") ?: "").toString())
    tagName(project.version.toString())
    releaseName(project.version.toString())
    releaseAssets(tasks["jar"], tasks["sourcesJar"], tasks["javadocJar"])
    draft((project.findProperty("github.draft") ?: "false").toString().toBoolean())
    overwrite((project.findProperty("github.override") ?: "true").toString().toBoolean())

    val prerelease = (project.findProperty("github.prerelease") ?: "true").toString().toBoolean()
    if (prerelease) {
        prerelease(true)
    } else {
        body { """
            |# What's new
            |
            |TBD
            |
            |# Upgrade notes
            |
            |Nothing to do.
            |
            |# Contributions
            |
            |None.
            """.trimMargin()
        }
    }
}

allprojects {
    plugins.withId("java") {
        tasks.withType<JavaCompile>().configureEach{
            sourceCompatibility = JavaVersion.VERSION_1_8.toString()
            targetCompatibility = JavaVersion.VERSION_1_8.toString()
        }
    }
}