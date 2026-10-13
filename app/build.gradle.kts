plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt") }
android {
 namespace = "com.personal.memostarter"
 compileSdk = 36
 defaultConfig { applicationId = "com.personal.memostarter"; minSdk = 26; targetSdk = 36; versionCode = 342; versionName = "3.4.2-protected"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; manifestPlaceholders["appLabel"] = "My Memo"
  val pins = providers.environmentVariable("MYMEMO_RELEASE_CERT_SHA256").orElse("").get().lowercase()
  require(pins.isEmpty() || pins.split(",").all { it.matches(Regex("[0-9a-f]{64}")) }) { "Invalid release certificate fingerprint" }
  buildConfigField("String", "RELEASE_CERT_PINS", "\"$pins\"") }
 buildTypes {
  getByName("debug") { applicationIdSuffix = ".preview34"; manifestPlaceholders["appLabel"] = "My Memo 3.4 테스트" }
  getByName("release") {
   isMinifyEnabled = true
   isShrinkResources = true
   isDebuggable = false
   isJniDebuggable = false
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   // Deliberately unsigned: tools/protect_release.py seals and signs the final R8 APK.
   signingConfig = null
  }
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions {
  unitTests.isIncludeAndroidResources = true
  unitTests.all { it.systemProperty("mymemo.securityFixtures", providers.environmentVariable("MYMEMO_SECURITY_FIXTURES").orElse("").get()) }
 }
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 implementation("androidx.room:room-runtime:2.7.2")
 implementation("androidx.room:room-ktx:2.7.2")
 kapt("androidx.room:room-compiler:2.7.2")
 implementation("androidx.datastore:datastore-preferences:1.1.7")
 testImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.14.1")
 testImplementation("androidx.test:core:1.6.1")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
 doFirst { require(!providers.environmentVariable("MYMEMO_RELEASE_CERT_SHA256").orNull.isNullOrBlank()) { "Release needs explicit approved public certificate SHA-256; no preview/debug-key fallback" } }
}
