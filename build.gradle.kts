import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.5.6"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.5.31"
    kotlin("plugin.spring") version "1.5.31"
    kotlin("plugin.jpa") version "1.5.31"
    kotlin("plugin.serialization") version "1.5.31"
}

group = "org.endy"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

/**
 * The version of the playwright client, kept in one place because both the dependency below and the
 * `installPlaywrightBrowsers` task have to agree on it: the driver the task installs is the one of
 * this exact release, and a mismatch makes the browser refuse to start.
 *
 * 1.63 is compiled for java 8, so it does not raise the jvmTarget of this project.
 */
val playwrightVersion = "1.63.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.springfox:springfox-boot-starter:3.0.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("mysql:mysql-connector-java")
    runtimeOnly ("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
//    {
//        exclude("org.junit.vintage", "junit-vintage-engine")
//    }
    testImplementation(kotlin("test"))
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("io.mockk:mockk:1.12.4")
//    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.1")
//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jsoup:jsoup:1.15.1")
    implementation("com.microsoft.playwright:playwright:$playwrightVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Downloads the chromium build playwright drives, which is what `BrowserFetcher` starts.
 *
 * The browser binaries are not inside the playwright jar, so a fresh checkout has none of them and
 * every call that renders a page fails until this has run once. Nothing here does it automatically:
 * it is a one-off of a few hundred megabytes, and a build that fetched it silently would make
 * `gradle build` depend on the network for something no test needs.
 *
 * Run it as `gradlew installPlaywrightBrowsers`. `install` rather than `install --with-deps`, since
 * the latter installs system libraries with root, which is a linux concern this project does not
 * have on windows.
 *
 * The browsers land in `%USERPROFILE%\AppData\Local\ms-playwright` unless `PLAYWRIGHT_BROWSERS_PATH`
 * points somewhere else, in which case the same variable has to be set for the application, because
 * the driver looks there and nowhere else.
 *
 * That variable is worth setting when the system drive is full: the driver unpacks itself into the
 * temp directory before it downloads anything, and it fails there with a bare "not enough space on
 * the disk" that says nothing about a browser. Both variables have to agree, and both processes need
 * them, so this is set for the build and for the run:
 *
 *     set PLAYWRIGHT_BROWSERS_PATH=D:\ms-playwright
 *     set TEMP=D:\tmp
 *     gradlew installPlaywrightBrowsers
 *
 * and the same two for the application, as environment variables or in the ide run configuration.
 */
tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "playwright"
    description = "downloads the chromium build playwright drives, needed once before scraping works"
    classpath = configurations["runtimeClasspath"]
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "chromium")
}
