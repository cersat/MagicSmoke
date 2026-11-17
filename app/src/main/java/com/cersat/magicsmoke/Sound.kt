package com.cersat.magicsmoke

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

// Все звуки синтезируются кодом — никаких чужих файлов
class Sound {
	private val rate = 22050
	private val tracks = mutableListOf<AudioTrack>()

	private val clickTrack = track(synth(0.035) { t ->
		sin(2 * PI * 1600 * t) * exp(-t * 110) * 0.5 + (Random.nextDouble() * 2 - 1) * exp(-t * 300) * 0.25
	})
	private val coinTrack = track(synth(0.2) { t ->
		val f = if (t < 0.06) 1318.5 else 1760.0
		(sin(2 * PI * f * t) + 0.3 * sin(6 * PI * f * t)) * exp(-t * 12) * 0.2
	})
	private val sellTrack = track(synth(0.18) { t ->
		sin(2 * PI * (880 * t - 1200 * t * t)) * exp(-t * 10) * 0.3
	})

	private fun synth(dur: Double, f: (Double) -> Double): ShortArray =
		ShortArray((rate * dur).toInt()) { i -> (f(i.toDouble() / rate).coerceIn(-1.0, 1.0) * 32767).toInt().toShort() }

	private fun track(data: ShortArray): AudioTrack? = try {
		val t = AudioTrack.Builder()
			.setAudioAttributes(
				AudioAttributes.Builder()
					.setUsage(AudioAttributes.USAGE_GAME)
					.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.build()
			)
			.setAudioFormat(
				AudioFormat.Builder()
					.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
					.setSampleRate(rate)
					.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
					.build()
			)
			.setBufferSizeInBytes(data.size * 2)
			.setTransferMode(AudioTrack.MODE_STATIC)
			.build()
		t.write(data, 0, data.size)
		tracks += t
		t
	} catch (ex: Exception) {
		null
	}

	private fun play(t: AudioTrack?) {
		if (t == null) return
		try {
			t.stop()
			t.reloadStaticData()
			t.play()
		} catch (ex: Exception) {
		}
	}

	fun click() = play(clickTrack)
	fun coin() = play(coinTrack)
	fun sell() = play(sellTrack)

	fun release() {
		for (t in tracks) t.release()
		tracks.clear()
	}
}
