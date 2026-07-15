package jp.local.kakeibo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RestorationTest {
    @Test
    fun `restoring expense clears deletion and marks it unsynced`() {
        val expense =
            ExpenseEntity(
                uuid = "expense",
                date = "2026-07-15",
                amount = 1_000,
                categoryUuid = "category",
                memo = "夕食",
                createdAt = "created",
                updatedAt = "deleted",
                deletedAt = "deleted",
                isSynced = true,
            )

        val restored = expense.restoredAt("restored")

        assertNull(restored.deletedAt)
        assertEquals("restored", restored.updatedAt)
        assertFalse(restored.isSynced)
    }

    @Test
    fun `restoring category clears deletion and marks it unsynced`() {
        val category =
            CategoryEntity(
                uuid = "category",
                name = "食費",
                createdAt = "created",
                updatedAt = "deleted",
                deletedAt = "deleted",
                isSynced = true,
            )

        val restored = category.restoredAt("restored")

        assertNull(restored.deletedAt)
        assertEquals("restored", restored.updatedAt)
        assertFalse(restored.isSynced)
    }
}
