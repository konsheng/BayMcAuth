dependencies {
    implementation(project(":modules:auth-common"))
    implementation(project(":modules:auth-storage"))

    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}
