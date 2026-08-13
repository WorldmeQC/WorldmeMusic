plugins {
    id("java")
}

allprojects {
    group = "top.worldme.music"
    version = "1.0.0"
}



subprojects {
    apply(plugin = "java")
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.momirealms.net/releases/")
    }
}


tasks.jar {
    enabled = false
}

tasks.withType<Jar> {
    enabled = false
}

tasks.clean {
    doFirst {
        delete(rootProject.layout.projectDirectory.dir("out"))
    }
}


tasks.register<Copy>("packagePlugins") {
    group = "build"
    description = "收集所有子模块的插件 jar 到 out 目录"

    // 收集所有子项目的 jar 任务产物
    subprojects.forEach { sub ->
        from(sub.tasks.jar)
        // 如果子项目用了 Shadow 插件，也一并收集 shadowJar
        sub.tasks.findByName("shadowJar")?.let { shadowTask ->
            from(shadowTask)
        }
    }

    into(layout.projectDirectory.dir("out"))
}

// 让 ./gradlew build 自动执行收集
tasks.build {
    dependsOn(tasks.named("packagePlugins"))
}
