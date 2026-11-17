plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
}

// Реальные ID рекламы приходят из секретов GitHub; без них — тестовые ID Google
val admobAppId = System.getenv("ADMOB_APP_ID")?.takeIf { it.isNotBlank() } ?: "ca-app-pub-3940256099942544~3347511713"
val admobRewardedId = System.getenv("ADMOB_REWARDED_ID")?.takeIf { it.isNotBlank() } ?: "ca-app-pub-3940256099942544/5224354917"
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
	namespace = "com.cersat.magicsmoke"
	compileSdk = 35

	defaultConfig {
		applicationId = "com.cersat.magicsmoke"
		minSdk = 24
		targetSdk = 35
		versionCode = 1
		versionName = "0.1.0"
	}

	signingConfigs {
		if (keystoreFile != null) {
			create("release") {
				storeFile = file(keystoreFile)
				storePassword = System.getenv("KEYSTORE_PASSWORD")
				keyAlias = System.getenv("KEY_ALIAS")
				keyPassword = System.getenv("KEY_PASSWORD")
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			// Пока нет своего ключа в секретах — подписываем debug-ключом, чтобы APK ставился
			signingConfig = signingConfigs.getByName(if (keystoreFile != null) "release" else "debug")
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
