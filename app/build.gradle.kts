plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
}

// Реальные ID рекламы приходят из секретов GitHub; без них — тестовые ID Google
val admobAppId = System.getenv("ADMOB_APP_ID")?.takeIf { it.isNotBlank() } ?: "ca-app-pub-3940256099942544~3347511713"
val admobRewardedId = System.getenv("ADMOB_REWARDED_ID")?.takeIf { it.isNotBlank() } ?: "ca-app-pub-3940256099942544/5224354917"

android {
	namespace = "com.cersat.magicsmoke"
	compileSdk = 35

	defaultConfig {
		applicationId = "com.cersat.magicsmoke"
		minSdk = 24
		targetSdk = 35
		versionCode = 7
		versionName = "0.1.6"
	}

	// Постоянный ключ лежит в репо — подпись не меняется, обновления ставятся поверх
	signingConfigs {
		create("release") {
			storeFile = file("../magicsmoke.jks")
			storePassword = "magicsmoke"
			keyAlias = "magicsmoke"
			keyPassword = "magicsmoke"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
		}
	}

	flavorDimensions += "store"
	productFlavors {
		create("play") {
			dimension = "store"
			manifestPlaceholders["admobAppId"] = admobAppId
			buildConfigField("String", "REWARDED_ID", "\"$admobRewardedId\"")
		}
		create("github") {
			dimension = "store"
		}
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
}

dependencies {
	val bom = platform("androidx.compose:compose-bom:2024.12.01")
	implementation(bom)
	implementation("androidx.activity:activity-compose:1.9.3")
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.foundation:foundation")
	implementation("androidx.compose.material3:material3")
	"playImplementation"("com.google.android.gms:play-services-ads:23.6.0")
}
