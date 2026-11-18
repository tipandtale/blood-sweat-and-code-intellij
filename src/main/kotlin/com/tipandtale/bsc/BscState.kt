package com.tipandtale.bsc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Today's numbers plus the three switches. Saved by the IDE between sessions.
 * Sweat = keystrokes, Blood = bugs squashed, Code = lines written. Resets at local midnight.
 */
@Service(Service.Level.APP)
@State(name = "BloodSweatAndCodeState", storages = [Storage("bloodSweatAndCode.xml")])
class BscState : PersistentStateComponent<BscState.Data> {

    /** What gets written to disk. Needs public vars and defaults so the IDE can (de)serialize it. */
    class Data {
        var day: String = ""
        var sweat: Int = 0
        var blood: Int = 0
        var code: Int = 0
        var goalReached: Boolean = false
        var goal: Int = 100
        var sound: Boolean = true
        var effects: Boolean = true
        var calm: Boolean = false
    }

    private var data = Data()
    private val listeners = CopyOnWriteArrayList<Runnable>()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        data = state
    }

    /** Returns the data, starting a fresh day first if the date changed. */
    private fun today(): Data {
        val now = LocalDate.now().toString()
        if (data.day != now) {
            data.day = now
            data.sweat = 0
            data.blood = 0
            data.code = 0
            data.goalReached = false
        }
        return data
    }

    val sweat: Int get() = today().sweat
    val blood: Int get() = today().blood
    val code: Int get() = today().code
    val goal: Int get() = today().goal.coerceAtLeast(1)

    var sound: Boolean
        get() = data.sound
        set(value) {
            data.sound = value
            changed()
        }

    var effects: Boolean
        get() = data.effects
        set(value) {
            data.effects = value
            changed()
        }

    /** Calm mode: no effects and no sound. Counts still go up. */
    var calm: Boolean
        get() = data.calm
        set(value) {
            data.calm = value
            changed()
        }

    /** +1 keystroke. Returns true when a multiple of 1000 was just reached. */
    fun addSweat(): Boolean {
        val d = today()
        d.sweat += 1
        changed()
        return d.sweat % 1000 == 0
    }

    fun addBlood(n: Int) {
        val d = today()
        d.blood += n
        changed()
    }

    /** Adds lines written. Returns true the first time today's goal is reached. */
    fun addCode(lines: Int): Boolean {
        val d = today()
        d.code += lines
        val hit = !d.goalReached && d.code >= d.goal.coerceAtLeast(1)
        if (hit) d.goalReached = true
        changed()
        return hit
    }

    fun changeGoal(lines: Int) {
        data.goal = lines.coerceAtLeast(1)
        changed()
    }

    fun reset() {
        data.day = LocalDate.now().toString()
        data.sweat = 0
        data.blood = 0
        data.code = 0
        data.goalReached = false
        changed()
    }

    fun addListener(listener: Runnable) {
        listeners.add(listener)
    }

    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    private fun changed() {
        listeners.forEach { it.run() }
    }

    companion object {
        fun get(): BscState = ApplicationManager.getApplication().getService(BscState::class.java)
    }
}
