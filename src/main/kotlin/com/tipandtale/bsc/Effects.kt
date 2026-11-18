package com.tipandtale.bsc

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.Key
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Sprite sheets (same png + json frame lists as the artwork in src/main/resources/animations)
// ---------------------------------------------------------------------------------------------

class Frame(val x: Int, val y: Int, val w: Int, val h: Int)

class SpriteSheet(val image: BufferedImage, val frames: List<Frame>, val fps: Int)

object Sprites {
    private val cache = HashMap<String, SpriteSheet?>()

    fun get(kind: String): SpriteSheet? {
        if (!cache.containsKey(kind)) {
            cache[kind] = try {
                load(kind)
            } catch (_: Throwable) {
                null
            }
        }
        return cache[kind]
    }

    private fun load(kind: String): SpriteSheet? {
        val image = Sprites::class.java.getResourceAsStream("/animations/$kind.png")?.use { ImageIO.read(it) } ?: return null
        val json = Sprites::class.java.getResourceAsStream("/animations/$kind.json")
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        val fps = Regex("\"fps\":\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 12
        val frame = Regex("\\{\"x\":\\s*(\\d+),\\s*\"y\":\\s*(\\d+),\\s*\"w\":\\s*(\\d+),\\s*\"h\":\\s*(\\d+)\\}")
        val frames = frame.findAll(json).map { m ->
            Frame(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt())
        }.toList()
        return if (frames.isEmpty()) null else SpriteSheet(image, frames, fps)
    }
}

// ---------------------------------------------------------------------------------------------
// Things that can be drawn on top of the editor
// ---------------------------------------------------------------------------------------------

interface Effect {
    fun isDone(now: Long): Boolean
    fun paint(g: Graphics2D, now: Long)
}

private val OUTLINE = Color(0x3A, 0x2C, 0x63)

/** Plays one sprite sheet once, centered on (cx, cy). */
class SpriteEffect(
    private val sheet: SpriteSheet,
    private val cx: Int,
    private val cy: Int,
    private val size: Int,
    private val start: Long
) : Effect {
    private val frameMs = (1000 / sheet.fps).coerceAtLeast(10)

    override fun isDone(now: Long): Boolean = now - start >= frameMs.toLong() * sheet.frames.size

    override fun paint(g: Graphics2D, now: Long) {
        val index = ((now - start) / frameMs).toInt().coerceIn(0, sheet.frames.size - 1)
        val f = sheet.frames[index]
        g.drawImage(
            sheet.image,
            cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2,
            f.x, f.y, f.x + f.w, f.y + f.h,
            null
        )
    }
}

/** A short label that floats up and fades. Has a dark outline so it's readable on any editor theme. */
class TextEffect(
    private val text: String,
    private val cx: Int,
    private val cy: Int,
    private val font: Font,
    private val color: Color,
    private val start: Long
) : Effect {
    override fun isDone(now: Long): Boolean = now - start >= DURATION_MS

    override fun paint(g: Graphics2D, now: Long) {
        val t = (now - start).toFloat() / DURATION_MS
        val alpha = if (t < 0.7f) 1f else (1f - (t - 0.7f) / 0.3f).coerceIn(0f, 1f)
        g.font = font
        val x = (cx - g.fontMetrics.stringWidth(text) / 2).coerceAtLeast(4)
        val y = cy - (t * 30).toInt()
        g.composite = AlphaComposite.SrcOver.derive(alpha)
        g.color = OUTLINE
        for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) g.drawString(text, x + dx, y + dy)
        g.color = color
        g.drawString(text, x, y)
    }

    private companion object {
        const val DURATION_MS = 1100L
    }
}

/** Pastel confetti (squares and pixel hearts) that bursts up from the bottom of the visible editor. */
class ConfettiEffect(private val x0: Float, private val y0: Float, private val start: Long) : Effect {
    private class Particle(val vx: Float, val vy: Float, val color: Color, val heart: Boolean, val life: Int)

    private val particles = List(70) {
        Particle(
            Random.nextFloat() * 6f - 3f,
            -(Random.nextFloat() * 8f + 4f),
            COLORS[Random.nextInt(COLORS.size)],
            Random.nextFloat() < 0.35f,
            60 + Random.nextInt(30)
        )
    }

    override fun isDone(now: Long): Boolean = (now - start) / FRAME_MS > 95

    override fun paint(g: Graphics2D, now: Long) {
        val t = (now - start).toFloat() / FRAME_MS
        for (p in particles) {
            if (t > p.life) continue
            val fadeFrom = p.life * 0.7f
            val alpha = if (t < fadeFrom) 1f else (1f - (t - fadeFrom) / (p.life - fadeFrom)).coerceIn(0f, 1f)
            val x = (x0 + p.vx * t * 1.6f).toInt()
            val y = (y0 + p.vy * t + 0.5f * 0.3f * t * t).toInt()
            g.composite = AlphaComposite.SrcOver.derive(alpha)
            if (p.heart) drawHeart(g, x, y, p.color) else drawSquare(g, x, y, p.color)
        }
    }

    private fun drawSquare(g: Graphics2D, x: Int, y: Int, color: Color) {
        g.color = OUTLINE
        g.fillRect(x - 1, y - 1, 6, 6)
        g.color = color
        g.fillRect(x, y, 4, 4)
    }

    private fun drawHeart(g: Graphics2D, x: Int, y: Int, color: Color) {
        val u = 2
        for (pass in 0..1) {
            g.color = if (pass == 0) OUTLINE else color
            val grow = if (pass == 0) 1 else 0
            HEART.forEachIndexed { ry, row ->
                row.forEachIndexed { rx, ch ->
                    if (ch == 'X') g.fillRect(x + rx * u - grow, y + ry * u - grow, u + grow * 2, u + grow * 2)
                }
            }
        }
    }

    private companion object {
        const val FRAME_MS = 16f
        val HEART = listOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
        val COLORS = listOf(
            Color(0xC9A7FF), Color(0xA47BE6), Color(0xE5D4FF), Color(0xF3B8FF),
            Color(0xB794F6), Color(0xFF8000), Color(0xFFC010)
        )
    }
}

/**
 * A transparent layer added on top of one editor. It never takes mouse clicks or focus,
 * and only runs its animation timer while something is on screen.
 */
class EditorOverlay(private val editor: Editor) : JComponent() {
    private val effects = CopyOnWriteArrayList<Effect>()
    private val timer = Timer(16) { tick() }
    private val resizeListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            syncBounds()
        }
    }

    init {
        isOpaque = false
        isFocusable = false
        editor.contentComponent.add(this)
        editor.contentComponent.addComponentListener(resizeListener)
        syncBounds()
    }

    private fun syncBounds() {
        val c = editor.contentComponent
        setBounds(0, 0, c.width, c.height)
    }

    // Let every mouse event fall through to the editor underneath
    override fun contains(x: Int, y: Int): Boolean = false

    fun add(effect: Effect) {
        syncBounds()
        effects.add(effect)
        if (!timer.isRunning) timer.start()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        effects.removeIf { it.isDone(now) }
        if (effects.isEmpty()) timer.stop()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        if (effects.isEmpty()) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val now = System.currentTimeMillis()
            for (effect in effects) effect.paint(g2, now)
        } finally {
            g2.dispose()
        }
    }

    fun dispose() {
        timer.stop()
        effects.clear()
        editor.contentComponent.removeComponentListener(resizeListener)
        editor.contentComponent.remove(this)
    }
}

// ---------------------------------------------------------------------------------------------
// What the rest of the plugin calls
// ---------------------------------------------------------------------------------------------

object Effects {
    private val OVERLAY_KEY = Key.create<EditorOverlay>("bsc.overlay")

    private val LABEL_COLORS = listOf(
        Color(0xC9A7FF), Color(0xE5D4FF), Color(0xF3B8FF), Color(0xB8E0FF), Color(0xFFFFFF), Color(0xFFC83D)
    )

    private fun overlayOf(editor: Editor): EditorOverlay {
        editor.getUserData(OVERLAY_KEY)?.let { return it }
        val overlay = EditorOverlay(editor)
        editor.putUserData(OVERLAY_KEY, overlay)
        return overlay
    }

    /** Called when an editor closes. */
    fun release(editor: Editor) {
        editor.getUserData(OVERLAY_KEY)?.dispose()
        editor.putUserData(OVERLAY_KEY, null)
    }

    private fun caretPoint(editor: Editor): Point {
        val p = editor.visualPositionToXY(editor.caretModel.visualPosition)
        return Point(p.x, p.y + editor.lineHeight / 2)
    }

    /** Pastel heart pops at the caret while typing. */
    fun blip(editor: Editor) = sprite(editor, "blip", 1.6f, false)

    /** The "7" burst when deleting. */
    fun boom(editor: Editor) = sprite(editor, "boom", 2.6f, false)

    /** Sparkles above the caret on a new line. */
    fun newline(editor: Editor) = sprite(editor, "newline", 2.0f, true)

    private fun sprite(editor: Editor, kind: String, scale: Float, lift: Boolean) {
        if (editor.isDisposed) return
        val sheet = Sprites.get(kind) ?: return
        val c = caretPoint(editor)
        val size = (editor.lineHeight * scale).toInt().coerceAtLeast(24)
        val y = if (lift) c.y - editor.lineHeight else c.y
        overlayOf(editor).add(SpriteEffect(sheet, c.x, y, size, System.currentTimeMillis()))
    }

    /** Floating text above the caret (easter eggs, milestones). */
    fun label(editor: Editor, text: String) {
        if (editor.isDisposed) return
        val c = caretPoint(editor)
        val font = editor.colorsScheme.getFont(EditorFontType.BOLD).deriveFont(Font.BOLD, editor.lineHeight.toFloat())
        val color = LABEL_COLORS[Random.nextInt(LABEL_COLORS.size)]
        overlayOf(editor).add(TextEffect(text, c.x, c.y - editor.lineHeight, font, color, System.currentTimeMillis()))
    }

    /** Confetti from the bottom of the visible part of the editor. */
    fun confetti(editor: Editor) {
        if (editor.isDisposed) return
        val area = editor.scrollingModel.visibleArea
        val x = (area.x + area.width / 2).toFloat()
        val y = (area.y + area.height - 10).toFloat()
        overlayOf(editor).add(ConfettiEffect(x, y, System.currentTimeMillis()))
    }
}
