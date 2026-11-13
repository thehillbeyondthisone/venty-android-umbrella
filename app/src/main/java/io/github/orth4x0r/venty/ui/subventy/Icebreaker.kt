package io.github.orth4x0r.venty.ui.subventy

import kotlin.random.Random

/*
 * ICEBREAKER: crack a four-glyph access code. Each attempt reports how many glyphs are right and in
 * place (locked) and how many are right but elsewhere (loose). There is no clock and no score for
 * speed; the trace meter only counts attempts. Nothing here touches the device or session data.
 */

data class Probe(val exact: Int, val near: Int)

data class Attempt(val guess: String, val probe: Probe)

enum class BreakState { Running, Cracked, Traced }

data class IcebreakerGame(
    val secret: String,
    val node: String,
    val attempts: List<Attempt> = emptyList(),
    val draft: String = "",
) {
    val state: BreakState get() = when {
        attempts.lastOrNull()?.probe?.exact == CODE_LENGTH -> BreakState.Cracked
        attempts.size >= MAX_ATTEMPTS -> BreakState.Traced
        else -> BreakState.Running
    }
    /** 0..100, one step per attempt. */
    val trace: Int get() = attempts.size * 100 / MAX_ATTEMPTS

    fun type(glyph: Char): IcebreakerGame =
        if (state != BreakState.Running || draft.length >= CODE_LENGTH || glyph !in GLYPHS) this else copy(draft = draft + glyph)

    fun backspace(): IcebreakerGame = if (draft.isEmpty()) this else copy(draft = draft.dropLast(1))

    fun submit(): IcebreakerGame =
        if (state != BreakState.Running || draft.length != CODE_LENGTH) this
        else copy(attempts = attempts + Attempt(draft, probe(secret, draft)), draft = "")

    companion object {
        const val GLYPHS = "ABCDEF"
        const val CODE_LENGTH = 4
        const val MAX_ATTEMPTS = 10

        fun new(random: Random = Random.Default): IcebreakerGame {
            val secret = String(CharArray(CODE_LENGTH) { GLYPHS[random.nextInt(GLYPHS.length)] })
            val node = String.format(java.util.Locale.ROOT, "%02X.%02X.ember.line", random.nextInt(256), random.nextInt(256))
            return IcebreakerGame(secret, node)
        }

        /** Classic scoring: exact matches first, then glyphs shared but misplaced (multiset overlap). */
        fun probe(secret: String, guess: String): Probe {
            require(secret.length == guess.length)
            var exact = 0
            val restS = IntArray(128); val restG = IntArray(128)
            for (i in secret.indices) {
                if (secret[i] == guess[i]) exact++ else { restS[secret[i].code]++; restG[guess[i].code]++ }
            }
            var near = 0
            for (c in restS.indices) near += minOf(restS[c], restG[c])
            return Probe(exact, near)
        }
    }
}

/** A file pulled from the fictional 1999 board. Released in order, one per cracked node. */
data class LootFile(val id: String, val name: String, val text: String) {
    val bytes: Int get() = text.length
}

object Loot {
    /** The next file a crack would release, or null once everything has been pulled. */
    fun next(found: Set<String>): LootFile? = FILES.firstOrNull { it.id !in found }

    fun byId(id: String): LootFile? = FILES.firstOrNull { it.id == id }

    val FILES = listOf(
        LootFile("readme", "readme.1st", """
THE EMBER LINE  //  1 node
300-28.8k  //  open 23:00-05:00
------------------------------
you got in. good.

this board runs on a 486 in a
basement that smells like
solder and orange peel. the
sysop goes by nightjar. be
polite and nobody gets hurt.

files come out one crack at a
time. no rush. the line is
open all night.
""".trimIndent()),
        LootFile("rules", "rules.txt", """
EMBER LINE RULES (rev. 3)
------------------------------
1. no speed records. nobody
   cares how fast you cracked
   it.
2. leave the line open for the
   next caller.
3. no flaming in #session.
4. what happens after 2am is
   between you and the modem.
5. drink water. seriously.
6. go to bed eventually.
""".trimIndent()),
        LootFile("nfo", "ember.nfo", """
      .    (    .     )    .
        )   )  (   (    (
       (   (    )   )  .  )
    +----------------------+
    |  E M B E R   L I N E |
    +----------------------+
         |  |  |  |  |  |
    ....... presents .......
     a quiet place to idle
    ------------------------
    greetz: night owls,
    phone phreaks, insomniacs,
    everyone still awake.
""".trimIndent()),
        LootFile("sysop", "sysop.log", """
[23:02] board up. kettle on.
[23:40] caller #1: kestrel.
        pulled 3 files, said
        thanks. legend.
[00:15] modem screaming again.
        swapped the cable.
[01:03] someone keeps typing
        'hack the planet' at
        the prompt. noted.
[02:47] power flicker. board
        survived. so did i.
[04:58] logging off. leaving
        the porch light on.
""".trimIndent()),
        LootFile("coil", "coil.doc", """
COIL.DOC  //  door game notes
------------------------------
COIL is a door game i wrote
in turbo pascal at 3am.

you don't win COIL. you sit
with it. the screen warms from
blue to amber and then it
just glows. that's the game.

people asked for a high score
table. i said no.
""".trimIndent()),
        LootFile("kestrel", "kestrel.msg", """
FROM: kestrel
TO:   nightjar
SUBJ: re: the line
------------------------------
can't sleep again. called in
just to watch the cursor
blink. weirdly helps.

left you a bad poem in /misc.
don't read it in daylight.
""".trimIndent()),
        LootFile("poem", "misc/3am.txt", """
a poem for 3am
------------------------------
the fan hums its one note,
the screen keeps its promise,
somewhere a modem sings
to a modem that sings back.

nothing is due.
nothing is late.
the cursor waits
like a porch light.
""".trimIndent()),
        LootFile("phreak", "phreak.txt", """
on tones
------------------------------
dial tone: 350 + 440 Hz.
ringback:  440 + 480.
busy:      480 + 620, half a
           second on, half off.

i don't know why i know this.
it's 3am. you learn things.
""".trimIndent()),
        LootFile("moon", "moon.doc", """
MOON.EXE v0.9
------------------------------
i wrote a moon phase program
because i thought the board
got busy at the full moon.

turns out it's busy on
fridays. kept the program
anyway. it lives at the
prompt. type moon.
""".trimIndent()),
        LootFile("callers", "callers.txt", """
things callers typed that did
something
------------------------------
whoami        fortune
uptime        finger
xyzzy         +++
ping          sudo
...and one about a planet.
""".trimIndent()),
        LootFile("modem", "modem.txt", """
the handshake
------------------------------
people ask why the modem
screams. it's two machines
introducing themselves,
loudly, at the same time.

if you miss the sound, there
is a switch at the prompt:
  modem on
""".trimIndent()),
        LootFile("lastcall", "last_call.txt", """
LAST CALL
------------------------------
if you're reading this, you
pulled everything. i'm taking
the board down for a while.

the 486 needs a rest. so do i.
so do you, probably.

the line stays open as long
as someone's awake on the
other end. tonight that's you.

goodnight, caller.
  -- nightjar
""".trimIndent()),
    )
}

/** Pulled loot as "id@epochMs" pairs; tolerant of junk and duplicates. */
internal fun encodeLoot(list: List<Pair<String, Long>>): String = list.joinToString(",") { "${it.first}@${it.second}" }

internal fun decodeLoot(raw: String): List<Pair<String, Long>> = raw.split(',').mapNotNull { part ->
    val at = part.lastIndexOf('@')
    if (at <= 0) null else part.substring(0, at).let { id -> part.substring(at + 1).toLongOrNull()?.let { id to it } }
}.distinctBy { it.first }
