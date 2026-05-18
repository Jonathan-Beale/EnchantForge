plugins { java }
group = "com.example"
version = "1.0-SNAPSHOT"
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Replace Bukkit API with Paper API for testing
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Add JUnit for unit testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")

    // Add Mockito for mocking
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.5.0")

    // Ensure test runtime includes JUnit platform
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}
java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.test { useJUnitPlatform() }
