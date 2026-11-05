plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false

    // KSP：升级到 2.3.9（AGP 9 要求 2.3.6+）
    id("com.google.devtools.ksp") version "2.3.9" apply false

    // Hilt：升级到 2.59.2（AGP 9 要求 2.59.2+）
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
}

