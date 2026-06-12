plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.4.2" apply false
}

val basePluginVersion = "1.0.0-SNAPSHOT"
val effectivePluginVersion = providers.gradleProperty("artifactVersionOverride").orElse(basePluginVersion).get()

group = "com.baymc.auth"
version = effectivePluginVersion

extra["basePluginVersion"] = basePluginVersion

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.0")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:6.1.0")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.register("shadowJar") {
    group = "build"
    description = "构建最终的 BayMcAuth 插件 Jar"
    dependsOn(":modules:auth-plugin:shadowJar")
}

tasks.register("printVersion") {
    group = "help"
    description = "输出 BayMcAuth 插件基础版本"
    doLast {
        println(basePluginVersion)
    }
}
