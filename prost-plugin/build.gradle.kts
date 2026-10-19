plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    maven("https://repo.essentialsx.net/releases/")
    maven("https://repo.extendedclip.com/releases/")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

dependencies {
    implementation(project(":prost-api"))
    implementation("org.bstats:bstats-bukkit:3.2.1")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.essentialsx:EssentialsX:2.21.2") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.12.3")
    testImplementation("me.clip:placeholderapi:2.12.3")
    testImplementation("net.essentialsx:EssentialsX:2.21.2") { isTransitive = false } // the instanceof IEssentials check
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
tasks.processResources {
    inputs.property("version", project.version)   // expand() alone leaves plugin.yml at the old version after a bump
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.jar { archiveClassifier.set("plain") }
tasks.shadowJar {
    archiveBaseName.set("Prost")
    archiveClassifier.set("")
    relocate("org.bstats", "dev.louismudrack.prost.libs.bstats")
}
tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.test {
    useJUnitPlatform { excludeTags("integration") }
    systemProperty("prost.offline", "true")
    // MockBukkit turns a call it has not implemented into a skipped test: a skip must not pass as a green build
    afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ suite, result ->
        if (suite.parent == null && result.skippedTestCount > 0)
            throw GradleException("${result.skippedTestCount} test(s) skipped - MockBukkit skips a test that calls something it does not implement; see the test report")
    }))
}
val integrationTest by tasks.registering(Test::class) {
    description = "Tests against a MariaDB container (needs Docker)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    systemProperty("prost.offline", "true")
}
