plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "com.codigrate"

// plugin.xml is hand-maintained (same workflow as the Codigrate theme plugins),
// so it is the single source of truth: the build reads the version and
// since-build from it instead of patching them in from gradle.properties.
val pluginXmlText = file("src/main/resources/META-INF/plugin.xml").readText()
val pluginXmlVersion = Regex("<version>([^<]+)</version>").find(pluginXmlText)!!.groupValues[1]
val pluginXmlSinceBuild = Regex("since-build=\"([^\"]+)\"").find(pluginXmlText)!!.groupValues[1]

version = pluginXmlVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null && file(localIde).exists()) {
            local(localIde)
        } else {
            intellijIdeaCommunity("2025.2")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        version = pluginXmlVersion
        ideaVersion {
            sinceBuild = pluginXmlSinceBuild
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
