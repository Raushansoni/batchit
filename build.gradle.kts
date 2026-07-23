buildscript {
  repositories {
    google()
    maven("https://plugins.gradle.org/m2/")
  }
}

subprojects {
  configurations.configureEach {
    resolutionStrategy {
      // Keep Stream Log / Push aligned with Chat SDK 6.4.4. Newer push (1.3.x)
      // upgrades stream-log to 1.3.x (moved AndroidStreamLogger package) and
      // registers incompatible PushDelegateProvider entries.
      force("io.getstream:stream-log:1.1.4")
      force("io.getstream:stream-log-android:1.1.4")
      force("io.getstream:stream-android-push:1.1.8")
      force("io.getstream:stream-android-push-delegate:1.1.8")
      force("io.getstream:stream-android-push-firebase:1.1.8")
      force("io.getstream:stream-android-push-permissions:1.1.8")
      force("io.getstream:stream-android-push-permissions-snackbar:1.1.8")
    }
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.google.secrets) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.firebase.crashlytics) apply false
  alias(libs.plugins.spotless) apply false
}
