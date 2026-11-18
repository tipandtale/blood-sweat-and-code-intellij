package com.tipandtale.bsc

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.util.concurrent.ConcurrentHashMap

/** Glue: turns editor events into stats, effects and sound. Always called on the UI thread. */
object BscEngine {
    private val eggs = EasterEggs()
    private val lastEditAt = ConcurrentHashMap<String, Long>()

    private fun state() = BscState.get()
    private fun fx(): Boolean = state().effects && !state().calm
    private fun snd(): Boolean = state().sound && !state().calm

    private fun markEdited(editor: Editor) {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        lastEditAt[file.path] = System.currentTimeMillis()
    }

    fun wasEditedRecently(path: String): Boolean =
        System.currentTimeMillis() - (lastEditAt[path] ?: 0L) < 8000

    fun onTyped(editor: Editor, c: Char) {
        markEdited(editor)
        val s = state()
        val milestone = s.addSweat()
        if (fx()) Effects.blip(editor)
        if (snd()) Sounds.typing()
        if (!s.calm) {
            eggs.feed(c)?.let { fireEgg(editor, it) }
        }
        if (milestone && !s.calm) {
            fireEgg(editor, Egg("${s.sweat} SWEAT", "${s.sweat} keystrokes today", Clip.CHICKEN_NOODLE_SOUP, true))
        }
    }

    fun onEnter(editor: Editor) {
        markEdited(editor)
        val s = state()
        s.addSweat()
        val goalHit = s.addCode(1)
        if (fx()) Effects.newline(editor)
        if (goalHit && !s.calm) {
            fireEgg(editor, Egg("DAILY GOAL!", "Daily goal hit: ${s.goal} lines", Clip.CHICKEN_NOODLE_SOUP, true))
        }
    }

    fun onDelete(editor: Editor) {
        markEdited(editor)
        if (fx()) Effects.boom(editor)
        if (snd()) Sounds.deleting()
    }

    fun onBugsSquashed(project: Project, editor: Editor?, n: Int) {
        val s = state()
        s.addBlood(n)
        status(project, "Bug squashed (${s.blood} today)")
        if (s.calm || editor == null) return
        if (s.effects) {
            Effects.boom(editor)
            Effects.label(editor, "BUG SQUASHED")
        }
        if (s.sound) Sounds.play(Clip.MIC_DROP)
    }

    private fun fireEgg(editor: Editor, egg: Egg) {
        val project = editor.project
        if (project != null) status(project, egg.message)
        val s = state()
        if (s.calm) return
        if (s.effects) {
            Effects.label(editor, egg.label)
            if (egg.confetti) Effects.confetti(editor)
        }
        if (s.sound) egg.sound?.let { Sounds.play(it) }
    }

    /** Party time button: confetti on the current editor plus the celebration clip. */
    fun celebrate(editor: Editor?) {
        val s = state()
        if (s.calm) return
        if (editor != null && s.effects) Effects.confetti(editor)
        if (s.sound) Sounds.play(Clip.CHICKEN_NOODLE_SOUP)
    }

    private fun status(project: Project, text: String) {
        WindowManager.getInstance().getStatusBar(project)?.info = text
    }
}
