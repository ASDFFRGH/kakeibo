package jp.local.kakeibo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GamblingRestorationTest {
    @Test
    fun `restoring a gambling record clears deletion and marks it unsynced`() {
        val record =
            GamblingRecordEntity(
                uuid = "record",
                date = "2026-09-04",
                stakeAmount = 1_000,
                payoutAmount = 1_500,
                gameType = "競馬",
                memo = "メモ",
                createdAt = "created",
                updatedAt = "deleted",
                deletedAt = "deleted",
                isSynced = true,
            )

        val restored = record.restoredAt("restored")

        assertNull(restored.deletedAt)
        assertEquals("restored", restored.updatedAt)
        assertFalse(restored.isSynced)
        assertEquals("record", restored.uuid)
        assertEquals(1_000, restored.stakeAmount)
        assertEquals(1_500, restored.payoutAmount)
    }
}
