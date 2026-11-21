package com.cersat.magicsmoke.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cersat.magicsmoke.Ads
import com.cersat.magicsmoke.R
import com.cersat.magicsmoke.Sound
import com.cersat.magicsmoke.game.Ach
import com.cersat.magicsmoke.game.Card
import com.cersat.magicsmoke.game.Engine
import com.cersat.magicsmoke.game.Kind
import com.cersat.magicsmoke.game.PlaceResult
import com.cersat.magicsmoke.game.QuestType
import com.cersat.magicsmoke.game.Rules
import com.cersat.magicsmoke.game.Shape
import kotlinx.coroutines.delay

@Composable
fun GameScreen(engine: Engine, activity: Activity, sound: Sound, onSave: () -> Unit) {
	val ctx = LocalContext.current
	val save by rememberUpdatedState(onSave)
	var selCard by remember { mutableIntStateOf(-1) }
	var selPocket by remember { mutableIntStateOf(-1) }
	var cardRot by remember { mutableStateOf(false) }
	var selPart by remember { mutableIntStateOf(-1) }
	var message by remember { mutableStateOf<String?>(null) }
	var showAch by remember { mutableStateOf(false) }
	var showPrestige by remember { mutableStateOf(false) }
	var showQuests by remember { mutableStateOf(false) }
	var showStats by remember { mutableStateOf(false) }
	var showSettings by remember { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		var last = System.nanoTime()
		var saveAcc = 0.0
		while (true) {
			delay(100)
			val t = System.nanoTime()
			val dt = ((t - last) / 1e9).coerceIn(0.0, 1.0)
			last = t
			if (engine.running) {
				engine.update(dt, System.currentTimeMillis())
				saveAcc += dt
				if (saveAcc >= 5.0) {
					saveAcc = 0.0
					save()
				}
			}
		}
	}
	LaunchedEffect(message) {
		if (message != null) {
			delay(2000)
			message = null
		}
	}

	@Suppress("UNUSED_VARIABLE")
	val tick = engine.version
	val now = System.currentTimeMillis()
	val boost = engine.boostActive(now)
	val income = engine.incomePerSec(boost)
	val mult = engine.multiplier()
	val delaySec = engine.delay()

	Box(Modifier.fillMaxSize().background(Pal.Bg)) {
		Column(
			Modifier
				.fillMaxSize()
				.systemBarsPadding()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 10.dp, vertical = 6.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// Верхняя панель
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text(stringResource(R.string.coins), color = Pal.Dim, fontSize = 12.sp)
					Text(
						Fmt.int(engine.money), color = Pal.Gold, fontSize = 24.sp,
						fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
					)
					Text(
						stringResource(R.string.income, Fmt.dec(income * delaySec, 1), Fmt.dec(delaySec, 2)), color = Pal.Good,
						fontSize = 13.sp, fontFamily = FontFamily.Monospace
					)
				}
				TextButton(onClick = { showAch = true }) {
					Text(stringResource(R.string.achievements_short, engine.achievements.size, Ach.entries.size), color = Pal.Text)
				}
				TextButton(onClick = { showPrestige = true }) {
					Text(stringResource(R.string.prestige), color = Pal.Gold)
				}
			}

			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				val cl = engine.claimable()
				TextButton(onClick = { showQuests = true }) {
					Text(
						if (cl > 0) stringResource(R.string.quests_ready, cl) else stringResource(R.string.quests),
						color = if (cl > 0) Pal.Good else Pal.Text
					)
				}
				TextButton(onClick = { showStats = true }) { Text(stringResource(R.string.stats), color = Pal.Text) }
				TextButton(onClick = { showSettings = true }) { Text(stringResource(R.string.settings), color = Pal.Text) }
			}

			// Баланс, задержка, защита
			Column(
				Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Pal.Panel).padding(10.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						stringResource(R.string.multiplier, Fmt.dec(mult, 2)),
						color = if (mult >= 1.8) Pal.Good else if (mult >= 1.2) Pal.Mid else Pal.Bad,
						fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
					)
					val d = engine.deficit()
					Text(
						if (d == null) stringResource(R.string.balance_ok) else stringResource(R.string.need_more, needName(d)),
						color = Pal.Dim, fontSize = 12.sp
					)
				}
				BalanceBar(engine.shares(), Modifier.fillMaxWidth().height(16.dp))
				// Суммарная мощность каждого типа и сколько нужно для идеала 1:2:3
				val tg = engine.targets()
				Row {
					for ((i, k) in Kind.MAIN.withIndex()) {
						val have = engine.power(k)
						Column(Modifier.weight(1f)) {
							Text("${kindName(k)} · ${Rules.IDEAL[i].toInt()}", color = kindColor(k), fontSize = 11.sp, maxLines = 1)
							Text(
								"$have", color = Pal.Text,
								fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1
							)
							Text(
								if (have >= tg[i]) "✓" else stringResource(R.string.need_plus, (tg[i] - have).toString()),
								color = if (have >= tg[i]) Pal.Good else kindColor(k),
								fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1
							)
						}
					}
				}
				Text(stringResource(R.string.delay, Fmt.dec(delaySec, 2)), color = Pal.Text, fontSize = 13.sp)
				LinearProgressIndicator(
					progress = { (engine.timer / delaySec).toFloat().coerceIn(0f, 1f) },
					modifier = Modifier.fillMaxWidth(),
					color = Pal.Cap,
					trackColor = Pal.Line
				)
				Text(
					stringResource(R.string.protection, Fmt.dec((1.0 - engine.resFactor()) * 100.0, 1)),
					color = Pal.Text, fontSize = 13.sp
				)
			}

			// Плата
			Board(
				engine = engine,
				selectedPartId = selPart,
				onTapCell = { cx, cy ->
					if (selCard >= 0 || selPocket >= 0) {
						val res = if (selCard >= 0) engine.buy(selCard, cardRot, cx, cy) else engine.fromPocket(selPocket, cardRot, cx, cy)
						when (res) {
							PlaceResult.OK -> {
								sound.click()
								selCard = -1
								selPocket = -1
								cardRot = false
							}
							PlaceResult.MERGED -> {
								sound.merge()
								selCard = -1
								selPocket = -1
								cardRot = false
							}
							PlaceResult.NO_MONEY -> message = ctx.getString(R.string.err_money)
							PlaceResult.BLOCKED -> message = ctx.getString(R.string.err_blocked)
						}
					} else {
						selPart = engine.partAt(cx, cy)?.id ?: -1
					}
				},
				onMove = { id, x, y ->
					if (engine.move(id, x, y)) {
						sound.click()
						selPart = id
					} else {
						message = ctx.getString(R.string.err_blocked)
					}
				},
				onMerge = { src, dst ->
					if (engine.merge(src, dst)) {
						sound.merge()
						selPart = dst
					}
				},
				onSwap = { a, b ->
					if (engine.swap(a, b)) {
						sound.click()
						selPart = a
					} else {
						message = ctx.getString(R.string.err_blocked)
					}
				},
				modifier = Modifier.fillMaxWidth().aspectRatio(Rules.GRID.toFloat() / engine.rows).clip(RoundedCornerShape(10.dp))
			)

			// Информация о выбранном
			val part = engine.parts.firstOrNull { it.id == selPart }
			val card = engine.deck.getOrNull(selCard)
			val pocketPart = engine.pocket.getOrNull(selPocket)
			val msg = message
			Row(
				Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).background(Pal.Panel)
					.padding(horizontal = 10.dp, vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				when {
					msg != null -> Text(msg, color = Pal.Bad, fontSize = 13.sp)
					part != null -> {
						Column(Modifier.weight(1f)) {
							Text(
								if (part.kind == Kind.FUSE) "${kindName(part.kind)} · ${part.w}×${part.h}"
								else "${kindName(part.kind)} · ${part.power} × ${part.shape.cells} = ${part.output} · ${part.w}×${part.h}",
								color = kindColor(part.kind), fontSize = 13.sp, fontWeight = FontWeight.Bold
							)
							Text(
								stringResource(R.string.burn_chance, Fmt.sig(engine.burnChance(part) * 100.0)),
								color = Pal.Dim, fontSize = 11.sp
							)
						}
						if (part.shape == Shape.S1x2) {
							TextButton(onClick = {
								if (engine.rotatePart(part.id)) sound.click() else message = ctx.getString(R.string.err_blocked)
							}) { Text(stringResource(R.string.rotate), fontSize = 12.sp) }
						}
						TextButton(onClick = {
							if (engine.toPocket(part.id)) {
								sound.click()
								selPart = -1
							} else {
								message = ctx.getString(R.string.err_pocket_full)
							}
						}) { Text(stringResource(R.string.to_pocket), fontSize = 12.sp) }
						Button(onClick = {
							engine.sell(part.id)
							sound.sell()
							selPart = -1
						}) {
							Text(stringResource(R.string.sell_for, Fmt.dec(Rules.sell(part.level, part.shape), 2)), fontSize = 12.sp)
						}
					}
					card != null || pocketPart != null -> {
						Text(stringResource(R.string.hint_place), color = Pal.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
						if ((card?.shape ?: pocketPart?.shape) == Shape.S1x2) {
							TextButton(onClick = { cardRot = !cardRot }) { Text(stringResource(R.string.rotate)) }
						}
					}
					else -> Text(stringResource(R.string.hint_select), color = Pal.Dim, fontSize = 13.sp)
				}
			}

			// Карман
			Text(stringResource(R.string.pocket, engine.pocket.size, Rules.POCKET), color = Pal.Dim, fontSize = 12.sp)
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				for (i in 0 until Rules.POCKET) {
					val p = engine.pocket.getOrNull(i)
					val shape = RoundedCornerShape(8.dp)
					val sel = i == selPocket && p != null
					Box(
						Modifier
							.weight(1f)
							.height(56.dp)
							.clip(shape)
							.background(Pal.Panel)
							.border(if (sel) 2.dp else 1.dp, if (sel) Pal.Gold else Pal.Line, shape)
							.clickable(enabled = p != null) {
								if (selPocket == i) {
									selPocket = -1
								} else {
									selPocket = i
									selCard = -1
									selPart = -1
								}
								cardRot = false
							}
							.padding(4.dp)
					) {
						if (p != null) {
							val pw = if (i == selPocket && cardRot && p.shape == Shape.S1x2) p.shape.h else p.shape.w
							val ph = if (i == selPocket && cardRot && p.shape == Shape.S1x2) p.shape.w else p.shape.h
							Canvas(Modifier.fillMaxSize()) {
								val unit = minOf(size.width / pw, size.height / ph)
								drawPart(p.kind, p.level, (size.width - unit * pw) / 2, (size.height - unit * ph) / 2, unit * pw, unit * ph)
							}
						}
					}
				}
			}

			// Колода
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(stringResource(R.string.deck), color = Pal.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
				val cost = engine.rerollCost()
				TextButton(
					onClick = {
						if (engine.reroll()) {
							sound.click()
							selCard = -1
							selPocket = -1
							cardRot = false
						} else {
							message = ctx.getString(R.string.err_money)
						}
					}
				) {
					Text(
						stringResource(R.string.reroll, Fmt.int(cost)),
						color = if (engine.money + 1e-9 >= cost) Pal.Gold else Pal.Bad, fontSize = 13.sp
					)
				}
			}
			for (row in 0 until 2) {
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					for (col in 0 until 4) {
						val i = row * 4 + col
						val c = engine.deck.getOrNull(i)
						if (c != null) {
							DeckCard(
								card = c,
								price = engine.priceOf(c),
								rot = i == selCard && cardRot,
								selected = i == selCard,
								affordable = engine.money + 1e-9 >= engine.priceOf(c),
								modifier = Modifier.weight(1f)
							) {
								if (selCard == i) {
									selCard = -1
								} else {
									selCard = i
									selPart = -1
									selPocket = -1
								}
								cardRot = false
							}
						} else {
							Spacer(Modifier.weight(1f))
						}
					}
				}
			}

			// Награды за рекламу (только в сборке для Google Play)
			if (Ads.available) {
				val left = ((engine.boostUntil - now) / 1000).coerceAtLeast(0)
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					Button(
						onClick = { Ads.show(activity) { engine.startBoost(System.currentTimeMillis()) } },
						enabled = Ads.ready && !boost,
						modifier = Modifier.weight(1f)
					) {
						Text(
							if (boost) stringResource(R.string.boost_active, "%d:%02d".format(left / 60, left % 60))
							else stringResource(R.string.ad_boost),
							fontSize = 12.sp, textAlign = TextAlign.Center
						)
					}
					Button(
						onClick = { Ads.show(activity) { engine.grant(engine.coinPack()) } },
						enabled = Ads.ready,
						modifier = Modifier.weight(1f)
					) {
						Text(stringResource(R.string.ad_coins, Fmt.int(engine.coinPack())), fontSize = 12.sp, textAlign = TextAlign.Center)
					}
				}
			}
		}

		// Плашка достижения
		val ach = engine.newAchievements.firstOrNull()
		LaunchedEffect(ach) {
			if (ach != null) {
				delay(2500)
				engine.newAchievements.removeFirstOrNull()
				engine.changed()
			}
		}
		if (ach != null) {
			Box(
				Modifier
					.align(Alignment.TopCenter)
					.systemBarsPadding()
					.padding(top = 8.dp)
					.clip(RoundedCornerShape(10.dp))
					.background(Color(0xFF2B2410))
					.border(1.dp, Pal.Gold, RoundedCornerShape(10.dp))
					.padding(horizontal = 14.dp, vertical = 8.dp)
			) {
				Text(stringResource(R.string.ach_unlocked, stringResource(achTitle(ach))), color = Pal.Gold)
			}
		}
	}

	// Офлайн-доход
	val off = engine.offline
	if (off != null) {
		AlertDialog(
			onDismissRequest = { engine.offline = null },
			confirmButton = {
				TextButton(onClick = { engine.offline = null }) { Text(stringResource(R.string.ok)) }
			},
			dismissButton = {
				if (Ads.available) {
					TextButton(enabled = Ads.ready, onClick = {
						engine.offline = null
						Ads.show(activity) { engine.grant(off.earned) }
					}) { Text(stringResource(R.string.ad_double)) }
				}
			},
			title = { Text(stringResource(R.string.offline_title)) },
			text = {
				Text(stringResource(R.string.offline_text, Fmt.int(off.earned), duration(off.seconds), off.burned))
			},
			containerColor = Pal.Panel,
			titleContentColor = Pal.Text,
			textContentColor = Pal.Text
		)
	}

	// Престиж
	if (showPrestige) {
		val gain = engine.prestigeGain()
		AlertDialog(
			onDismissRequest = { showPrestige = false },
			confirmButton = {
				TextButton(enabled = gain >= 1, onClick = {
					engine.doPrestige()
					showPrestige = false
					selCard = -1
					selPart = -1
					selPocket = -1
				}) { Text(stringResource(R.string.prestige_do)) }
			},
			dismissButton = {
				TextButton(onClick = { showPrestige = false }) { Text(stringResource(R.string.cancel)) }
			},
			title = { Text(stringResource(R.string.prestige)) },
			text = {
				Text(
					if (gain >= 1) stringResource(R.string.prestige_text, gain, gain * 10, engine.prestige * 10)
					else stringResource(
						R.string.prestige_locked, Fmt.int(Rules.PRESTIGE_NEED), Fmt.int(engine.runEarned), engine.prestige * 10
					)
				)
			},
			containerColor = Pal.Panel,
			titleContentColor = Pal.Text,
			textContentColor = Pal.Text
		)
	}

	// Ежедневные задания
	if (showQuests) {
		AlertDialog(
			onDismissRequest = { showQuests = false },
			confirmButton = { TextButton(onClick = { showQuests = false }) { Text(stringResource(R.string.ok)) } },
			title = { Text(stringResource(R.string.quests_title)) },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					for (q in engine.quests) {
						val prog = engine.questProgress(q)
						Column {
							Text(questText(q.type, q.target), color = Pal.Text, fontSize = 14.sp)
							LinearProgressIndicator(
								progress = { (prog / q.target).toFloat().coerceIn(0f, 1f) },
								modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
								color = Pal.Good, trackColor = Pal.Line
							)
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(
									questProgressText(q.type, prog, q.target) + " · " + stringResource(R.string.quest_reward, Fmt.int(engine.questReward())),
									color = Pal.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f)
								)
								if (q.claimed) {
									Text(stringResource(R.string.quest_claimed), color = Pal.Good, fontSize = 12.sp)
								} else {
									TextButton(enabled = engine.questDone(q), onClick = { engine.claim(q); sound.coin() }) {
										Text(stringResource(R.string.quest_claim))
									}
								}
							}
						}
					}
					Text(stringResource(R.string.quests_reset), color = Pal.Dim, fontSize = 11.sp)
				}
			},
			containerColor = Pal.Panel,
			titleContentColor = Pal.Text,
			textContentColor = Pal.Text
		)
	}

	// Статистика
	if (showStats) {
		val rowsList = listOf(
			R.string.st_total to Fmt.int(engine.totalEarned),
			R.string.st_run to Fmt.int(engine.runEarned),
			R.string.st_max_income to Fmt.dec(engine.maxIncome, 1),
			R.string.st_play to duration(engine.playSeconds.toLong()),
			R.string.st_perfect to duration(engine.perfectTotal.toLong()),
			R.string.st_bought to engine.partsBought.toString(),
			R.string.st_merges to engine.merges.toString(),
			R.string.st_sells to engine.sells.toString(),
			R.string.st_rerolls to engine.rerollsTotal.toString(),
			R.string.st_burns to engine.burns.toString(),
			R.string.st_fuse_saves to engine.fuseSaves.toString(),
			R.string.st_best to (if (engine.bestLevel >= 0) Rules.power(engine.bestLevel).toString() else "—"),
			R.string.st_quests to engine.questsDone.toString(),
			R.string.st_prestige to "${engine.prestige} (+${engine.prestige * 10}%)",
			R.string.st_rows to "${Rules.GRID}×${engine.rows}",
			R.string.st_ach to "${engine.achievements.size}/${Ach.entries.size}"
		)
		AlertDialog(
			onDismissRequest = { showStats = false },
			confirmButton = { TextButton(onClick = { showStats = false }) { Text(stringResource(R.string.ok)) } },
			title = { Text(stringResource(R.string.stats)) },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState())) {
					for ((label, value) in rowsList) {
						Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
							Text(stringResource(label), color = Pal.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
							Text(value, color = Pal.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
						}
					}
				}
			},
			containerColor = Pal.Panel,
			titleContentColor = Pal.Text,
			textContentColor = Pal.Text
		)
	}

	// Настройки
	if (showSettings) {
		AlertDialog(
			onDismissRequest = { showSettings = false },
			confirmButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.ok)) } },
			title = { Text(stringResource(R.string.settings)) },
			text = {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(stringResource(R.string.set_anim), color = Pal.Text, modifier = Modifier.weight(1f))
					Switch(checked = engine.animations, onCheckedChange = {
						engine.setAnimations(it)
						save()
					})
				}
			},
			containerColor = Pal.Panel,
			titleContentColor = Pal.Text,
			textContentColor = Pal.Text
		)
	}

	// Достижения
	if (showAch) {
		AlertDialog(
			onDismissRequest = { showAch = false },
			confirmButton = {
				TextButton(onClick = { showAch = false }) { Text(stringResource(R.string.ok)) }
			},
			title = { Text(stringResource(R.string.achievements)) },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState())) {
					for (a in Ach.entries) {
						val got = a in engine.achievements
						Row(Modifier.padding(vertical = 4.dp)) {
							Text(if (got) "✔" else "·", color = if (got) Pal.Good else Pal.Dim, modifier = Modifier.width(22.dp))
							Column {
								Text(
									stringResource(achTitle(a)), color = if (got) Pal.Text else Pal.Dim,
									fontWeight = FontWeight.Bold, fontSize = 14.sp
								)
								Text(stringResource(achDesc(a)), color = Pal.Dim, fontSize = 12.sp)
							}
						}
					}
				}
			},
			containerColor = Pal.Panel,
			titleContentColor = Pal.Text,
			textContentColor = Pal.Text
		)
	}
}

@Composable
private fun DeckCard(card: Card, price: Double, rot: Boolean, selected: Boolean, affordable: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val c = card.copy(rot = rot)
	val shape = RoundedCornerShape(8.dp)
	Column(
		modifier
			.clip(shape)
			.background(Pal.Panel)
			.border(if (selected) 2.dp else 1.dp, if (selected) Pal.Gold else Pal.Line, shape)
			.clickable(onClick = onClick)
			.padding(4.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Canvas(Modifier.fillMaxWidth().height(46.dp)) {
			val unit = minOf(size.width / c.w, size.height / c.h, size.height / 1.4f)
			val pw = unit * c.w
			val ph = unit * c.h
			drawPart(c.kind, c.level, (size.width - pw) / 2, (size.height - ph) / 2, pw, ph)
		}
		Text("${c.w}×${c.h}", color = Pal.Dim, fontSize = 10.sp, maxLines = 1)
		Text(
			Fmt.dec(price, 1), color = if (affordable) Pal.Gold else Pal.Bad,
			fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
			maxLines = 1, softWrap = false
		)
	}
}

@Composable
private fun BalanceBar(shares: DoubleArray, modifier: Modifier) {
	Canvas(modifier) {
		val w = size.width
		val h = size.height
		val cols = listOf(Pal.Chip, Pal.Cap, Pal.Res)
		drawRect(Pal.Line, Offset.Zero, Size(w, h * 0.62f))
		var x = 0f
		for (i in 0..2) {
			val sw = (shares[i] * w).toFloat()
			drawRect(cols[i], Offset(x, 0f), Size(sw, h * 0.62f))
			x += sw
		}
		val s = Rules.IDEAL.sum()
		x = 0f
		for (i in 0..2) {
			val sw = (Rules.IDEAL[i] / s * w).toFloat()
			drawRect(cols[i], Offset(x, h * 0.75f), Size(sw, h * 0.25f), alpha = 0.45f)
			x += sw
		}
	}
}

@Composable
private fun kindName(k: Kind): String = stringResource(
	when (k) {
		Kind.CHIP -> R.string.kind_chip
		Kind.CAP -> R.string.kind_cap
		Kind.RES -> R.string.kind_res
		Kind.FUSE -> R.string.kind_fuse
	}
)

@Composable
private fun needName(k: Kind): String = stringResource(
	when (k) {
		Kind.CHIP -> R.string.need_chip
		Kind.CAP -> R.string.need_cap
		Kind.RES -> R.string.need_res
		Kind.FUSE -> R.string.kind_fuse
	}
)

@Composable
private fun questText(t: QuestType, target: Double): String = when (t) {
	QuestType.MERGE -> stringResource(R.string.q_merge, target.toInt())
	QuestType.BUY -> stringResource(R.string.q_buy, target.toInt())
	QuestType.EARN -> stringResource(R.string.q_earn, Fmt.int(target))
	QuestType.REROLL -> stringResource(R.string.q_reroll, target.toInt())
	QuestType.PERFECT -> stringResource(R.string.q_perfect, (target / 60).toInt())
	QuestType.SELL -> stringResource(R.string.q_sell, target.toInt())
}

private fun questProgressText(t: QuestType, prog: Double, target: Double): String = when (t) {
	QuestType.EARN -> "${Fmt.int(prog)} / ${Fmt.int(target)}"
	QuestType.PERFECT -> "${(prog / 60).toInt()} / ${(target / 60).toInt()}"
	else -> "${prog.toInt()} / ${target.toInt()}"
}

@Composable
private fun duration(sec: Long): String = stringResource(R.string.duration, (sec / 3600).toInt(), ((sec % 3600) / 60).toInt())

private fun achTitle(a: Ach): Int = when (a) {
	Ach.FIRST_PART -> R.string.ach_first_part
	Ach.TEN_PARTS -> R.string.ach_ten_parts
	Ach.PERFECT -> R.string.ach_perfect
	Ach.EARN_1K -> R.string.ach_earn_1k
	Ach.EARN_1M -> R.string.ach_earn_1m
	Ach.EARN_1B -> R.string.ach_earn_1b
	Ach.MAX_POWER -> R.string.ach_max_power
	Ach.GIANT -> R.string.ach_giant
	Ach.FULL_BOARD -> R.string.ach_full_board
	Ach.FIRST_BURN -> R.string.ach_first_burn
	Ach.BURN_100 -> R.string.ach_burn_100
	Ach.MIN_DELAY -> R.string.ach_min_delay
	Ach.PRESTIGE -> R.string.ach_prestige
	Ach.PARTS_25 -> R.string.ach_parts_25
	Ach.ALL_KINDS -> R.string.ach_all_kinds
	Ach.FIRST_SELL -> R.string.ach_first_sell
	Ach.FIRST_REROLL -> R.string.ach_first_reroll
	Ach.REROLL_25 -> R.string.ach_reroll_25
	Ach.HOLD_10K -> R.string.ach_hold_10k
	Ach.EARN_1T -> R.string.ach_earn_1t
	Ach.INCOME_100 -> R.string.ach_income_100
	Ach.INCOME_10K -> R.string.ach_income_10k
	Ach.CHIPS_1000 -> R.string.ach_chips_1000
	Ach.MAX_PROTECT -> R.string.ach_max_protect
	Ach.BURN_1000 -> R.string.ach_burn_1000
	Ach.PRESTIGE_5 -> R.string.ach_prestige_5
	Ach.GIANTS_9 -> R.string.ach_giants_9
	Ach.FIRST_MERGE -> R.string.ach_first_merge
	Ach.FIRST_FUSE -> R.string.ach_first_fuse
	Ach.FUSE_SAVE -> R.string.ach_fuse_save
	Ach.FUSE_SAVE_100 -> R.string.ach_fuse_save_100
	Ach.MERGE_100 -> R.string.ach_merge_100
	Ach.PARTS_50 -> R.string.ach_parts_50
	Ach.REROLL_100 -> R.string.ach_reroll_100
	Ach.FIRST_QUEST -> R.string.ach_first_quest
	Ach.QUESTS_30 -> R.string.ach_quests_30
	Ach.PLAY_10H -> R.string.ach_play_10h
	Ach.ROWS_15 -> R.string.ach_rows_15
	Ach.STABLE_10M -> R.string.ach_stable_10m
	Ach.OFFLINE_8H -> R.string.ach_offline_8h
	Ach.BEST_OF_BEST -> R.string.ach_best_of_best
}

private fun achDesc(a: Ach): Int = when (a) {
	Ach.FIRST_PART -> R.string.ach_first_part_d
	Ach.TEN_PARTS -> R.string.ach_ten_parts_d
	Ach.PERFECT -> R.string.ach_perfect_d
	Ach.EARN_1K -> R.string.ach_earn_1k_d
	Ach.EARN_1M -> R.string.ach_earn_1m_d
	Ach.EARN_1B -> R.string.ach_earn_1b_d
	Ach.MAX_POWER -> R.string.ach_max_power_d
	Ach.GIANT -> R.string.ach_giant_d
	Ach.FULL_BOARD -> R.string.ach_full_board_d
	Ach.FIRST_BURN -> R.string.ach_first_burn_d
	Ach.BURN_100 -> R.string.ach_burn_100_d
	Ach.MIN_DELAY -> R.string.ach_min_delay_d
	Ach.PRESTIGE -> R.string.ach_prestige_d
	Ach.PARTS_25 -> R.string.ach_parts_25_d
	Ach.ALL_KINDS -> R.string.ach_all_kinds_d
	Ach.FIRST_SELL -> R.string.ach_first_sell_d
	Ach.FIRST_REROLL -> R.string.ach_first_reroll_d
	Ach.REROLL_25 -> R.string.ach_reroll_25_d
	Ach.HOLD_10K -> R.string.ach_hold_10k_d
	Ach.EARN_1T -> R.string.ach_earn_1t_d
	Ach.INCOME_100 -> R.string.ach_income_100_d
	Ach.INCOME_10K -> R.string.ach_income_10k_d
	Ach.CHIPS_1000 -> R.string.ach_chips_1000_d
	Ach.MAX_PROTECT -> R.string.ach_max_protect_d
	Ach.BURN_1000 -> R.string.ach_burn_1000_d
	Ach.PRESTIGE_5 -> R.string.ach_prestige_5_d
	Ach.GIANTS_9 -> R.string.ach_giants_9_d
	Ach.FIRST_MERGE -> R.string.ach_first_merge_d
	Ach.FIRST_FUSE -> R.string.ach_first_fuse_d
	Ach.FUSE_SAVE -> R.string.ach_fuse_save_d
	Ach.FUSE_SAVE_100 -> R.string.ach_fuse_save_100_d
	Ach.MERGE_100 -> R.string.ach_merge_100_d
	Ach.PARTS_50 -> R.string.ach_parts_50_d
	Ach.REROLL_100 -> R.string.ach_reroll_100_d
	Ach.FIRST_QUEST -> R.string.ach_first_quest_d
	Ach.QUESTS_30 -> R.string.ach_quests_30_d
	Ach.PLAY_10H -> R.string.ach_play_10h_d
	Ach.ROWS_15 -> R.string.ach_rows_15_d
	Ach.STABLE_10M -> R.string.ach_stable_10m_d
	Ach.OFFLINE_8H -> R.string.ach_offline_8h_d
	Ach.BEST_OF_BEST -> R.string.ach_best_of_best_d
}
