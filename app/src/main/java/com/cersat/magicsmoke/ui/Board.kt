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
import com.cersat.magicsmoke.game.Rules
import kotlin.math.roundToInt

@Composable
fun Board(
	engine: Engine,
	selectedPartId: Int,
	onTapCell: (Int, Int) -> Unit,
	onMove: (Int, Int, Int) -> Unit,
	modifier: Modifier
) {
	val tap by rememberUpdatedState(onTapCell)
	val move by rememberUpdatedState(onMove)
	var dragId by remember { mutableIntStateOf(-1) }
	var finger by remember { mutableStateOf(Offset.Zero) }
	var grab by remember { mutableStateOf(Offset.Zero) }
	val n = Rules.GRID

	fun snap(cs: Float, w: Int, h: Int): Pair<Int, Int> {
		val nx = (finger.x / cs - grab.x).roundToInt().coerceIn(0, n - w)
		val ny = (finger.y / cs - grab.y).roundToInt().coerceIn(0, n - h)
		return nx to ny
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
							val (nx, ny) = snap(size.width / n.toFloat(), part.w, part.h)
							move(part.id, nx, ny)
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
		val pad = cs * 0.16f
		for (cy in 0 until n) for (cx in 0 until n) {
			drawRect(Pal.Copper, Offset((cx + 0.5f) * cs - pad / 2, (cy + 0.5f) * cs - pad / 2), Size(pad, pad), alpha = 0.45f)
		}
		for (p in engine.parts) {
			if (p.id == dragId) continue
			drawPart(p.kind, p.level, p.x * cs, p.y * cs, p.w * cs, p.h * cs)
			if (p.id == selectedPartId) {
				drawRect(Pal.Gold, Offset(p.x * cs, p.y * cs), Size(p.w * cs, p.h * cs), style = Stroke(cs * 0.06f))
			}
		}
		val dragged = engine.parts.firstOrNull { it.id == dragId }
		if (dragged != null) {
			val (nx, ny) = snap(cs, dragged.w, dragged.h)
			val ok = engine.fits(nx, ny, dragged.w, dragged.h, dragged.id)
			drawRect(if (ok) Pal.Good else Pal.Bad, Offset(nx * cs, ny * cs), Size(dragged.w * cs, dragged.h * cs), alpha = 0.25f)
			drawPart(dragged.kind, dragged.level, finger.x - grab.x * cs, finger.y - grab.y * cs, dragged.w * cs, dragged.h * cs, 0.85f)
		}
	}
}
