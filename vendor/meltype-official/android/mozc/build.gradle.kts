// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
}

val officialArtifacts = rootProject.file(".tools/official-meltype-artifacts")
val bundledMozc = layout.buildDirectory.dir("generated/bundledMozc")
val stageMozc by tasks.registering(Sync::class) {
    val required = listOf("libmozc.so", "mozc.data", "MOZC_NOTICES.txt")
    doFirst {
        val missing = required.filterNot { officialArtifacts.resolve(it).isFile }
        if (missing.isNotEmpty()) throw GradleException("Prepare the official Mozc artifacts: ${missing.joinToString()}")
    }
    into(bundledMozc)
    from(officialArtifacts) {
        include("libmozc.so")
        into("jniLibs/arm64-v8a")
    }
    from(officialArtifacts) {
        include("mozc.data", "MOZC_NOTICES.txt")
        into("assets")
    }
}
android {
    namespace = "io.github.ninjin0802.keyboard.mozc"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }
    sourceSets.getByName("main") {
        assets.srcDir(bundledMozc.map { it.dir("assets") })
        jniLibs.srcDir(bundledMozc.map { it.dir("jniLibs") })
    }
    packaging.jniLibs.keepDebugSymbols += "**/libmozc.so"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.36.2" }
    generateProtoTasks {
        all().forEach { task -> task.builtins.create("java") { option("lite") } }
    }
}
tasks.named("preBuild") { dependsOn(stageMozc) }
dependencies { api("com.google.protobuf:protobuf-javalite:4.36.2") }
