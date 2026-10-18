plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

dependencies {
    implementation(project(":prost-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT") // the API MockBukkit 4.116.3 is built against
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation("org.testcontainers:mariadb:1.21.4")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}
tasks.processResources { filesMatching("plugin.yml") { expand("version" to project.version) } }
tasks.jar { archiveClassifier.set("plain") }
tasks.shadowJar {
    archiveBaseName.set("Prost")
    archiveClassifier.set("")
}
tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.test { useJUnitPlatform { excludeTags("integration") } }
val integrationTest by tasks.registering(Test::class) {
    description = "Tests against a MariaDB container (needs Docker)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}
