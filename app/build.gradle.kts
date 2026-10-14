plugins { id("com.android.application") }
android { namespace = "com.blackghost.adaptask"; compileSdk = 35
 defaultConfig { applicationId = "com.blackghost.adaptask"; minSdk = 26; targetSdk = 35; versionCode = 9; versionName = "0.8.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
