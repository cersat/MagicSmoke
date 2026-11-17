package com.cersat.magicsmoke

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

// Сборка для Google Play: награды за просмотр рекламы через AdMob
object Ads {
	val available = true
	var ready by mutableStateOf(false)
		private set

	private var ad: RewardedAd? = null
	private var loading = false
	private val handler = Handler(Looper.getMainLooper())

	fun init(activity: Activity) {
		MobileAds.initialize(activity) {}
		load(activity.applicationContext)
	}

	private fun load(ctx: Context) {
		if (loading || ad != null) return
		loading = true
		RewardedAd.load(ctx, BuildConfig.REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
			override fun onAdLoaded(a: RewardedAd) {
				ad = a
				loading = false
				ready = true
			}

			override fun onAdFailedToLoad(e: LoadAdError) {
				ad = null
				loading = false
				ready = false
				handler.postDelayed({ load(ctx) }, 30_000)
			}
		})
	}

	fun show(activity: Activity, onReward: () -> Unit) {
		val ctx = activity.applicationContext
		val a = ad
		if (a == null) {
			load(ctx)
			return
		}
		a.fullScreenContentCallback = object : FullScreenContentCallback() {
			override fun onAdDismissedFullScreenContent() {
				ad = null
				ready = false
				load(ctx)
			}

			override fun onAdFailedToShowFullScreenContent(e: AdError) {
				ad = null
				ready = false
				load(ctx)
			}
		}
		a.show(activity) { onReward() }
	}
}
