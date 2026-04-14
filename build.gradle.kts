import java.util.Properties

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinSerialization)
}

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}
fun localProp(key: String): String? = localProps.getProperty(key)

group = "dev.jarviis.obsidian"
version = "1.1.5"

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
        val localIde = localProp("localIdeDir")
        if (localIde != null) local(localIde)

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
                <li><b>Localization</b> — all user-visible strings are now served through the plugin's message bundle; no more hardcoded English text in the UI.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            val localIde = localProp("localIdeDir")
            if (localIde != null) local(localIde)
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
