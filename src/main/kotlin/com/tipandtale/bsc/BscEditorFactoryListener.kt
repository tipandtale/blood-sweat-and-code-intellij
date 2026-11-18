package com.tipandtale.bsc

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class BscEditorFactoryListener : EditorFactoryListener {
    override fun editorReleased(event: EditorFactoryEvent) {
        Effects.release(event.editor)
    }
}
