package com.tipandtale.bsc

/** A surprise: floating label, status bar message, optional sound and confetti. */
data class Egg(
    val label: String,
    val message: String,
    val sound: Clip? = null,
    val confetti: Boolean = false
)

/**
 * Type any of these words and the egg fires. Edit freely: add words, change labels, swap sounds.
 * Words shorter than 3 letters are ignored because they show up in normal code too often.
 */
class EasterEggs {

    private class Def(val words: List<String>, val egg: Egg)

    private val buffer = StringBuilder()
    private var lastAny = 0L
    private val lastByLabel = HashMap<String, Long>()

    /** Feed one typed character; returns an egg when a trigger word was just completed. */
    fun feed(c: Char): Egg? {
        buffer.append(c.lowercaseChar())
        if (buffer.length > BUFFER_LEN) buffer.delete(0, buffer.length - BUFFER_LEN)
        val text = buffer.toString()

        for (def in DEFS) {
            for (word in def.words) {
                if (word.length < MIN_WORD_LENGTH || !text.endsWith(word)) continue
                // The word must start a word, so "swarmy" doesn't trigger "army"
                val before = if (text.length > word.length) text[text.length - word.length - 1] else ' '
                if (before in 'a'..'z') continue
                return cooldown(def.egg)
            }
        }
        return null
    }

    private fun cooldown(egg: Egg): Egg? {
        val now = System.currentTimeMillis()
        if (now - lastAny < GLOBAL_COOLDOWN_MS) return null
        if (now - (lastByLabel[egg.label] ?: 0L) < PER_EGG_COOLDOWN_MS) return null
        lastAny = now
        lastByLabel[egg.label] = now
        buffer.setLength(0)
        return egg
    }

    private companion object {
        const val BUFFER_LEN = 24
        const val MIN_WORD_LENGTH = 3
        const val GLOBAL_COOLDOWN_MS = 2500L
        const val PER_EGG_COOLDOWN_MS = 12000L

        val DEFS = listOf(
            Def(listOf("bloodsweat", "blood sweat"), Egg("BLOOD SWEAT CODE", "Blood, sweat and code 💜", Clip.CHICKEN_NOODLE_SOUP, true)),
            Def(listOf("borahae"), Egg("I PURPLE YOU", "Borahae. I purple you 💜", Clip.CHICKEN_NOODLE_SOUP, true)),
            Def(listOf("army"), Egg("ARMY!", "ARMY, assemble 💜", Clip.CHICKEN_NOODLE_SOUP, true)),
            Def(listOf("bts", "bangtan"), Egg("BTS!", "Seven members. One team. 💜", Clip.MIC_DROP)),
            Def(listOf("dynamite"), Egg("DYNAMITE!", "Light it up 🧨", Clip.CHICKEN_NOODLE_SOUP, true)),
            Def(listOf("butter"), Egg("SMOOTH MODE", "Smooth mode on 🧈", Clip.JIMIN_SING)),
            Def(listOf("micdrop", "mic drop"), Egg("MIC DROP", "Mic drop", Clip.MIC_DROP)),
            Def(listOf("jhope", "j-hope", "chickennoodlesoup", "hoseok"), Egg("HOPE WORLD", "You are my hope, you are my angel", Clip.CHICKEN_NOODLE_SOUP, true)),
            Def(listOf("jimin"), Egg("JIMIN!", "Jimin 💛", Clip.JIMIN_LAUGH)),
            Def(listOf("jungkook"), Egg("JUNGKOOK!", "Golden maknae 🐰", Clip.JUNGKOOK)),
            Def(listOf("suga", "yoongi"), Egg("SUGA!", "Agust D incoming 🐱", Clip.MIC_DROP)),
            Def(listOf("namjoon"), Egg("NAMJOON!", "Leader mode 🐨", Clip.JIMIN_SING)),
            Def(listOf("seokjin", "worldwidehandsome"), Egg("SEOKJIN!", "Worldwide Handsome 🐹", Clip.JIMIN_LAUGH)),
            Def(listOf("taehyung", "kimtaehyung"), Egg("TAEHYUNG!", "V 🐯", Clip.JIMIN_SING))
        )
    }
}
