package io.github.orth4x0r.venty.collections

import io.github.orth4x0r.venty.session.SessionRecord
import org.junit.Assert.*
import org.junit.Test

class CollectionStoreTest {
    private fun session(start: Long, device: String = "device") = SessionRecord(
        start, start + 60000, 0, null, 1900, false,
        reachedBands = listOf(185, 180, 185), deviceKey = device)

    @Test fun repeatedHistoryDoesNotDuplicateBeadsOrOverwriteNotes() {
        val session = session(1000)
        val archive = mergeArchive(emptyList(), listOf(session))
        val annotated = archive.map { it.copy(note = "Keep this observation") }
        assertEquals(annotated, mergeArchive(annotated, listOf(session, session)))
        assertEquals(listOf(180, 185), annotated.single().bands)
    }
    @Test fun historyRolloverDoesNotExpireOlderCollectionEntries() {
        val archive = mergeArchive(emptyList(), listOf(session(1000)))
        val result = mergeArchive(archive, listOf(session(2000)))
        assertEquals(2, result.size)
        assertTrue(result.any { it.startMs == 1000L })
    }
    @Test fun demoAndHardwareSessionsHaveDifferentIdentities() {
        assertEquals(2, mergeArchive(emptyList(), listOf(session(1000), session(1000, "demo:Venty"))).size)
    }
    @Test fun historicalAveragesDoNotInventReachedBands() {
        val old = session(1000).copy(avgTargetTenths = 1850, reachedBands = emptyList())
        assertTrue(mergeArchive(emptyList(), listOf(old)).single().bands.isEmpty())
    }
}
