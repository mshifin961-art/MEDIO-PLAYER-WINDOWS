import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.desktop)
}

group = "com.mediop.win"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.vlcj)
    implementation(libs.sqlite.jdbc)
    implementation(libs.jaudiotagger)
    implementation(libs.slf4j.simple)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.mediop.win.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "MEDIO PLAYER"
            packageVersion = "1.0.0"
            description = "MEDIO PLAYER — Windows Desktop Edition"
            vendor = "MEDIO PLAYER"
            copyright = "Copyright (c) 2026 MEDIO PLAYER"

            modules("java.sql", "java.desktop", "java.naming", "jdk.unsupported")

            windows {
                menuGroup = "MEDIO PLAYER"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "8d3f2c4a-91b7-4e6d-a512-7c8f3e4b9011"
                iconFile.set(project.file("src/main/resources/icons/medio_player.ico"))
            }
        }
    }
}
