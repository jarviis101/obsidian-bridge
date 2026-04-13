plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "dev.jarviis.obsidian"
version = "1.1.4"

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
                <li><b>Graph live updates</b> — the graph now automatically refreshes when vault files are created, deleted, moved, or when wiki-links are added or removed inside a note.</li>
                <li><b>Wiki-link autocomplete for duplicate names</b> — notes with the same filename now all appear in the completion list; selecting one inserts the full disambiguating path. The prefix filter also matches against folder paths.</li>
                <li><b>Alias folding fix</b> — <code>[[note|Alias]]</code> now correctly renders as <em>Alias</em> even when the link already had a prior fold with a different display text.</li>
                <li><b>Fold refresh on vault ready</b> — <code>.md</code> files opened during IDE startup now receive their wiki-link folds once the vault index is fully built.</li>
                <li><b>Graph colors</b> — replaced direct <code>java.awt.Color</code> alpha usage with <code>ColorUtil.withAlpha</code> for proper theme support.</li>
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
