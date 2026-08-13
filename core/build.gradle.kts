plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation(file("../../libs/qrcode-encoder-1.0.0.jar"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveFileName.set("WorldmeMusic-${project.version}.jar")
    relocate("top.worldmeqc", "top.worldme.music.libs.top.worldmeqc")
}
