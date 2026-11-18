package com.cersat.magicsmoke.game

// Базовый шанс перегорания в секунду: чип > резистор > конденсатор > 0
enum class Kind(val baseChance: Double) {
	CHIP(0.0005),
	CAP(0.00005),
	RES(0.0002),
	FUSE(0.0002);

	companion object {
		// Типы, которые участвуют в балансе
		val MAIN = listOf(CHIP, CAP, RES)
	}
}

enum class Shape(val w: Int, val h: Int) {
	S1x1(1, 1),
	S1x2(1, 2),
	S2x2(2, 2),
	S3x3(3, 3);

	val cells: Int get() = w * h
}

object Rules {
	const val GRID = 10
	const val DECK = 8
	const val MAX_LEVEL = 10
	const val START_MONEY = 100.0
	const val BASE_DELAY = 10.0
	const val MIN_DELAY = 1.0
	const val INCOME_PER_POWER = 0.1
	const val MIN_MULT = 0.5
	const val MAX_MULT = 2.0
	const val BALANCE_TOLERANCE = 0.10
	const val MIN_RES_FACTOR = 0.1
	// −0.05% задержки / шанса за каждую единицу мощности конденсаторов / резисторов
	const val CAP_STEP = 0.9995
	const val RES_STEP = 0.9995
	const val AGE_CHANCE_PER_HOUR = 0.00000001
	const val OFFLINE_MAX = 8L * 3600
	const val OFFLINE_BURN = 3600L
	const val OFFLINE_MIN = 60L
	const val BOOST_MS = 10L * 60 * 1000
	const val PRESTIGE_NEED = 1_000_000.0
	const val PRESTIGE_BONUS = 0.10
	const val REROLL_BASE = 50.0
	const val REROLL_STEP = 5.0
	const val QUESTS_PER_DAY = 3
	const val BEST_HOLD = 60.0

	// Идеальная пропорция по суммарной мощности: чипы : конденсаторы : резисторы
	val IDEAL = doubleArrayOf(1.0, 2.0, 3.0)

	fun power(level: Int): Long = 1L shl level
	fun price(level: Int, shape: Shape): Double = power(level) * 11L * shape.cells / 10.0
	fun sell(level: Int, shape: Shape): Double = price(level, shape) * 0.5 + (level + 1) * 2
}

data class Card(val kind: Kind, val shape: Shape, val level: Int, val rot: Boolean = false) {
	val w: Int get() = if (rot) shape.h else shape.w
	val h: Int get() = if (rot) shape.w else shape.h
	val price: Double get() = Rules.price(level, shape)
}

data class Part(
	val id: Int,
	val kind: Kind,
	val shape: Shape,
	val level: Int,
	val x: Int,
	val y: Int,
	val rot: Boolean,
	var age: Long = 0L
) {
	val w: Int get() = if (rot) shape.h else shape.w
	val h: Int get() = if (rot) shape.w else shape.h
	val power: Long get() = Rules.power(level)
	// Вклад детали: мощность × размер в клетках
	val output: Long get() = power * shape.cells
	fun covers(cx: Int, cy: Int) = cx >= x && cx < x + w && cy >= y && cy < y + h
}

enum class PlaceResult { OK, MERGED, NO_MONEY, BLOCKED }

class OfflineResult(val seconds: Long, val earned: Double, val burned: Int)

enum class QuestType { MERGE, BUY, EARN, REROLL, PERFECT, SELL }

class Quest(val type: QuestType, val target: Double, val base: Double, val reward: Double, var claimed: Boolean = false)

enum class FxType { PAYOUT, MERGE, PLACE }

class Fx(val type: FxType, val x: Int, val y: Int, val w: Int, val h: Int, val text: String, val start: Long)

enum class Ach {
	FIRST_PART, TEN_PARTS, PERFECT, EARN_1K, EARN_1M, EARN_1B,
	MAX_POWER, GIANT, FULL_BOARD, FIRST_BURN, BURN_100, MIN_DELAY, PRESTIGE,
	PARTS_25, ALL_KINDS, FIRST_SELL, FIRST_REROLL, REROLL_25, HOLD_10K, EARN_1T,
	INCOME_100, INCOME_10K, CHIPS_1000, MAX_PROTECT, BURN_1000, PRESTIGE_5, GIANTS_9, FIRST_MERGE,
	FIRST_FUSE, FUSE_SAVE, FUSE_SAVE_100, MERGE_100, PARTS_50, REROLL_100, FIRST_QUEST, QUESTS_30,
	PLAY_10H, ROWS_15, STABLE_10M, OFFLINE_8H, BEST_OF_BEST
}
