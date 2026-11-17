package com.cersat.magicsmoke.game

import android.content.Context
import org.json.JSONObject

object Save {
	private const val PREF = "magicsmoke"
	private const val KEY = "state"

	fun save(ctx: Context, e: Engine) {
		ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, e.toJson().toString()).apply()
	}

	fun load(ctx: Context): Engine {
		val e = Engine()
		val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
		if (s == null) {
			e.newGame()
		} else {
			try {
				e.fromJson(JSONObject(s))
			} catch (e: Exception) {
				e.newGame()
			}
		}
		return e
	}
}
