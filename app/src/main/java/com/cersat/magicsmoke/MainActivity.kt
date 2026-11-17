package com.cersat.magicsmoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.cersat.magicsmoke.game.Engine
import com.cersat.magicsmoke.game.Save
import com.cersat.magicsmoke.ui.GameScreen
import com.cersat.magicsmoke.ui.Pal

class MainActivity : ComponentActivity() {
	private lateinit var engine: Engine
	private lateinit var sound: Sound

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		engine = Save.load(this)
		sound = Sound()
		engine.onPayout = { sound.coin() }
		Ads.init(this)
		setContent {
			MaterialTheme(
				colorScheme = darkColorScheme(
					primary = Pal.Gold,
					onPrimary = Color(0xFF1A1400),
					surface = Pal.Panel,
					background = Pal.Bg
				)
			) {
				GameScreen(engine, this, sound) { Save.save(this, engine) }
			}
		}
	}

	override fun onResume() {
		super.onResume()
		engine.resume(System.currentTimeMillis())
	}

	override fun onPause() {
		engine.pause(System.currentTimeMillis())
		Save.save(this, engine)
		super.onPause()
	}

	override fun onDestroy() {
		sound.release()
		super.onDestroy()
	}
}
