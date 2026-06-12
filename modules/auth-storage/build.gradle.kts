dependencies {
    api(project(":modules:auth-common"))

    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.mysql:mysql-connector-j:9.7.0")
    implementation("io.lettuce:lettuce-core:7.6.0.RELEASE")
    implementation("org.slf4j:slf4j-api:2.0.17")
}
