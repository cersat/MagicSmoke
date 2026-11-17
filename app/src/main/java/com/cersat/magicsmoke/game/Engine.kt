package com.cersat.magicsmoke.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class Engine(private val rnd: Random = Random.Default) {
	var version by mutableIntStateOf(0)
		private set
	var offline by mutableStateOf<OfflineResult?>(null)

	var money = Rules.START_MONEY
		private set
	var runEarned = 0.0
		private set
	var totalEarned = 0.0
		private set
	var prestige = 0
		private set
	var burns = 0L
		private set
	var rerolls = 0
		private set
	var rerollsTotal = 0
		private set
	var sells = 0L
		private set
	var merges = 0L
		private set
	var boostUntil = 0L
		private set
	var timer = 0.0
		private set
	var running = false
		private set

	val parts = mutableListOf<Part>()
	val deck = mutableListOf<Card>()
	val achievements = mutableSetOf<Ach>()
	val newAchievements = ArrayDeque<Ach>()
	var onPayout: (() -> Unit)? = null

	private var pending = 0.0
	private var nextId = 1
	private var secAcc = 0.0
	private var lastWall = 0L

	fun changed() {
		version++
	}

	// ---------- Расчёты ----------

	fun power(kind: Kind): Long {
		var s = 0L
		for (p in parts) if (p.kind == kind) s += p.power
		return s
	}

	// Доли чипов, конденсаторов, резисторов по мощности
	fun shares(): DoubleArray {
		val c = power(Kind.CHIP).toDouble()
		val k = power(Kind.CAP).toDouble()
		val r = power(Kind.RES).toDouble()
		val t = c + k + r
		if (t <= 0.0) return doubleArrayOf(0.0, 0.0, 0.0)
		return doubleArrayOf(c / t, k / t, r / t)
	}

	fun multiplier(): Double {
		if (parts.isEmpty()) return Rules.MIN_MULT
		val sh = shares()
		val ideal = Rules.IDEAL
		val s = ideal.sum()
		var d = 0.0
		for (i in 0..2) d += abs(sh[i] - ideal[i] / s)
		val maxD = 2.0 * (1.0 - ideal.min() / s)
		val m = Rules.MAX_MULT - (Rules.MAX_MULT - Rules.MIN_MULT) * d / maxD
		return m.coerceIn(Rules.MIN_MULT, Rules.MAX_MULT)
	}

	// Тип, которого не хватает сильнее всего (null — баланс идеален)
	fun deficit(): Kind? {
		if (parts.isEmpty()) return Kind.CHIP
		if (multiplier() >= Rules.MAX_MULT - 0.001) return null
		val sh = shares()
		val s = Rules.IDEAL.sum()
		var best = 0
		var bestGap = Double.NEGATIVE_INFINITY
		for (i in 0..2) {
			val gap = Rules.IDEAL[i] / s - sh[i]
			if (gap > bestGap) {
				bestGap = gap
				best = i
			}
		}
		return when (best) {
			0 -> Kind.CHIP
			1 -> Kind.CAP
			else -> Kind.RES
		}
	}

	// Каждый конденсатор мощности 1 — минус 1% от текущей задержки
	fun delay(): Double = max(Rules.MIN_DELAY, Rules.BASE_DELAY * 0.99.pow(power(Kind.CAP).toDouble()))

	fun resFactor(): Double = max(Rules.MIN_RES_FACTOR, 0.99.pow(power(Kind.RES).toDouble()))

	fun burnChance(kind: Kind, level: Int): Double = kind.baseChance / 1.1.pow(level) * resFactor()

	fun prestigeMul(): Double = 1.0 + prestige * Rules.PRESTIGE_BONUS

	fun boostActive(now: Long) = now < boostUntil

	fun incomePerSec(boost: Boolean): Double =
		power(Kind.CHIP) * Rules.INCOME_PER_POWER * multiplier() * prestigeMul() * (if (boost) 2.0 else 1.0)

	// ---------- Время ----------

	fun update(dt: Double, now: Long) {
		if (!running) return
		lastWall = now
		pending += incomePerSec(boostActive(now)) * dt
		timer += dt
		if (timer >= delay()) {
			timer = 0.0
			if (pending > 0.0) {
				earn(pending)
				pending = 0.0
				onPayout?.invoke()
			}
		}
		secAcc += dt
		while (secAcc >= 1.0) {
			secAcc -= 1.0
			burnTick()
		}
		checkAchievements()
		changed()
	}

	private fun burnTick(): Int {
		val rf = resFactor()
		var n = 0
		val it = parts.iterator()
		while (it.hasNext()) {
			val p = it.next()
			if (rnd.nextDouble() < p.kind.baseChance / 1.1.pow(p.level) * rf) {
				it.remove()
				n++
			}
		}
		if (n > 0) {
			burns += n
			ensureNotStuck()
		}
		return n
	}

	fun resume(now: Long) {
		val elapsed = if (lastWall > 0) (now - lastWall) / 1000 else 0L
		if (elapsed >= Rules.OFFLINE_MIN) {
			val t = min(elapsed, Rules.OFFLINE_MAX)
			val burnT = min(t, Rules.OFFLINE_BURN)
			var earned = 0.0
			var burned = 0
			var inc = incomePerSec(false)
			for (i in 0 until burnT) {
				earned += inc
				val b = burnTick()
				if (b > 0) {
					burned += b
					inc = incomePerSec(false)
				}
			}
			earned += inc * (t - burnT)
			if (earned > 0.0 || burned > 0) {
				earn(earned)
				offline = OfflineResult(t, earned, burned)
			}
		}
		lastWall = now
		running = true
		checkAchievements()
		changed()
	}

	fun pause(now: Long) {
		running = false
		lastWall = now
	}

	private fun earn(x: Double) {
		money += x
		runEarned += x
		totalEarned += x
	}

	fun grant(x: Double) {
		earn(x)
		checkAchievements()
		changed()
	}

	fun startBoost(now: Long) {
		boostUntil = now + Rules.BOOST_MS
		changed()
	}

	fun coinPack(): Double = max(100.0, incomePerSec(false) * 300)

	// ---------- Плата ----------

	fun partAt(cx: Int, cy: Int): Part? = parts.firstOrNull { it.covers(cx, cy) }

	fun fits(x: Int, y: Int, w: Int, h: Int, ignoreId: Int = -1): Boolean {
		if (x < 0 || y < 0 || x + w > Rules.GRID || y + h > Rules.GRID) return false
		for (cy in y until y + h) for (cx in x until x + w) {
			if (parts.any { it.id != ignoreId && it.covers(cx, cy) }) return false
		}
		return true
	}

	fun buy(index: Int, rot: Boolean, cx: Int, cy: Int): PlaceResult {
		val base = deck.getOrNull(index) ?: return PlaceResult.BLOCKED
		val card = base.copy(rot = rot && base.shape == Shape.S1x2)
		if (money + 1e-9 < card.price) return PlaceResult.NO_MONEY
		val x = cx.coerceIn(0, Rules.GRID - card.w)
		val y = cy.coerceIn(0, Rules.GRID - card.h)
		if (!fits(x, y, card.w, card.h)) return PlaceResult.BLOCKED
		money = max(0.0, money - card.price)
		parts += Part(nextId++, card.kind, card.shape, card.level, x, y, card.rot)
		replaceCard(index)
		ensureNotStuck()
		checkAchievements()
		changed()
		return PlaceResult.OK
	}

	fun move(id: Int, x: Int, y: Int): Boolean {
		val i = parts.indexOfFirst { it.id == id }
		if (i < 0) return false
		val p = parts[i]
		if (!fits(x, y, p.w, p.h, id)) return false
		parts[i] = p.copy(x = x, y = y)
		changed()
		return true
	}

	// Объединение: две одинаковые детали (тип, размер, мощность) → одна вдвое мощнее
	fun canMerge(srcId: Int, dstId: Int): Boolean {
		if (srcId == dstId) return false
		val a = parts.firstOrNull { it.id == srcId } ?: return false
		val b = parts.firstOrNull { it.id == dstId } ?: return false
		return a.kind == b.kind && a.shape == b.shape && a.level == b.level && a.level < Rules.MAX_LEVEL
	}

	fun merge(srcId: Int, dstId: Int): Boolean {
		if (!canMerge(srcId, dstId)) return false
		parts.removeAll { it.id == srcId }
		val i = parts.indexOfFirst { it.id == dstId }
		parts[i] = parts[i].copy(level = parts[i].level + 1)
		merges++
		checkAchievements()
		changed()
		return true
	}

	fun rotatePart(id: Int): Boolean {
		val i = parts.indexOfFirst { it.id == id }
		if (i < 0) return false
		val p = parts[i]
		if (p.shape != Shape.S1x2) return false
		val r = p.copy(rot = !p.rot)
		if (!fits(r.x, r.y, r.w, r.h, id)) return false
		parts[i] = r
		changed()
		return true
	}

	fun sell(id: Int) {
		val i = parts.indexOfFirst { it.id == id }
		if (i < 0) return
		val p = parts.removeAt(i)
		money += Rules.sell(p.level, p.shape)
		sells++
		checkAchievements()
		ensureNotStuck()
		changed()
	}

	// ---------- Колода ----------

	private fun randomCard(force: Kind?): Card {
		val kind = force ?: Kind.entries.random(rnd)
		val shape = Shape.entries.random(rnd)
		val budget = max(money, Rules.START_MONEY)
		var top = 0
		while (top < Rules.MAX_LEVEL && Rules.price(top + 1, Shape.S1x1) <= budget) top++
		val level = rnd.nextInt(0, min(Rules.MAX_LEVEL, top + 1) + 1)
		return Card(kind, shape, level)
	}

	private fun newDeck() {
		deck.clear()
		for (k in Kind.entries) {
			deck += Card(k, if (rnd.nextBoolean()) Shape.S1x1 else Shape.S1x2, rnd.nextInt(0, 3))
		}
		while (deck.size < Rules.DECK) deck += randomCard(null)
		deck.shuffle(rnd)
	}

	// Реролл колоды: 50 + 5 × число рероллов в этом забеге
	fun rerollCost(): Double = Rules.REROLL_BASE + Rules.REROLL_STEP * rerolls

	fun reroll(): Boolean {
		val cost = rerollCost()
		if (money + 1e-9 < cost) return false
		money = max(0.0, money - cost)
		rerolls++
		rerollsTotal++
		deck.clear()
		for (k in Kind.entries) deck += randomCard(k)
		while (deck.size < Rules.DECK) deck += randomCard(null)
		deck.shuffle(rnd)
		ensureNotStuck()
		checkAchievements()
		changed()
		return true
	}

	private fun replaceCard(index: Int) {
		val rest = deck.filterIndexed { j, _ -> j != index }
		val missing = Kind.entries.firstOrNull { k -> rest.none { it.kind == k } }
		deck[index] = randomCard(missing)
	}

	// Защита от тупика: нет чипов и ничего не купить — подкладываем дешёвый чип
	private fun ensureNotStuck() {
		if (power(Kind.CHIP) > 0L) return
		if (deck.any { money + 1e-9 >= it.price }) return
		var idx = -1
		var maxPrice = -1.0
		for ((i, c) in deck.withIndex()) {
			val replaceable = c.kind == Kind.CHIP || deck.count { it.kind == c.kind } >= 2
			if (replaceable && c.price > maxPrice) {
				maxPrice = c.price
				idx = i
			}
		}
		if (idx < 0) return
		val cheap = Card(Kind.CHIP, Shape.S1x1, 0)
		deck[idx] = cheap
		if (parts.isEmpty() && money < cheap.price) money = cheap.price
	}

	// ---------- Игра / престиж ----------

	fun newGame() {
		money = Rules.START_MONEY
		runEarned = 0.0
		rerolls = 0
		parts.clear()
		pending = 0.0
		timer = 0.0
		secAcc = 0.0
		newDeck()
		changed()
	}

	fun prestigeGain(): Int = floor(sqrt(runEarned / Rules.PRESTIGE_NEED)).toInt()

	fun doPrestige() {
		val gain = prestigeGain()
		if (gain < 1) return
		prestige += gain
		newGame()
		checkAchievements()
		changed()
	}

	// ---------- Достижения ----------

	private fun unlock(a: Ach) {
		if (achievements.add(a)) newAchievements.addLast(a)
	}

	private fun checkAchievements() {
		if (parts.isNotEmpty()) unlock(Ach.FIRST_PART)
		if (parts.size >= 10) unlock(Ach.TEN_PARTS)
		if (parts.isNotEmpty() && multiplier() >= Rules.MAX_MULT - 0.001) unlock(Ach.PERFECT)
		if (totalEarned >= 1e3) unlock(Ach.EARN_1K)
		if (totalEarned >= 1e6) unlock(Ach.EARN_1M)
		if (totalEarned >= 1e9) unlock(Ach.EARN_1B)
		if (parts.any { it.level == Rules.MAX_LEVEL }) unlock(Ach.MAX_POWER)
		if (parts.any { it.shape == Shape.S3x3 }) unlock(Ach.GIANT)
		if (parts.sumOf { it.w * it.h } >= Rules.GRID * Rules.GRID) unlock(Ach.FULL_BOARD)
		if (burns >= 1) unlock(Ach.FIRST_BURN)
		if (burns >= 100) unlock(Ach.BURN_100)
		if (power(Kind.CAP) > 0 && delay() <= Rules.MIN_DELAY) unlock(Ach.MIN_DELAY)
		if (prestige > 0) unlock(Ach.PRESTIGE)
		if (parts.size >= 25) unlock(Ach.PARTS_25)
		if (Kind.entries.all { k -> parts.any { it.kind == k } }) unlock(Ach.ALL_KINDS)
		if (sells >= 1) unlock(Ach.FIRST_SELL)
		if (rerollsTotal >= 1) unlock(Ach.FIRST_REROLL)
		if (rerollsTotal >= 25) unlock(Ach.REROLL_25)
		if (money >= 1e4) unlock(Ach.HOLD_10K)
		if (totalEarned >= 1e12) unlock(Ach.EARN_1T)
		val inc = incomePerSec(false)
		if (inc >= 100.0) unlock(Ach.INCOME_100)
		if (inc >= 10000.0) unlock(Ach.INCOME_10K)
		if (power(Kind.CHIP) >= 1000) unlock(Ach.CHIPS_1000)
		if (power(Kind.RES) > 0 && resFactor() <= Rules.MIN_RES_FACTOR) unlock(Ach.MAX_PROTECT)
		if (burns >= 1000) unlock(Ach.BURN_1000)
		if (prestige >= 5) unlock(Ach.PRESTIGE_5)
		if (parts.count { it.shape == Shape.S3x3 } >= 9) unlock(Ach.GIANTS_9)
		if (merges >= 1) unlock(Ach.FIRST_MERGE)
	}

	// ---------- Сохранение ----------

	fun toJson(): JSONObject = JSONObject().apply {
		put("money", money)
		put("run", runEarned)
		put("total", totalEarned)
		put("prestige", prestige)
		put("burns", burns)
		put("rerolls", rerolls)
		put("rerollsTotal", rerollsTotal)
		put("sells", sells)
		put("merges", merges)
		put("boost", boostUntil)
		put("pending", pending)
		put("timer", timer)
		put("next", nextId)
		put("wall", lastWall)
		put("parts", JSONArray().apply {
			for (p in parts) put(JSONObject().apply {
				put("id", p.id)
				put("k", p.kind.name)
				put("s", p.shape.name)
				put("l", p.level)
				put("x", p.x)
				put("y", p.y)
				put("r", p.rot)
			})
		})
		put("deck", JSONArray().apply {
			for (c in deck) put(JSONObject().apply {
				put("k", c.kind.name)
				put("s", c.shape.name)
				put("l", c.level)
			})
		})
		put("ach", JSONArray(achievements.map { it.name }))
	}

	fun fromJson(o: JSONObject) {
		money = o.optDouble("money", Rules.START_MONEY)
		runEarned = o.optDouble("run", 0.0)
		totalEarned = o.optDouble("total", 0.0)
		prestige = o.optInt("prestige", 0)
		burns = o.optLong("burns", 0L)
		rerolls = o.optInt("rerolls", 0)
		rerollsTotal = o.optInt("rerollsTotal", 0)
		sells = o.optLong("sells", 0L)
		merges = o.optLong("merges", 0L)
		boostUntil = o.optLong("boost", 0L)
		pending = o.optDouble("pending", 0.0)
		timer = o.optDouble("timer", 0.0)
		nextId = o.optInt("next", 1)
		lastWall = o.optLong("wall", 0L)
		parts.clear()
		o.optJSONArray("parts")?.let { a ->
			for (i in 0 until a.length()) {
				val p = a.getJSONObject(i)
				parts += Part(
					p.getInt("id"), Kind.valueOf(p.getString("k")), Shape.valueOf(p.getString("s")),
					p.getInt("l").coerceIn(0, Rules.MAX_LEVEL), p.getInt("x"), p.getInt("y"), p.optBoolean("r", false)
				)
			}
		}
		deck.clear()
		o.optJSONArray("deck")?.let { a ->
			for (i in 0 until a.length()) {
				val c = a.getJSONObject(i)
				deck += Card(Kind.valueOf(c.getString("k")), Shape.valueOf(c.getString("s")), c.getInt("l").coerceIn(0, Rules.MAX_LEVEL))
			}
		}
		if (deck.size != Rules.DECK) newDeck()
		achievements.clear()
		o.optJSONArray("ach")?.let { a ->
			for (i in 0 until a.length()) runCatching { Ach.valueOf(a.getString(i)) }.getOrNull()?.let { achievements += it }
		}
		if (parts.isNotEmpty()) nextId = max(nextId, parts.maxOf { it.id } + 1)
		ensureNotStuck()
		changed()
	}
}
