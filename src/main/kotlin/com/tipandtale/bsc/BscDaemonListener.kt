package com.tipandtale.bsc

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project

/**
 * Blood: when the error count of a file you were just editing drops, a bug got squashed.
 * Runs after each highlighting pass.
 */
class BscDaemonListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
    private val baseline = HashMap<String, Int>()

    override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            for (fe in fileEditors) {
                val te = fe as? TextEditor ?: continue
                val editor = te.editor
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: continue
                val now = countErrors(editor.document)
                val prev = baseline[file.path] ?: now
                baseline[file.path] = now
                if (now < prev && BscEngine.wasEditedRecently(file.path)) {
                    val active = FileEditorManager.getInstance(project).selectedTextEditor
                    BscEngine.onBugsSquashed(project, if (active === editor) editor else null, prev - now)
                }
            }
        }
    }

    private fun countErrors(document: com.intellij.openapi.editor.Document): Int {
        val model = DocumentMarkupModel.forDocument(document, project, false) ?: return 0
        var n = 0
        for (h in model.allHighlighters) {
            val info = HighlightInfo.fromRangeHighlighter(h) ?: continue
            if (info.severity == HighlightSeverity.ERROR) n++
        }
        return n
    }
}
