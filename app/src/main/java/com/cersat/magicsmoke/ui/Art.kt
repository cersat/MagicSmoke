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
	val Pcb = Color(0xFF0A1A14)
	val PcbLine = Color(0xFF143A2B)
	val Copper = Color(0xFFB87333)
}

fun kindColor(k: Kind): Color = when (k) {
	Kind.CHIP -> Pal.Chip
	Kind.CAP -> Pal.Cap
	Kind.RES -> Pal.Res
}

fun levelColor(level: Int): Color = Color.hsv((level * 33f + 200f) % 360f, 0.7f, 1f)

private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	textAlign = Paint.Align.CENTER
	typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
}

fun DrawScope.label(text: String, cx: Float, cy: Float, maxW: Float, size: Float, color: Color, alpha: Float) {
	textPaint.textSize = size
	val w = textPaint.measureText(text)
	if (w > maxW && w > 0f) textPaint.textSize = size * maxW / w
	textPaint.color = color.toArgb()
	textPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
	val fm = textPaint.fontMetrics
	val y = cy - (fm.ascent + fm.descent) / 2f
	drawIntoCanvas { it.nativeCanvas.drawText(text, cx, y, textPaint) }
}

// Цветовой код резистора: две цифры мощности + множитель
private val bandColors = listOf(
	Color(0xFF111111), Color(0xFF7B4A2A), Color(0xFFD8322E), Color(0xFFF2852B), Color(0xFFF5D90A),
	Color(0xFF3DAA4A), Color(0xFF2F6FE0), Color(0xFF8E44D8), Color(0xFF8D8D8D), Color(0xFFF2F2F2)
)

private fun bands(level: Int): List<Color> {
	val s = Rules.power(level).toString().padStart(2, '0')
	val d1 = s[0] - '0'
	val d2 = s[1] - '0'
	val m = (s.length - 2).coerceIn(0, 9)
	return listOf(bandColors[d1], bandColors[d2], bandColors[m], Color(0xFFD4AF37))
}

fun DrawScope.drawPart(kind: Kind, level: Int, l: Float, t: Float, w: Float, h: Float, a: Float = 1f) {
	val m = min(w, h)
	val inset = m * 0.07f
	val x = l + inset
	val y = t + inset
	val bw = w - 2 * inset
	val bh = h - 2 * inset
	val led = levelColor(level)
	val power = Rules.power(level).toString()
	when (kind) {
		Kind.CHIP -> {
			val pin = m * 0.11f
			val pinColor = Color(0xFFC9CED6)
			val vertical = bh >= bw
			val along = if (vertical) bh else bw
			val n = max(2, (along / (m * 0.22f)).toInt())
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
			val r = CornerRadius(m * 0.07f)
			drawRoundRect(Color(0xFF1B1E24), Offset(bx, by), Size(bW, bH), r, alpha = a)
			drawRoundRect(led, Offset(bx, by), Size(bW, bH), r, style = Stroke(m * 0.035f), alpha = a * 0.8f)
			drawCircle(Color(0xFF454B55), m * 0.05f, Offset(bx + m * 0.13f, by + m * 0.13f), alpha = a)
			label(power, bx + bW / 2, by + bH / 2, bW * 0.82f, m * 0.3f, led, a)
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
				drawCircle(top, r * 0.74f, c, alpha = a)
				val k = r * 0.45f
				val vent = Color(0xFF7A828E)
				drawLine(vent, Offset(c.x - k, c.y), Offset(c.x + k, c.y), r * 0.05f, alpha = a)
				drawLine(vent, Offset(c.x, c.y - k), Offset(c.x, c.y + k), r * 0.05f, alpha = a)
				label(power, c.x, c.y, r * 1.25f, r * 0.55f, Color(0xFF0B1A33), a)
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
				label(power, x + bw / 2, y + bh / 2, min(bw, bh) * 0.9f, m * 0.32f, Color.White, a)
			}
		}
		Kind.RES -> {
			val horizontal = bw >= bh
			val lead = Color(0xFF9AA3AD)
			val beige = Color(0xFFD9B98A)
			val long = (if (horizontal) bw else bh) * 0.78f
			val short = min((if (horizontal) bh else bw) * 0.62f, long * 0.45f)
			val cx = x + bw / 2
			val cy = y + bh / 2
			if (horizontal) drawLine(lead, Offset(x, cy), Offset(x + bw, cy), m * 0.06f, alpha = a)
			else drawLine(lead, Offset(cx, y), Offset(cx, y + bh), m * 0.06f, alpha = a)
			val bx = if (horizontal) cx - long / 2 else cx - short / 2
			val by = if (horizontal) cy - short / 2 else cy - long / 2
			val bW = if (horizontal) long else short
			val bH = if (horizontal) short else long
			drawRoundRect(beige, Offset(bx, by), Size(bW, bH), CornerRadius(short * 0.35f), alpha = a)
			val bandW = long * 0.07f
			val pos = floatArrayOf(0.12f, 0.24f, 0.36f, 0.86f)
			val cols = bands(level)
			for (i in 0..3) {
				val p = pos[i] * long
				if (horizontal) drawRect(cols[i], Offset(bx + p - bandW / 2, by), Size(bandW, bH), alpha = a)
				else drawRect(cols[i], Offset(bx, by + p - bandW / 2), Size(bW, bandW), alpha = a)
			}
			val tx = if (horizontal) bx + long * 0.61f else cx
			val ty = if (horizontal) cy else by + long * 0.61f
			val maxW = if (horizontal) long * 0.4f else short * 0.9f
			label(power, tx, ty, maxW, short * 0.55f, Color(0xFF2A1A0A), a)
		}
	}
}
