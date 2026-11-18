package com.cersat.magicsmoke.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.cersat.magicsmoke.game.Kind
import com.cersat.magicsmoke.game.Rules
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Pal {
	val Bg = Color(0xFF0B0E12)
	val Panel = Color(0xFF151A21)
	val Line = Color(0xFF2A323D)
	val Text = Color(0xFFE6EDF3)
	val Dim = Color(0xFF8B96A3)
	val Gold = Color(0xFFFFC94D)
	val Good = Color(0xFF5BE38A)
	val Mid = Color(0xFFFFD24D)
	val Bad = Color(0xFFFF5C5C)
	val Chip = Color(0xFFB388FF)
	val Cap = Color(0xFF4FC3F7)
	val Res = Color(0xFFFFB74D)
	val Fuse = Color(0xFFFF6B8B)
	val Pcb = Color(0xFF0A1A14)
	val PcbLine = Color(0xFF12301F)
	val Trace = Color(0xFF1F6A45)
	val Copper = Color(0xFFB87333)
	val Silk = Color(0xFFE8F0E8)
}

fun kindColor(k: Kind): Color = when (k) {
	Kind.CHIP -> Pal.Chip
	Kind.CAP -> Pal.Cap
	Kind.RES -> Pal.Res
	Kind.FUSE -> Pal.Fuse
}

fun levelColor(level: Int): Color = Color.hsv((level * 33f + 200f) % 360f, 0.7f, 1f)

private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	textAlign = Paint.Align.CENTER
	typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
}

private fun fitSize(text: String, maxW: Float, size: Float): Float {
	textPaint.textSize = size
	val w = textPaint.measureText(text)
	return if (w > maxW && w > 0f) size * maxW / w else size
}

fun DrawScope.label(text: String, cx: Float, cy: Float, maxW: Float, size: Float, color: Color, alpha: Float) {
	textPaint.textSize = fitSize(text, maxW, size)
	textPaint.color = color.toArgb()
	textPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
	val fm = textPaint.fontMetrics
	val y = cy - (fm.ascent + fm.descent) / 2f
	drawIntoCanvas { it.nativeCanvas.drawText(text, cx, y, textPaint) }
}

// Текст на тёмной подложке — читается на любом фоне
fun DrawScope.pill(text: String, cx: Float, cy: Float, maxW: Float, size: Float, alpha: Float) {
	val s = fitSize(text, maxW * 0.8f, size)
	textPaint.textSize = s
	val tw = textPaint.measureText(text)
	val th = s * 1.1f
	val px = s * 0.3f
	val py = s * 0.12f
	drawRoundRect(
		Color.Black, Offset(cx - tw / 2 - px, cy - th / 2 - py), Size(tw + 2 * px, th + 2 * py),
		CornerRadius(th * 0.3f), alpha = alpha * 0.78f
	)
	label(text, cx, cy, Float.MAX_VALUE, s, Color.White, alpha)
}

// Шелкография-позиционник (U1, C2, R3) в левом верхнем углу детали
private fun DrawScope.silk(tag: String, l: Float, t: Float, m: Float, alpha: Float) {
	val s = m * 0.17f
	textPaint.textSize = s
	val tw = textPaint.measureText(tag)
	label(tag, l + m * 0.07f + tw / 2, t + m * 0.07f + s * 0.5f, Float.MAX_VALUE, s, Pal.Silk, alpha * 0.85f)
}

// led: яркость светодиода чипа 0..1 (мерцание), null — горит ровно
fun DrawScope.drawPart(
	kind: Kind, level: Int, l: Float, t: Float, w: Float, h: Float,
	a: Float = 1f, tag: String? = null, led: Float? = null
) {
	val m = min(w, h)
	val kc = kindColor(kind)
	val fi = m * 0.03f
	val corner = CornerRadius(m * 0.08f)
	// Посадочное место: видно, какие клетки занимает деталь
	drawRoundRect(kc, Offset(l + fi, t + fi), Size(w - 2 * fi, h - 2 * fi), corner, alpha = a * 0.16f)
	drawRoundRect(kc, Offset(l + fi, t + fi), Size(w - 2 * fi, h - 2 * fi), corner, style = Stroke(max(1.5f, m * 0.04f)), alpha = a * 0.9f)

	val inset = m * 0.14f
	val x = l + inset
	val y = t + inset
	val bw = w - 2 * inset
	val bh = h - 2 * inset
	val power = Rules.power(level).toString()

	when (kind) {
		Kind.CHIP -> {
			val pin = m * 0.1f
			val pinColor = Color(0xFFC9CED6)
			val vertical = bh >= bw
			val along = if (vertical) bh else bw
			val n = max(2, (along / (m * 0.2f)).toInt())
			val step = along / n
			for (i in 0 until n) {
				val c = (i + 0.5f) * step
				val pw = step * 0.45f
				if (vertical) {
					drawRect(pinColor, Offset(x, y + c - pw / 2), Size(pin, pw), alpha = a)
					drawRect(pinColor, Offset(x + bw - pin, y + c - pw / 2), Size(pin, pw), alpha = a)
				} else {
					drawRect(pinColor, Offset(x + c - pw / 2, y), Size(pw, pin), alpha = a)
					drawRect(pinColor, Offset(x + c - pw / 2, y + bh - pin), Size(pw, pin), alpha = a)
				}
			}
			val bx = if (vertical) x + pin * 0.8f else x
			val by = if (vertical) y else y + pin * 0.8f
			val bW = if (vertical) bw - pin * 1.6f else bw
			val bH = if (vertical) bh else bh - pin * 1.6f
			val r = CornerRadius(m * 0.06f)
			drawRoundRect(Color(0xFF16181D), Offset(bx, by), Size(bW, bH), r, alpha = a)
			drawRoundRect(Pal.Chip, Offset(bx, by), Size(bW, bH), r, style = Stroke(m * 0.025f), alpha = a * 0.6f)
			val ledPos = Offset(bx + m * 0.1f, by + bH - m * 0.1f)
			val glow = led ?: 1f
			drawCircle(levelColor(level), m * 0.09f, ledPos, alpha = a * 0.35f * glow)
			drawCircle(levelColor(level), m * 0.045f, ledPos, alpha = a * (0.3f + 0.7f * glow))
			label(power, bx + bW / 2, by + bH / 2, bW * 0.86f, m * 0.4f, Color.White, a)
		}
		Kind.CAP -> {
			val body = Color(0xFF1F5FBF)
			val stripe = Color(0xFF9CC7FF)
			val top = Color(0xFFB8C0CC)
			if (abs(bw - bh) < 1f) {
				val c = Offset(x + bw / 2, y + bh / 2)
				val r = bw / 2
				drawCircle(body, r, c, alpha = a)
				drawArc(stripe, -50f, 100f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), alpha = a * 0.85f)
				drawCircle(top, r * 0.72f, c, alpha = a)
				val k = r * 0.5f
				val vent = Color(0xFF7A828E)
				drawLine(vent, Offset(c.x - k, c.y), Offset(c.x + k, c.y), r * 0.06f, alpha = a)
				drawLine(vent, Offset(c.x, c.y - k), Offset(c.x, c.y + k), r * 0.06f, alpha = a)
				pill(power, c.x, c.y, bw, m * 0.36f, a)
			} else {
				val vertical = bh > bw
				drawRoundRect(body, Offset(x, y), Size(bw, bh), CornerRadius(m * 0.2f), alpha = a)
				if (vertical) {
					drawRect(top, Offset(x, y + bh * 0.05f), Size(bw, bh * 0.09f), alpha = a)
					drawRect(stripe, Offset(x + bw * 0.74f, y + bh * 0.2f), Size(bw * 0.12f, bh * 0.72f), alpha = a)
				} else {
					drawRect(top, Offset(x + bw * 0.05f, y), Size(bw * 0.09f, bh), alpha = a)
					drawRect(stripe, Offset(x + bw * 0.2f, y + bh * 0.74f), Size(bw * 0.72f, bh * 0.12f), alpha = a)
				}
				pill(power, x + bw / 2, y + bh / 2, min(bw, bh) * 1.1f, m * 0.36f, a)
			}
		}
		Kind.RES -> {
			// SMD-резистор: чёрный корпус, лужёные контакты по краям, код белым
			val vertical = bh > bw
			val cx = x + bw / 2
			val cy = y + bh / 2
			val long = (if (vertical) bh else bw) * 0.96f
			val short = min((if (vertical) bw else bh) * 0.72f, long * 0.6f)
			val term = long * 0.17f
			rotate(if (vertical) 90f else 0f, Offset(cx, cy)) {
				val bx = cx - long / 2
				val by = cy - short / 2
				val tin = Color(0xFFCDD3DA)
				drawRect(tin, Offset(bx, by), Size(term, short), alpha = a)
				drawRect(tin, Offset(bx + long - term, by), Size(term, short), alpha = a)
				drawRect(Color(0xFF141414), Offset(bx + term, by), Size(long - 2 * term, short), alpha = a)
				drawRect(Color(0xFF2A2A2A), Offset(bx + term, by), Size(long - 2 * term, short * 0.12f), alpha = a)
				label(power, cx, cy, (long - 2 * term) * 0.88f, short * 0.62f, Color.White, a)
			}
		}
		Kind.FUSE -> {
			// Стеклянный предохранитель: металлические колпачки и тонкая нить
			val cx = x + bw / 2
			val cy = y + bh / 2
			val long = bw * 0.96f
			val short = bh * 0.5f
			val cap = long * 0.2f
			val bx = cx - long / 2
			val by = cy - short / 2
			val metal = Color(0xFFCDD3DA)
			drawRoundRect(Color(0xFF9FD8E8), Offset(bx + cap * 0.8f, by), Size(long - cap * 1.6f, short), CornerRadius(short * 0.3f), alpha = a * 0.35f)
			drawRect(metal, Offset(bx, by - short * 0.06f), Size(cap, short * 1.12f), alpha = a)
			drawRect(metal, Offset(bx + long - cap, by - short * 0.06f), Size(cap, short * 1.12f), alpha = a)
			drawLine(Color(0xFFE8E8E8), Offset(bx + cap, cy), Offset(bx + long - cap, cy), short * 0.07f, alpha = a)
			pill("F", cx, cy - short * 0.95f, long * 0.5f, m * 0.26f, a)
		}
	}
	if (tag != null) silk(tag, l, t, m, a)
}
