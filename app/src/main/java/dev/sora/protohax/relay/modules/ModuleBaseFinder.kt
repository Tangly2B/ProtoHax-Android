package dev.sora.protohax.relay.modules

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.sora.protohax.ui.overlay.RenderLayerView
import dev.sora.relay.cheat.module.CheatCategory
import dev.sora.relay.cheat.module.CheatModule
import dev.sora.relay.game.entity.EntityPlayer
import dev.sora.relay.game.event.EventChunkUnload
import dev.sora.relay.game.world.chunk.Chunk

class ModuleBaseFinder : CheatModule("BaseFinder", CheatCategory.VISUAL) {

	private var scaleValue by intValue("Scale", 4, 1..12)
	private var mapSizeValue by intValue("MapSize", 220, 100..500)
	private var staleMsValue by intValue("StaleMs", 60000, 5000..300000)
	private var showGhostValue by boolValue("ShowGhostChunks", true)
	private var opacityValue by intValue("Opacity", 200, 50..255)
	private var maxGhostChunksValue by intValue("MaxGhostChunks", 4000, 500..20000)

	private data class GhostChunk(val x: Int, val z: Int, var lastSeen: Long)
	private val ghostChunks = mutableMapOf<Long, GhostChunk>()

	override fun onEnable() {
		session.eventManager.emit(RenderLayerView.EventRefreshRender(session))
	}

	override fun onDisable() {
		ghostChunks.clear()
	}

	private val handleChunkUnload = handle<EventChunkUnload> {
		ghostChunks[chunk.hash] = GhostChunk(chunk.x, chunk.z, System.currentTimeMillis())
	}

	private val handleRender = handle<RenderLayerView.EventRender> {
		needRefresh = true

		val scale = scaleValue.toFloat()
		val mapSize = mapSizeValue.toFloat()
		val player = session.player
		val px = player.posX
		val pz = player.posZ
		val chunkPixelSize = 16f * scale
		val now = System.currentTimeMillis()

		val panelLeft = canvas.width - mapSize - 24f
		val panelTop = 24f
		val panelCenterX = panelLeft + mapSize / 2f
		val panelCenterY = panelTop + mapSize / 2f

		fun toPanelX(wx: Float) = panelCenterX + (wx - px) * scale
		fun toPanelY(wz: Float) = panelCenterY + (wz - pz) * scale

		canvas.drawRect(panelLeft, panelTop, panelLeft + mapSize, panelTop + mapSize,
			Paint().apply { color = Color.argb((opacityValue * 0.55f).toInt(), 0, 0, 0) })

		canvas.save()
		canvas.clipRect(panelLeft, panelTop, panelLeft + mapSize, panelTop + mapSize)

		if (showGhostValue) {
			val ghostPaint = Paint().apply { color = Color.argb((opacityValue * 0.35f).toInt(), 140, 140, 140) }
			ghostChunks.values.forEach { g ->
				val sx = toPanelX(g.x * 16f)
				val sz = toPanelY(g.z * 16f)
				canvas.drawRect(sx, sz, sx + chunkPixelSize, sz + chunkPixelSize, ghostPaint)
			}
		}

		val freshPaint = Paint().apply { color = Color.argb(opacityValue, 80, 200, 255) }
		val oldPaint = Paint().apply { color = Color.argb((opacityValue * 0.4f).toInt(), 80, 200, 255) }
		session.level.chunks.values.forEach { chunk: Chunk ->
			val sx = toPanelX(chunk.x * 16f)
			val sz = toPanelY(chunk.z * 16f)
			val age = now - chunk.loadedAt
			canvas.drawRect(sx, sz, sx + chunkPixelSize, sz + chunkPixelSize,
				if (age < staleMsValue) freshPaint else oldPaint)
		}

		val entityPaint = Paint().apply { color = Color.argb(opacityValue, 255, 213, 74) }
		val playerPaint = Paint().apply { color = Color.argb(opacityValue, 255, 68, 68) }
		session.level.entityMap.values.forEach { entity ->
			val sx = toPanelX(entity.posX)
			val sz = toPanelY(entity.posZ)
			val isPlayer = entity is EntityPlayer
			canvas.drawCircle(sx, sz, if (isPlayer) 5f else 3f, if (isPlayer) playerPaint else entityPaint)
		}

		val selfPaint = Paint().apply { color = Color.argb(opacityValue, 74, 255, 106) }
		canvas.drawCircle(panelCenterX, panelCenterY, 5f, selfPaint)
		val rad = Math.toRadians(player.rotationYaw.toDouble())
		canvas.drawLine(
			panelCenterX, panelCenterY,
			panelCenterX - (Math.sin(rad) * 14).toFloat(),
			panelCenterY + (Math.cos(rad) * 14).toFloat(),
			selfPaint
		)

		canvas.restore()

		canvas.drawRect(panelLeft, panelTop, panelLeft + mapSize, panelTop + mapSize, Paint().apply {
			color = Color.argb(opacityValue, 255, 255, 255)
			style = Paint.Style.STROKE
			strokeWidth = 2f
		})

		if (ghostChunks.size > maxGhostChunksValue) {
			val sorted = ghostChunks.entries.sortedBy { it.value.lastSeen }
			val toRemove = sorted.take(ghostChunks.size - maxGhostChunksValue)
			toRemove.forEach { ghostChunks.remove(it.key) }
		}
	}
}
