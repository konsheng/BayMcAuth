dependencies {
    implementation(project(":modules:auth-common"))
    implementation(project(":modules:auth-storage"))

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    compileOnly("net.kyori:adventure-api:5.1.1")
    compileOnly("net.kyori:adventure-text-minimessage:5.1.1")
}

tasks.processResources {
    inputs.property("pluginVersion", project.version.toString())
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
