package com.tipandtale.bsc

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

// (light theme, dark theme)
private val INK = JBColor(Color(0x3A2C63), Color(0xEDE4FF))
private val SOFT = JBColor(Color(0x7A66AD), Color(0xB9A8E6))
private val ACCENT = JBColor(Color(0xB48CF5), Color(0xB48CF5))
private val TRACK = JBColor(Color(0xE3D6FB), Color(0x4A3F6B))

/** Pastel notebook-style sidebar: today's numbers, the goal bar and three switches. */
class BscPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val state = BscState.get()
    private val greeting = label("", 15f, Font.BOLD, INK)
    private val goalText = label("", 12f, Font.PLAIN, SOFT)
    private val sweatValue = label("", 13f, Font.BOLD, INK)
    private val bloodValue = label("", 13f, Font.BOLD, INK)
    private val codeValue = label("", 13f, Font.BOLD, INK)
    private val bar = GoalBar()
    private val refresh = Runnable { SwingUtilities.invokeLater { update() } }

    private var updating = false
    private val soundBox = check("Sound") { state.sound = it }
    private val effectsBox = check("Effects") { state.effects = it }
    private val calmBox = check("Calm mode (no effects, no sound)") { state.calm = it }

    init {
        isOpaque = false
        val col = JPanel()
        col.layout = BoxLayout(col, BoxLayout.Y_AXIS)
        col.isOpaque = false
        col.border = BorderFactory.createEmptyBorder(14, 14, 14, 14)

        col.add(left(greeting))
        col.add(left(label("blood, sweat and code", 11f, Font.ITALIC, SOFT)))
        col.add(Box.createVerticalStrut(14))

        col.add(left(label("Today's goal", 12f, Font.BOLD, INK)))
        col.add(Box.createVerticalStrut(4))
        col.add(left(bar))
        col.add(left(goalText))
        col.add(Box.createVerticalStrut(14))

        col.add(row("💧 Sweat", "keystrokes", sweatValue))
        col.add(row("🩸 Blood", "bugs squashed", bloodValue))
        col.add(row("⌨ Code", "lines written", codeValue))
        col.add(Box.createVerticalStrut(14))

        col.add(left(soundBox))
        col.add(left(effectsBox))
        col.add(left(calmBox))
        col.add(Box.createVerticalStrut(12))

        col.add(left(link("party time") {
            BscEngine.celebrate(FileEditorManager.getInstance(project).selectedTextEditor)
        }))
        col.add(Box.createVerticalStrut(4))
        col.add(left(link("start over") {
            if (Messages.showYesNoDialog(project, "Reset today's numbers?", "Blood, Sweat and Code", null) == Messages.YES) {
                state.reset()
            }
        }))
        col.add(Box.createVerticalStrut(14))
        col.add(left(label("fan-made, not affiliated with HYBE or BTS 💜", 10f, Font.PLAIN, SOFT)))

        add(col, BorderLayout.NORTH)

        goalText.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        goalText.toolTipText = "Click to change your daily goal"
        goalText.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val input = Messages.showInputDialog(project, "Lines per day:", "Daily goal", null, state.goal.toString(), null)
                input?.trim()?.toIntOrNull()?.let { state.changeGoal(it) }
            }
        })

        state.addListener(refresh)
        update()
    }

    private fun update() {
        updating = true
        val h = java.time.LocalTime.now().hour
        greeting.text = when {
            h < 5 -> "Still up? 🌙"
            h < 12 -> "Good morning ☀"
            h < 18 -> "Hey you 💜"
            else -> "Good evening 🌙"
        }
        val goal = state.goal
        val code = state.code
        bar.fraction = (code.toFloat() / goal).coerceIn(0f, 1f)
        goalText.text = if (code >= goal) "$code / $goal lines. Goal reached! 🎉" else "$code / $goal lines"
        sweatValue.text = "%,d".format(state.sweat)
        bloodValue.text = state.blood.toString()
        codeValue.text = state.code.toString()
        soundBox.isSelected = state.sound
        effectsBox.isSelected = state.effects
        calmBox.isSelected = state.calm
        updating = false
        repaint()
    }

    override fun dispose() {
        state.removeListener(refresh)
    }

    // ----- small helpers -----

    private fun label(text: String, size: Float, style: Int, color: Color) = JLabel(text).apply {
        font = font.deriveFont(style, size)
        foreground = color
    }

    private fun left(c: JComponent): JComponent {
        c.alignmentX = Component.LEFT_ALIGNMENT
        return c
    }

    private fun check(text: String, onChange: (Boolean) -> Unit) = JCheckBox(text).apply {
        isOpaque = false
        foreground = INK
        addActionListener { if (!updating) onChange(isSelected) }
    }

    private fun link(text: String, onClick: () -> Unit) = JLabel("<html><u>$text</u></html>").apply {
        foreground = SOFT
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
    }

    private fun row(name: String, hint: String, value: JLabel): JComponent {
        val p = JPanel(BorderLayout())
        p.isOpaque = false
        p.border = BorderFactory.createEmptyBorder(3, 0, 3, 0)
        val l = JPanel()
        l.layout = BoxLayout(l, BoxLayout.Y_AXIS)
        l.isOpaque = false
        l.add(label(name, 13f, Font.BOLD, INK))
        l.add(label(hint, 10f, Font.PLAIN, SOFT))
        p.add(l, BorderLayout.WEST)
        p.add(value, BorderLayout.EAST)
        p.maximumSize = Dimension(Int.MAX_VALUE, 40)
        p.alignmentX = Component.LEFT_ALIGNMENT
        return p
    }

    private class GoalBar : JComponent() {
        var fraction = 0f
        init {
            preferredSize = Dimension(200, 12)
            maximumSize = Dimension(Int.MAX_VALUE, 12)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = TRACK
                g2.fillRoundRect(0, 0, width, height, height, height)
                val w = (width * fraction).toInt()
                if (w > 0) {
                    g2.color = ACCENT
                    g2.fillRoundRect(0, 0, w.coerceAtLeast(height), height, height, height)
                }
            } finally {
                g2.dispose()
            }
        }
    }
}
