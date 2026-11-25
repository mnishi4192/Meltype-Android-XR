plugins {
    id("com.android.application")
}
val bundledOfficialCore = layout.buildDirectory.dir("generated/bundledOfficialCore")
val stageOfficialCore by tasks.registering(Sync::class) {
    val library = rootProject.file(".tools/official-meltype-artifacts/libMeltypeNative.so")
    doFirst { check(library.isFile) { "Run tools/build-official-meltype.sh before assembling the APK" } }
    from(library) { into("arm64-v8a") }
    into(bundledOfficialCore)
}
android {
    namespace = "local.mnishi.meltype.android"
    compileSdk = 35
    ndkVersion = "30.0.16248370"
    ndkPath = rootProject.file(".tools/android-sdk/ndk/30.0.16248370").absolutePath
    defaultConfig {
        applicationId = "local.mnishi.meltype.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 9
        versionName = "0.8.0"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release",
                    "-DCMAKE_MAKE_PROGRAM=${rootProject.file(".tools/bin/ninja").absolutePath}")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild {
        cmake {
            path = rootProject.file("core-native/CMakeLists.txt")
            version = "4.4.2"
        }
    }
    sourceSets.getByName("main").jniLibs.srcDir(bundledOfficialCore)
    androidResources { noCompress += listOf("gguf", "data") }
}
tasks.named("preBuild") { dependsOn(stageOfficialCore) }
dependencies {
    implementation(project(":mozc"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
