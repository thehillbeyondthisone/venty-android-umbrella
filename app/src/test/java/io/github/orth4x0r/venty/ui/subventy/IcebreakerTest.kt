package io.github.orth4x0r.venty.ui.subventy

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class IcebreakerTest {
    private fun IcebreakerGame.guess(code: String) = code.fold(this) { g, c -> g.type(c) }.submit()

    @Test fun probeCountsExactBeforeNearAndNeverDoubleCounts() {
        assertEquals(Probe(4, 0), IcebreakerGame.probe("CAFE", "CAFE"))
        assertEquals(Probe(0, 4), IcebreakerGame.probe("ABCD", "DCBA"))
        assertEquals(Probe(1, 0), IcebreakerGame.probe("AABB", "ACCC"))
        assertEquals(Probe(0, 1), IcebreakerGame.probe("ABBB", "CAAA"))
        assertEquals(Probe(2, 2), IcebreakerGame.probe("AABB", "ABAB"))
        assertEquals(Probe(0, 0), IcebreakerGame.probe("AAAA", "BBBB"))
    }

    @Test fun crackingEndsTheRun() {
        val g = IcebreakerGame("CAFE", "n").guess("ABCD").guess("CAFE")
        assertEquals(BreakState.Cracked, g.state)
        assertSame(g, g.type('A'))
        assertSame(g, g.submit())
    }

    @Test fun tenMissesTraceTheCaller() {
        var g = IcebreakerGame("CAFE", "n")
        repeat(IcebreakerGame.MAX_ATTEMPTS - 1) { g = g.guess("BBBB") }
        assertEquals(BreakState.Running, g.state)
        assertEquals(90, g.trace)
        g = g.guess("BBBB")
        assertEquals(BreakState.Traced, g.state)
        assertEquals(100, g.trace)
    }

    @Test fun crackingOnTheLastAttemptStillCounts() {
        var g = IcebreakerGame("CAFE", "n")
        repeat(IcebreakerGame.MAX_ATTEMPTS - 1) { g = g.guess("BBBB") }
        assertEquals(BreakState.Cracked, g.guess("CAFE").state)
    }

    @Test fun draftIgnoresBadInputAndPartialSubmits() {
        val g = IcebreakerGame("CAFE", "n").type('Z').type('A').type('B')
        assertEquals("AB", g.draft)
        assertSame(g, g.submit())
        assertEquals("A", g.backspace().draft)
        assertEquals("ABCD", g.type('C').type('D').type('E').draft)
    }

    @Test fun newGamesUseOnlyGlyphs() {
        repeat(50) { seed ->
            val g = IcebreakerGame.new(Random(seed))
            assertEquals(IcebreakerGame.CODE_LENGTH, g.secret.length)
            assertTrue(g.secret.all { it in IcebreakerGame.GLYPHS })
            assertTrue(g.node.endsWith(".ember.line"))
        }
    }
}

class LootTest {
    @Test fun filesAreReleasedInOrderOnce() {
        assertEquals("readme", Loot.next(emptySet())?.id)
        assertEquals("rules", Loot.next(setOf("readme"))?.id)
        assertNull(Loot.next(Loot.FILES.map { it.id }.toSet()))
        assertEquals(Loot.FILES.size, Loot.FILES.map { it.id }.distinct().size)
    }

    @Test fun filesFitAPhoneTerminal() {
        for (f in Loot.FILES) for (line in f.text.lines()) assertTrue("${f.name}: '$line'", line.length <= 32)
    }

    @Test fun lootEncodingRoundTripsAndDropsJunk() {
        val list = listOf("readme" to 1L, "rules" to 2L)
        assertEquals(list, decodeLoot(encodeLoot(list)))
        assertEquals(emptyList<Pair<String, Long>>(), decodeLoot(""))
        assertEquals(listOf("a" to 5L), decodeLoot("a@5,a@6,@7,b@x,junk"))
    }
}
