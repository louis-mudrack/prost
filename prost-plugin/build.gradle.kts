plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

dependencies {
    implementation(project(":prost-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
