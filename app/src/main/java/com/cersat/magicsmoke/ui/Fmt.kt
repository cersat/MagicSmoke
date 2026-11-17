package com.cersat.magicsmoke.ui

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.max

// Числа всегда целиком, без сокращений
object Fmt {
	fun int(x: Double): String = BigDecimal(floor(max(0.0, x))).toPlainString()

	fun dec(x: Double, scale: Int): String {
		if (x == 0.0) return "0"
		val s = BigDecimal(x).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
		return if (s.matches(Regex("-?0(\\.0+)?"))) "0" else s
	}

	fun sig(x: Double): String {
		if (x == 0.0) return "0"
		return BigDecimal(x).round(MathContext(3)).stripTrailingZeros().toPlainString()
	}
}
