plugins { id("com.android.application") }
android { namespace = "com.blackghost.adaptask"; compileSdk = 35
 defaultConfig { applicationId = "com.blackghost.adaptask"; minSdk = 26; targetSdk = 35; versionCode = 12; versionName = "1.1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
