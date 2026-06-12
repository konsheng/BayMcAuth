dependencies {
    api("org.yaml:snakeyaml:2.6")
    api("org.mindrot:jbcrypt:0.4")

    compileOnly("net.kyori:adventure-api:5.1.1")
    compileOnly("net.kyori:adventure-text-minimessage:5.1.1")
}

val generatedVersionDir = layout.buildDirectory.dir("generated/sources/baymcauthVersion/java")

val generateBayMcAuthBuildInfo by tasks.registering {
    val outputDir = generatedVersionDir
    inputs.property("pluginVersion", project.version.toString())
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/baymc/auth/common/BayMcAuthBuildInfo.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.baymc.auth.common;

            /*
             * 构建期版本信息
             *
             * <p>该类型由 Gradle 生成
             * CI 可以通过 artifactVersionOverride 覆盖插件有效版本
             */
            public final class BayMcAuthBuildInfo {
                public static final String VERSION = "${project.version}";

                private BayMcAuthBuildInfo() {
                }
            }
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )
    }
}

sourceSets {
    main {
        java.srcDir(generatedVersionDir)
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateBayMcAuthBuildInfo)
}

tasks.named("sourcesJar") {
    dependsOn(generateBayMcAuthBuildInfo)
}
