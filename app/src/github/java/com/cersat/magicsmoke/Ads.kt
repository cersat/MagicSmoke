package com.cersat.magicsmoke

import android.app.Activity

// Сборка для GitHub: рекламы нет, кнопки наград не показываются
object Ads {
	val available = false
	val ready = false

	fun init(activity: Activity) {}

	fun show(activity: Activity, onReward: () -> Unit) {}
}
