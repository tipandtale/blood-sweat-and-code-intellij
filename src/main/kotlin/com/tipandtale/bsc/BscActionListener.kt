package com.tipandtale.bsc

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.AnActionListener

class BscActionListener : AnActionListener {

    override fun afterEditorTyping(c: Char, dataContext: DataContext) {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
        if (editor.isViewer) return
        BscEngine.onTyped(editor, c)
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: com.intellij.openapi.actionSystem.AnActionResult) {
        val id = ActionManager.getInstance().getId(action) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        if (editor.isViewer) return
        when (id) {
            IdeActions.ACTION_EDITOR_BACKSPACE, IdeActions.ACTION_EDITOR_DELETE -> BscEngine.onDelete(editor)
            IdeActions.ACTION_EDITOR_ENTER -> BscEngine.onEnter(editor)
        }
    }
}
