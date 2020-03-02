import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    id("org.jetbrains.dokka") version "0.10.1"
    id("com.gradle.plugin-publish") version "0.10.1"
    id("io.gitlab.arturbosch.detekt") version "1.6.0"
    id("com.jfrog.bintray") version "1.8.4"
    id("net.researchgate.release") version "2.8.1"
    id("com.github.breadmoirai.github-release") version "2.2.10"
}

defaultTasks("build", "publishToMavenLocal")
description = "Gradle Environment Plugin"
group = "com.cognifide.gradle"

repositories {
    mavenLocal()
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("com.cognifide.gradle:common-plugin:0.1.19")
    implementation("org.buildobjects:jproc:2.2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("commons-io:commons-io:2.6")
    implementation("org.apache.httpcomponents:httpclient:4.5.10")

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.6.0")
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
    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        dependsOn("classes")
        from(sourceSets["main"].allSource)
    }

    register<DokkaTask>("dokkaJavadoc") {
        outputFormat = "html"
        outputDirectory = "$buildDir/javadoc"
    }

    register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
        dependsOn("dokkaJavadoc")
        from("$buildDir/javadoc")
    }

    withType<JavaCompile>().configureEach{
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
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
        dependsOn("bintrayUpload", "publishPlugins")
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
    }
}

val pluginTags = listOf("docker", "swarm", "environment", "docker-compose")

pluginBundle {
    website = "https://github.com/Cognifide/gradle-environment-plugin"
    vcsUrl = "https://github.com/Cognifide/gradle-environment-plugin.git"
    description = "Gradle Environment Plugin"
    tags = pluginTags
}

bintray {
    user = (project.findProperty("bintray.user") ?: System.getenv("BINTRAY_USER"))?.toString()
    key = (project.findProperty("bintray.key") ?: System.getenv("BINTRAY_KEY"))?.toString()
    setPublications("mavenJava")
    with(pkg) {
        repo = "maven-public"
        name = "gradle-environment-plugin"
        userOrg = "cognifide"
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/Cognifide/gradle-environment-plugin.git"
        setLabels(*pluginTags.toTypedArray())
        with(version) {
            name = project.version.toString()
            desc = "${project.description} ${project.version}"
            vcsTag = project.version.toString()
        }
    }
    publish = (project.findProperty("bintray.publish") ?: "true").toString().toBoolean()
    override = (project.findProperty("bintray.override") ?: "false").toString().toBoolean()
}

githubRelease {
    owner("Cognifide")
    repo("gradle-environment-plugin")
    token((project.findProperty("github.token") ?: "").toString())
    tagName(project.version.toString())
    releaseName(project.version.toString())
    releaseAssets(tasks["jar"], tasks["sourcesJar"], tasks["javadocJar"])
    draft((project.findProperty("github.draft") ?: "false").toString().toBoolean())
    prerelease((project.findProperty("github.prerelease") ?: "false").toString().toBoolean())
    overwrite((project.findProperty("github.override") ?: "true").toString().toBoolean())

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
