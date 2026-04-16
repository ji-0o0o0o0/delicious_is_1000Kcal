plugins {
    id("java")
    id("application")
}

group = "com.kakaotracker"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.kakaotracker.Main")
}

dependencies {
    // Google Sheets API
    implementation("com.google.apis:google-api-services-sheets:v4-rev20220927-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")

    // Tesseract OCR
    implementation("net.sourceforge.tess4j:tess4j:5.8.0")

    implementation("ch.qos.logback:logback-classic:1.4.11")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.kakaotracker.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}