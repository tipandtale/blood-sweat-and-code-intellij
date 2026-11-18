package com.tipandtale.bsc

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class BscStatusBarFactory : StatusBarWidgetFactory {
    override fun getId(): String = BscWidget.WIDGET_ID
    override fun getDisplayName(): String = "Blood, Sweat and Code"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = BscWidget(project)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class BscWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var bar: StatusBar? = null
    private val listener = Runnable { bar?.updateWidget(WIDGET_ID) }

    override fun ID(): String = WIDGET_ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        bar = statusBar
        BscState.get().addListener(listener)
    }

    override fun dispose() {
        BscState.get().removeListener(listener)
        bar = null
    }

    override fun getText(): String {
        val s = BscState.get()
        return "💧 %,d  🩸 %d  ⌨ %d/%d".format(s.sweat, s.blood, s.code, s.goal)
    }

    override fun getAlignment(): Float = 0f

    override fun getTooltipText(): String {
        val s = BscState.get()
        return "Blood, Sweat and Code (today): ${s.sweat} keystrokes, ${s.blood} bugs squashed, ${s.code} of ${s.goal} lines"
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow("Blood, Sweat and Code")?.activate(null)
    }

    companion object {
        const val WIDGET_ID = "BloodSweatAndCodeStatus"
    }
}
