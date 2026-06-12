import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":modules:auth-common"))
    implementation(project(":modules:auth-storage"))
    implementation(project(":modules:auth-velocity"))
    implementation(project(":modules:auth-paper"))
}

tasks.withType<ShadowJar>().configureEach {
    archiveBaseName.set("BayMcAuth")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    mergeServiceFiles()

    relocate("com.zaxxer.hikari", "com.baymc.auth.libs.hikari")
    relocate("com.mysql", "com.baymc.auth.libs.mysql")
    relocate("io.lettuce.core", "com.baymc.auth.libs.lettuce")
    relocate("org.yaml.snakeyaml", "com.baymc.auth.libs.snakeyaml")
    relocate("org.mindrot.jbcrypt", "com.baymc.auth.libs.jbcrypt")
    relocate("reactor", "com.baymc.auth.libs.reactor")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
