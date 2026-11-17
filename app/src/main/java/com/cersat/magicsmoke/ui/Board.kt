package com.cersat.magicsmoke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.cersat.magicsmoke.game.Engine
import com.cersat.magicsmoke.game.Kind
import com.cersat.magicsmoke.game.Part
import com.cersat.magicsmoke.game.Rules
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun Board(
	engine: Engine,
	selectedPartId: Int,
	onTapCell: (Int, Int) -> Unit,
	onMove: (Int, Int, Int) -> Unit,
	onMerge: (Int, Int) -> Unit,
	onSwap: (Int, Int) -> Unit,
	modifier: Modifier
) {
	val tap by rememberUpdatedState(onTapCell)
	val move by rememberUpdatedState(onMove)
	val mergeInto by rememberUpdatedState(onMerge)
	val swapWith by rememberUpdatedState(onSwap)
	var dragId by remember { mutableIntStateOf(-1) }
	var finger by remember { mutableStateOf(Offset.Zero) }
	var grab by remember { mutableStateOf(Offset.Zero) }
	val n = Rules.GRID

	fun snap(cs: Float, w: Int, h: Int): Pair<Int, Int> {
		val nx = (finger.x / cs - grab.x).roundToInt().coerceIn(0, n - w)
		val ny = (finger.y / cs - grab.y).roundToInt().coerceIn(0, n - h)
		return nx to ny
	}

	// Другая деталь под пальцем (цель объединения или обмена)
	fun dropTarget(cs: Float, srcId: Int): Part? {
		val t = engine.partAt((finger.x / cs).toInt(), (finger.y / cs).toInt()) ?: return null
		return if (t.id != srcId) t else null
	}

	Canvas(
		modifier
			.pointerInput(Unit) {
				detectTapGestures { p ->
					val cs = size.width / n.toFloat()
					tap((p.x / cs).toInt().coerceIn(0, n - 1), (p.y / cs).toInt().coerceIn(0, n - 1))
				}
			}
			.pointerInput(Unit) {
				detectDragGestures(
					onDragStart = { p ->
						val cs = size.width / n.toFloat()
						val part = engine.partAt((p.x / cs).toInt(), (p.y / cs).toInt())
						if (part != null) {
							dragId = part.id
							grab = Offset(p.x / cs - part.x, p.y / cs - part.y)
							finger = p
						}
					},
					onDrag = { change, amount ->
						if (dragId >= 0) {
							change.consume()
							finger += amount
						}
					},
					onDragEnd = {
						val part = engine.parts.firstOrNull { it.id == dragId }
						if (part != null) {
							val cs = size.width / n.toFloat()
							val target = dropTarget(cs, part.id)
							if (target != null && engine.canMerge(part.id, target.id)) {
								mergeInto(part.id, target.id)
							} else if (target != null) {
								swapWith(part.id, target.id)
							} else {
								val (nx, ny) = snap(cs, part.w, part.h)
								move(part.id, nx, ny)
							}
						}
						dragId = -1
					},
					onDragCancel = { dragId = -1 }
				)
			}
	) {
		engine.version
		val cs = size.width / n
		drawRect(Pal.Pcb)
		for (i in 0..n) {
			val p = i * cs
			drawLine(Pal.PcbLine, Offset(p, 0f), Offset(p, size.height), cs * 0.02f)
			drawLine(Pal.PcbLine, Offset(0f, p), Offset(size.width, p), cs * 0.02f)
		}
		// Дорожки и переходные отверстия — всегда одинаковый рисунок
		val tr = Random(7)
		fun c(v: Int) = (v + 0.5f) * cs
		repeat(26) {
			val a = tr.nextInt(n)
			val b0 = tr.nextInt(n - 1)
			val b1 = (b0 + 1 + tr.nextInt(4)).coerceAtMost(n - 1)
			if (tr.nextBoolean()) drawLine(Pal.Trace, Offset(c(b0), c(a)), Offset(c(b1), c(a)), cs * 0.09f)
			else drawLine(Pal.Trace, Offset(c(a), c(b0)), Offset(c(a), c(b1)), cs * 0.09f)
		}
		val pad = cs * 0.2f
		for (cy in 0 until n) for (cx in 0 until n) {
			drawRect(Pal.Copper, Offset(c(cx) - pad / 2, c(cy) - pad / 2), Size(pad, pad), alpha = 0.55f)
		}
		repeat(14) {
			val vx = tr.nextInt(n)
			val vy = tr.nextInt(n - 1)
			val o = Offset(c(vx), (vy + 1f) * cs)
			drawCircle(Pal.Copper, cs * 0.09f, o, alpha = 0.7f)
			drawCircle(Pal.Pcb, cs * 0.045f, o)
		}
		// Позиционные обозначения как на настоящей плате
		val tags = HashMap<Int, String>()
		var nu = 0
		var nc = 0
		var nr = 0
		for (p in engine.parts) {
			tags[p.id] = when (p.kind) {
				Kind.CHIP -> "U${++nu}"
				Kind.CAP -> "C${++nc}"
				Kind.RES -> "R${++nr}"
			}
		}
		for (p in engine.parts) {
			if (p.id == dragId) continue
			drawPart(p.kind, p.level, p.x * cs, p.y * cs, p.w * cs, p.h * cs, tag = tags[p.id])
			if (p.id == selectedPartId) {
				drawRect(Pal.Gold, Offset(p.x * cs, p.y * cs), Size(p.w * cs, p.h * cs), style = Stroke(cs * 0.06f))
			}
		}
		val dragged = engine.parts.firstOrNull { it.id == dragId }
		if (dragged != null) {
			val target = dropTarget(cs, dragged.id)
			if (target != null) {
				val tc = if (engine.canMerge(dragged.id, target.id)) Pal.Gold else Pal.Cap
				drawRect(tc, Offset(target.x * cs, target.y * cs), Size(target.w * cs, target.h * cs), alpha = 0.35f)
				drawRect(tc, Offset(target.x * cs, target.y * cs), Size(target.w * cs, target.h * cs), style = Stroke(cs * 0.08f))
			} else {
				val (nx, ny) = snap(cs, dragged.w, dragged.h)
				val ok = engine.fits(nx, ny, dragged.w, dragged.h, dragged.id)
				drawRect(if (ok) Pal.Good else Pal.Bad, Offset(nx * cs, ny * cs), Size(dragged.w * cs, dragged.h * cs), alpha = 0.25f)
			}
			drawPart(dragged.kind, dragged.level, finger.x - grab.x * cs, finger.y - grab.y * cs, dragged.w * cs, dragged.h * cs, 0.85f)
		}
	}
}
