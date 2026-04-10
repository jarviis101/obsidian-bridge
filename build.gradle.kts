plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "dev.jarviis.obsidian"
version = "1.1.1"

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
                <li><b>Graph fix</b> — notes with duplicate names in different directories (e.g. <code>business/module.md</code> and <code>design/module.md</code>) now resolve correctly and appear connected in the graph.</li>
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
