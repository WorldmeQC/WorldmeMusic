plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "top.worldme.music"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.google.code.gson:gson:2.10.1")
    implementation("top.mrxiaom:qrcode-encoder:1.0.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveFileName.set("WorldmeMusic-${project.version}.jar")
    relocate("top.mrxiaom", "top.worldme.music.libs.top.mrxiaom")
}
