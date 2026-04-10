plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "dev.jarviis.obsidian"
version = "1.1.0"

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
                <li><b>Obsidian Graph</b> — new interactive graph view built with pure Swing/Java2D. Shows all vault notes as nodes with wiki-link edges.</li>
                <li>Force-directed layout (Fruchterman–Reingold) with portrait orientation and label-aware node separation.</li>
                <li>Smooth trackpad/mouse zoom, pan, drag nodes — dragging a node pushes overlapping neighbours recursively.</li>
                <li>Click a node to highlight it and its connections; double-click to open the note in the editor.</li>
                <li>Graph layout is persisted per project — reopening the panel restores the last position of every node.</li>
                <li>Vault registry is now keyed by path — projects with vaults of the same name no longer share the same index.</li>
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
