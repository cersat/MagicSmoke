package com.cersat.magicsmoke.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
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
	var resets = 0
		private set
	var burns = 0L
		private set
	var fuseSaves = 0L
		private set
	var merges = 0L
		private set
	var sells = 0L
		private set
	var rerolls = 0
		private set
	var rerollsTotal = 0
		private set
	var partsBought = 0L
		private set
	var questsDone = 0
		private set
	var playSeconds = 0.0
		private set
	var perfectTotal = 0.0
		private set
	var perfectStreak = 0.0
		private set
	var maxIncome = 0.0
		private set
	var bestLevel = -1
		private set
	var boostUntil = 0L
		private set
	var timer = 0.0
		private set
	var running = false
		private set
	var animations = true
		private set

	val parts = mutableListOf<Part>()
	val deck = mutableListOf<Card>()
	val achievements = mutableSetOf<Ach>()
	val newAchievements = ArrayDeque<Ach>()
	val quests = mutableListOf<Quest>()
	val fx = mutableListOf<Fx>()
	var onPayout: (() -> Unit)? = null

	private var questDay = 0
	private var bestStreak = 0.0
	private var pending = 0.0
	private var nextId = 1
	private var secAcc = 0.0
	private var lastWall = 0L

	// Каждый престиж добавляет плате ряд снизу
	val rows: Int get() = Rules.GRID + resets

	fun changed() {
		version++
	}

	fun setAnimations(on: Boolean) {
		animations = on
		if (!on) fx.clear()
		changed()
	}

	// ---------- Расчёты ----------

	fun power(kind: Kind): Long {
		var s = 0L
		for (p in parts) if (p.kind == kind) s += p.output
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

	// Сколько мощности каждого типа нужно для идеала, ничего не убирая
	fun targets(): LongArray {
		val c = power(Kind.CHIP).toDouble()
		val k = power(Kind.CAP).toDouble()
		val r = power(Kind.RES).toDouble()
		val ideal = Rules.IDEAL
		val unit = max(max(c / ideal[0], k / ideal[1]), r / ideal[2])
		return LongArray(3) { i -> ceil(unit * ideal[i] - 1e-9).toLong() }
	}

	fun multiplier(): Double {
		val sh = shares()
		if (sh.sum() <= 0.0) return Rules.MIN_MULT
		val ideal = Rules.IDEAL
		val s = ideal.sum()
		var d = 0.0
		for (i in 0..2) d += abs(sh[i] - ideal[i] / s)
		val maxD = 2.0 * (1.0 - ideal.min() / s)
		// Отклонение до BALANCE_TOLERANCE считается идеалом, дальше — линейно до минимума
		val x = ((d / maxD - Rules.BALANCE_TOLERANCE) / (1.0 - Rules.BALANCE_TOLERANCE)).coerceIn(0.0, 1.0)
		val m = Rules.MAX_MULT - (Rules.MAX_MULT - Rules.MIN_MULT) * x
		return m.coerceIn(Rules.MIN_MULT, Rules.MAX_MULT)
	}

	fun isPerfect(): Boolean = shares().sum() > 0.0 && multiplier() >= Rules.MAX_MULT - 0.001

	// Тип, которого не хватает сильнее всего (null — баланс идеален)
	fun deficit(): Kind? {
		if (shares().sum() <= 0.0) return Kind.CHIP
		if (isPerfect()) return null
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
		return Kind.MAIN[best]
	}

	// Каждая единица мощности конденсаторов — минус 0.05% от текущей задержки
	fun delay(): Double = max(Rules.MIN_DELAY, Rules.BASE_DELAY * Rules.CAP_STEP.pow(power(Kind.CAP).toDouble()))

	fun resFactor(): Double = max(Rules.MIN_RES_FACTOR, Rules.RES_STEP.pow(power(Kind.RES).toDouble()))

	// База / 1.1 за каждую ступень мощности + износ за часы работы детали, всё × защита резисторов
	fun burnChance(p: Part): Double =
		(p.kind.baseChance / 1.1.pow(p.level) + p.age / 3600.0 * Rules.AGE_CHANCE_PER_HOUR) * resFactor()

	fun prestigeMul(): Double = 1.0 + prestige * Rules.PRESTIGE_BONUS

	fun boostActive(now: Long) = now < boostUntil

	fun incomePerSec(boost: Boolean): Double =
		power(Kind.CHIP) * Rules.INCOME_PER_POWER * multiplier() * prestigeMul() * (if (boost) 2.0 else 1.0)

	// ---------- Время ----------

	fun update(dt: Double, now: Long) {
		if (!running) return
		lastWall = now
		refreshQuests()
		playSeconds += dt
		val inc = incomePerSec(boostActive(now))
		pending += inc * dt
		maxIncome = max(maxIncome, incomePerSec(false))
		if (isPerfect()) {
			perfectTotal += dt
			perfectStreak += dt
		} else {
			perfectStreak = 0.0
		}
		val best = parts.isNotEmpty() && parts.all { it.kind != Kind.FUSE && it.level == Rules.MAX_LEVEL } && isPerfect()
		bestStreak = if (best) bestStreak + dt else 0.0
		timer += dt
		if (timer >= delay()) {
			timer = 0.0
			if (pending > 0.0) {
				earn(pending)
				payoutFx(pending)
				pending = 0.0
				onPayout?.invoke()
			}
		}
		secAcc += dt
		while (secAcc >= 1.0) {
			secAcc -= 1.0
			for (p in parts) p.age++
			burnTick()
		}
		checkAchievements()
		changed()
	}

	// Перегорание: если что-то должно сгореть, вместо него сгорает случайный предохранитель
	private fun burnTick(): Int {
		val hits = parts.filter { rnd.nextDouble() < burnChance(it) }
		if (hits.isEmpty()) return 0
		var removed = 0
		for (h in hits) {
			val fuses = parts.filter { it.kind == Kind.FUSE }
			if (fuses.isNotEmpty()) {
				parts.remove(fuses.random(rnd))
				fuseSaves++
				removed++
			} else if (parts.remove(h)) {
				removed++
			}
		}
		burns += removed
		ensureNotStuck()
		return removed
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
				for (p in parts) p.age++
				val b = burnTick()
				if (b > 0) {
					burned += b
					inc = incomePerSec(false)
				}
			}
			earned += inc * (t - burnT)
			for (p in parts) p.age += t - burnT
			if (elapsed >= Rules.OFFLINE_MAX) unlock(Ach.OFFLINE_8H)
			if (earned > 0.0 || burned > 0) {
				earn(earned)
				offline = OfflineResult(t, earned, burned)
			}
		}
		lastWall = now
		running = true
		refreshQuests()
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

	// «+N» над каждым чипом — его доля выплаты
	private fun payoutFx(total: Double) {
		if (!animations) return
		val chips = parts.filter { it.kind == Kind.CHIP }
		val sum = chips.sumOf { it.output }.toDouble()
		if (sum <= 0.0) return
		for (p in chips.take(40)) {
			val share = total * p.output / sum
			val text = if (share >= 10.0) floor(share).toLong().toString() else (floor(share * 10) / 10).toString()
			addFx(FxType.PAYOUT, p.x, p.y, p.w, p.h, "+$text")
		}
	}

	private fun addFx(type: FxType, x: Int, y: Int, w: Int, h: Int, text: String = "") {
		if (!animations) return
		fx += Fx(type, x, y, w, h, text, System.currentTimeMillis())
	}

	// ---------- Плата ----------

	fun partAt(cx: Int, cy: Int): Part? = parts.firstOrNull { it.covers(cx, cy) }

	fun fits(x: Int, y: Int, w: Int, h: Int, ignoreId: Int = -1): Boolean {
		if (x < 0 || y < 0 || x + w > Rules.GRID || y + h > rows) return false
		for (cy in y until y + h) for (cx in x until x + w) {
			if (parts.any { it.id != ignoreId && it.covers(cx, cy) }) return false
		}
		return true
	}

	private fun levelUp(i: Int) {
		val p = parts[i]
		parts[i] = p.copy(level = p.level + 1, age = 0L)
		bestLevel = max(bestLevel, p.level + 1)
		merges++
		addFx(FxType.MERGE, p.x, p.y, p.w, p.h)
	}

	fun buy(index: Int, rot: Boolean, cx: Int, cy: Int): PlaceResult {
		val base = deck.getOrNull(index) ?: return PlaceResult.BLOCKED
		val card = base.copy(rot = rot && base.shape == Shape.S1x2)
		if (money + 1e-9 < card.price) return PlaceResult.NO_MONEY
		// Тап по такой же детали — покупка сразу с объединением
		val same = partAt(cx, cy)
		if (same != null && same.kind != Kind.FUSE && same.kind == card.kind && same.shape == card.shape &&
			same.level == card.level && same.level < Rules.MAX_LEVEL
		) {
			money = max(0.0, money - card.price)
			partsBought++
			levelUp(parts.indexOfFirst { it.id == same.id })
			replaceCard(index)
			ensureNotStuck()
			checkAchievements()
			changed()
			return PlaceResult.MERGED
		}
		val x = cx.coerceIn(0, Rules.GRID - card.w)
		val y = cy.coerceIn(0, rows - card.h)
		if (!fits(x, y, card.w, card.h)) return PlaceResult.BLOCKED
		money = max(0.0, money - card.price)
		partsBought++
		parts += Part(nextId++, card.kind, card.shape, card.level, x, y, card.rot)
		if (card.kind != Kind.FUSE) bestLevel = max(bestLevel, card.level)
		addFx(FxType.PLACE, x, y, card.w, card.h)
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
		return a.kind != Kind.FUSE && a.kind == b.kind && a.shape == b.shape && a.level == b.level && a.level < Rules.MAX_LEVEL
	}

	fun merge(srcId: Int, dstId: Int): Boolean {
		if (!canMerge(srcId, dstId)) return false
		parts.removeAll { it.id == srcId }
		levelUp(parts.indexOfFirst { it.id == dstId })
		checkAchievements()
		changed()
		return true
	}

	// Обмен местами двух деталей (если обе влезают на новые места)
	fun swap(aId: Int, bId: Int): Boolean {
		if (aId == bId) return false
		val ia = parts.indexOfFirst { it.id == aId }
		val ib = parts.indexOfFirst { it.id == bId }
		if (ia < 0 || ib < 0) return false
		val a = parts[ia]
		val b = parts[ib]
		val na = a.copy(x = b.x.coerceIn(0, Rules.GRID - a.w), y = b.y.coerceIn(0, rows - a.h))
		val nb = b.copy(x = a.x.coerceIn(0, Rules.GRID - b.w), y = a.y.coerceIn(0, rows - b.h))
		val others = parts.filter { it.id != aId && it.id != bId }
		fun ok(p: Part, other: Part): Boolean {
			for (cy in p.y until p.y + p.h) for (cx in p.x until p.x + p.w) {
				if (other.covers(cx, cy) || others.any { it.covers(cx, cy) }) return false
			}
			return true
		}
		if (!ok(na, nb) || !ok(nb, na)) return false
		parts[ia] = na
		parts[ib] = nb
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
		ensureNotStuck()
		checkAchievements()
		changed()
	}

	// ---------- Колода ----------

	private fun randomKind(): Kind {
		// Предохранители выпадают реже основных деталей
		val r = rnd.nextInt(7)
		return if (r == 6) Kind.FUSE else Kind.MAIN[r / 2]
	}

	private fun randomCard(force: Kind?): Card {
		val kind = force ?: randomKind()
		if (kind == Kind.FUSE) return Card(Kind.FUSE, Shape.S1x1, 0)
		val shape = Shape.entries.random(rnd)
		val budget = max(money, Rules.START_MONEY)
		var top = 0
		while (top < Rules.MAX_LEVEL && Rules.price(top + 1, Shape.S1x1) <= budget) top++
		val level = rnd.nextInt(0, min(Rules.MAX_LEVEL, top + 1) + 1)
		return Card(kind, shape, level)
	}

	private fun fillDeck(cheap: Boolean) {
		deck.clear()
		for (k in Kind.MAIN) {
			deck += if (cheap) Card(k, if (rnd.nextBoolean()) Shape.S1x1 else Shape.S1x2, rnd.nextInt(0, 3)) else randomCard(k)
		}
		while (deck.size < Rules.DECK) deck += randomCard(null)
		deck.shuffle(rnd)
	}

	private fun replaceCard(index: Int) {
		val rest = deck.filterIndexed { j, _ -> j != index }
		val missing = Kind.MAIN.firstOrNull { k -> rest.none { it.kind == k } }
		deck[index] = randomCard(missing)
	}

	// Реролл колоды: 50 + 5 × число рероллов в этом забеге
	fun rerollCost(): Double = Rules.REROLL_BASE + Rules.REROLL_STEP * rerolls

	fun reroll(): Boolean {
		val cost = rerollCost()
		if (money + 1e-9 < cost) return false
		money = max(0.0, money - cost)
		rerolls++
		rerollsTotal++
		fillDeck(false)
		ensureNotStuck()
		checkAchievements()
		changed()
		return true
	}

	// Защита от тупика: нет чипов и ничего не купить — подкладываем дешёвый чип
	private fun ensureNotStuck() {
		if (power(Kind.CHIP) > 0L) return
		if (deck.any { money + 1e-9 >= it.price }) return
		var idx = -1
		var maxPrice = -1.0
		for ((i, c) in deck.withIndex()) {
			val replaceable = c.kind == Kind.CHIP || c.kind == Kind.FUSE || deck.count { it.kind == c.kind } >= 2
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

	// ---------- Ежедневные задания ----------

	private fun dayKey(): Int {
		val c = Calendar.getInstance()
		return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
	}

	fun questCounter(t: QuestType): Double = when (t) {
		QuestType.MERGE -> merges.toDouble()
		QuestType.BUY -> partsBought.toDouble()
		QuestType.EARN -> totalEarned
		QuestType.REROLL -> rerollsTotal.toDouble()
		QuestType.PERFECT -> perfectTotal
		QuestType.SELL -> sells.toDouble()
	}

	fun questProgress(q: Quest): Double = (questCounter(q.type) - q.base).coerceIn(0.0, q.target)

	fun questDone(q: Quest): Boolean = questProgress(q) >= q.target

	private fun refreshQuests() {
		val day = dayKey()
		if (day == questDay && quests.isNotEmpty()) return
		questDay = day
		quests.clear()
		val inc = incomePerSec(false)
		val reward = max(150.0, floor(inc * 600))
		for (t in QuestType.entries.shuffled(rnd).take(Rules.QUESTS_PER_DAY)) {
			val target = when (t) {
				QuestType.MERGE -> rnd.nextInt(3, 9).toDouble()
				QuestType.BUY -> rnd.nextInt(5, 16).toDouble()
				QuestType.EARN -> max(500.0, floor(inc * 1200))
				QuestType.REROLL -> rnd.nextInt(2, 6).toDouble()
				QuestType.PERFECT -> rnd.nextInt(5, 16) * 60.0
				QuestType.SELL -> rnd.nextInt(3, 9).toDouble()
			}
			quests += Quest(t, target, questCounter(t), reward)
		}
	}

	fun claim(q: Quest) {
		if (q.claimed || !questDone(q)) return
		q.claimed = true
		questsDone++
		earn(q.reward)
		checkAchievements()
		changed()
	}

	fun claimable(): Int = quests.count { !it.claimed && questDone(it) }

	// ---------- Игра / престиж ----------

	fun newGame() {
		money = Rules.START_MONEY
		runEarned = 0.0
		rerolls = 0
		parts.clear()
		fx.clear()
		pending = 0.0
		timer = 0.0
		secAcc = 0.0
		perfectStreak = 0.0
		bestStreak = 0.0
		fillDeck(true)
		changed()
	}

	fun prestigeGain(): Int = floor(sqrt(runEarned / Rules.PRESTIGE_NEED)).toInt()

	fun doPrestige() {
		val gain = prestigeGain()
		if (gain < 1) return
		prestige += gain
		resets++
		newGame()
		checkAchievements()
		changed()
	}

	// ---------- Достижения ----------

	private fun unlock(a: Ach) {
		if (achievements.add(a)) newAchievements.addLast(a)
	}

	private fun checkAchievements() {
		val inc = incomePerSec(false)
		if (parts.isNotEmpty()) unlock(Ach.FIRST_PART)
		if (parts.size >= 10) unlock(Ach.TEN_PARTS)
		if (parts.size >= 25) unlock(Ach.PARTS_25)
		if (parts.size >= 50) unlock(Ach.PARTS_50)
		if (isPerfect()) unlock(Ach.PERFECT)
		if (totalEarned >= 1e3) unlock(Ach.EARN_1K)
		if (totalEarned >= 1e6) unlock(Ach.EARN_1M)
		if (totalEarned >= 1e9) unlock(Ach.EARN_1B)
		if (totalEarned >= 1e12) unlock(Ach.EARN_1T)
		if (parts.any { it.kind != Kind.FUSE && it.level == Rules.MAX_LEVEL }) unlock(Ach.MAX_POWER)
		if (parts.any { it.shape == Shape.S3x3 }) unlock(Ach.GIANT)
		if (parts.count { it.shape == Shape.S3x3 } >= 9) unlock(Ach.GIANTS_9)
		if (parts.sumOf { it.w * it.h } >= Rules.GRID * rows) unlock(Ach.FULL_BOARD)
		if (burns >= 1) unlock(Ach.FIRST_BURN)
		if (burns >= 100) unlock(Ach.BURN_100)
		if (burns >= 1000) unlock(Ach.BURN_1000)
		if (power(Kind.CAP) > 0 && delay() <= Rules.MIN_DELAY) unlock(Ach.MIN_DELAY)
		if (prestige > 0) unlock(Ach.PRESTIGE)
		if (prestige >= 5) unlock(Ach.PRESTIGE_5)
		if (Kind.MAIN.all { k -> parts.any { it.kind == k } }) unlock(Ach.ALL_KINDS)
		if (sells >= 1) unlock(Ach.FIRST_SELL)
		if (rerollsTotal >= 1) unlock(Ach.FIRST_REROLL)
		if (rerollsTotal >= 25) unlock(Ach.REROLL_25)
		if (rerollsTotal >= 100) unlock(Ach.REROLL_100)
		if (money >= 1e4) unlock(Ach.HOLD_10K)
		if (inc >= 100.0) unlock(Ach.INCOME_100)
		if (inc >= 10000.0) unlock(Ach.INCOME_10K)
		if (power(Kind.CHIP) >= 1000) unlock(Ach.CHIPS_1000)
		if (power(Kind.RES) > 0 && resFactor() <= Rules.MIN_RES_FACTOR) unlock(Ach.MAX_PROTECT)
		if (merges >= 1) unlock(Ach.FIRST_MERGE)
		if (merges >= 100) unlock(Ach.MERGE_100)
		if (parts.any { it.kind == Kind.FUSE }) unlock(Ach.FIRST_FUSE)
		if (fuseSaves >= 1) unlock(Ach.FUSE_SAVE)
		if (fuseSaves >= 100) unlock(Ach.FUSE_SAVE_100)
		if (questsDone >= 1) unlock(Ach.FIRST_QUEST)
		if (questsDone >= 30) unlock(Ach.QUESTS_30)
		if (playSeconds >= 10 * 3600) unlock(Ach.PLAY_10H)
		if (rows >= 15) unlock(Ach.ROWS_15)
		if (perfectStreak >= 600) unlock(Ach.STABLE_10M)
		if (bestStreak >= Rules.BEST_HOLD) unlock(Ach.BEST_OF_BEST)
	}

	// ---------- Сохранение ----------

	fun toJson(): JSONObject = JSONObject().apply {
		put("money", money)
		put("run", runEarned)
		put("total", totalEarned)
		put("prestige", prestige)
		put("resets", resets)
		put("burns", burns)
		put("fuseSaves", fuseSaves)
		put("merges", merges)
		put("sells", sells)
		put("rerolls", rerolls)
		put("rerollsTotal", rerollsTotal)
		put("bought", partsBought)
		put("questsDone", questsDone)
		put("play", playSeconds)
		put("perfectTotal", perfectTotal)
		put("maxIncome", maxIncome)
		put("bestLevel", bestLevel)
		put("boost", boostUntil)
		put("pending", pending)
		put("timer", timer)
		put("next", nextId)
		put("wall", lastWall)
		put("anim", animations)
		put("questDay", questDay)
		put("parts", JSONArray().apply {
			for (p in parts) put(JSONObject().apply {
				put("id", p.id)
				put("k", p.kind.name)
				put("s", p.shape.name)
				put("l", p.level)
				put("x", p.x)
				put("y", p.y)
				put("r", p.rot)
				put("a", p.age)
			})
		})
		put("deck", JSONArray().apply {
			for (c in deck) put(JSONObject().apply {
				put("k", c.kind.name)
				put("s", c.shape.name)
				put("l", c.level)
			})
		})
		put("quests", JSONArray().apply {
			for (q in quests) put(JSONObject().apply {
				put("t", q.type.name)
				put("target", q.target)
				put("base", q.base)
				put("reward", q.reward)
				put("claimed", q.claimed)
			})
		})
		put("ach", JSONArray(achievements.map { it.name }))
	}

	fun fromJson(o: JSONObject) {
		money = o.optDouble("money", Rules.START_MONEY)
		runEarned = o.optDouble("run", 0.0)
		totalEarned = o.optDouble("total", 0.0)
		prestige = o.optInt("prestige", 0)
		resets = o.optInt("resets", 0)
		burns = o.optLong("burns", 0L)
		fuseSaves = o.optLong("fuseSaves", 0L)
		merges = o.optLong("merges", 0L)
		sells = o.optLong("sells", 0L)
		rerolls = o.optInt("rerolls", 0)
		rerollsTotal = o.optInt("rerollsTotal", 0)
		partsBought = o.optLong("bought", 0L)
		questsDone = o.optInt("questsDone", 0)
		playSeconds = o.optDouble("play", 0.0)
		perfectTotal = o.optDouble("perfectTotal", 0.0)
		maxIncome = o.optDouble("maxIncome", 0.0)
		bestLevel = o.optInt("bestLevel", -1)
		boostUntil = o.optLong("boost", 0L)
		pending = o.optDouble("pending", 0.0)
		timer = o.optDouble("timer", 0.0)
		nextId = o.optInt("next", 1)
		lastWall = o.optLong("wall", 0L)
		animations = o.optBoolean("anim", true)
		questDay = o.optInt("questDay", 0)
		parts.clear()
		o.optJSONArray("parts")?.let { a ->
			for (i in 0 until a.length()) {
				val p = a.getJSONObject(i)
				parts += Part(
					p.getInt("id"), Kind.valueOf(p.getString("k")), Shape.valueOf(p.getString("s")),
					p.getInt("l").coerceIn(0, Rules.MAX_LEVEL), p.getInt("x"), p.getInt("y"),
					p.optBoolean("r", false), p.optLong("a", 0L)
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
		if (deck.size != Rules.DECK) fillDeck(true)
		quests.clear()
		o.optJSONArray("quests")?.let { a ->
			for (i in 0 until a.length()) {
				val q = a.getJSONObject(i)
				runCatching {
					Quest(
						QuestType.valueOf(q.getString("t")), q.getDouble("target"), q.getDouble("base"),
						q.getDouble("reward"), q.optBoolean("claimed", false)
					)
				}.getOrNull()?.let { quests += it }
			}
		}
		achievements.clear()
		o.optJSONArray("ach")?.let { a ->
			for (i in 0 until a.length()) runCatching { Ach.valueOf(a.getString(i)) }.getOrNull()?.let { achievements += it }
		}
		if (parts.isNotEmpty()) nextId = max(nextId, parts.maxOf { it.id } + 1)
		ensureNotStuck()
		changed()
	}
}
