plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "dev.jarviis.obsidian"
version = "1.1.3"

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
            <ul>
                <li><b>Graph sync</b> — the graph now automatically highlights the node corresponding to the currently open file. Switching between files updates the highlight in real time.</li>
                <li>Clicking the graph background no longer clears the highlight — it returns focus to the open file's node.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            local("/Users/kharchenko.o/Applications/PhpStorm.app")
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
