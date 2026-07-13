plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.modificator.launcher"
    compileSdk = 36
    enableKotlin = false

    defaultConfig {
        applicationId = "cn.modificator.launcher"
        minSdk = 14
        targetSdk = 36
        versionCode = 49
        versionName = "0.1.10.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/license.txt")
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.apache.ftpserver:ftplet-api:1.2.1")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
    testImplementation("junit:junit:4.13.2")
}
