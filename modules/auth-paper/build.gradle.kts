dependencies {
    implementation(project(":modules:auth-common"))
    implementation(project(":modules:auth-storage"))

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    compileOnly("net.kyori:adventure-api:5.1.1")
    compileOnly("net.kyori:adventure-text-minimessage:5.1.1")
}
