plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "dev.jarviis.obsidian"
version = "1.0.4"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        local("/Users/kharchenko.o/Applications/PhpStorm.app")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

// ── Download D3.js (bundled, no CDN at runtime) ─────────────────────────────
val d3Version = "7.9.0"
val d3OutputDir = layout.projectDirectory.dir("src/main/resources/graph")

val downloadD3 by tasks.registering(Exec::class) {
    val outputFile = d3OutputDir.file("d3.min.js").asFile
    outputs.file(outputFile)
    onlyIf { !outputFile.exists() }
    doFirst { outputFile.parentFile.mkdirs() }
    commandLine(
        "curl", "-fsSL",
        "https://cdn.jsdelivr.net/npm/d3@$d3Version/dist/d3.min.js",
        "-o", outputFile.absolutePath
    )
}

tasks.named("processResources") {
    dependsOn(downloadD3)
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
