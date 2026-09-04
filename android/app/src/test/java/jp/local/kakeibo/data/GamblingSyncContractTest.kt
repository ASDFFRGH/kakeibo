package jp.local.kakeibo.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamblingSyncContractTest {
    @Test
    fun `sync request uses independent envelope and excludes local sync state`() {
        val record =
            GamblingRecordEntity(
                uuid = "record",
                date = "2026-09-04",
                stakeAmount = 1_000,
                payoutAmount = 1_500,
                gameType = "競馬",
                memo = "メモ",
                createdAt = "2026-09-04T00:00:00Z",
                updatedAt = "2026-09-04T00:01:00Z",
                deletedAt = "2026-09-04T00:02:00Z",
                isSynced = false,
            )
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

        val json =
            JsonParser.parseString(
                gson.toJson(
                    GamblingSyncRequest(
                        lastSyncedAt = "2026-09-03T00:00:00Z",
                        records = listOf(record),
                    ),
                ),
            ).asJsonObject

        assertEquals(setOf("last_synced_at", "records"), json.keySet())
        val encodedRecord = json.getAsJsonArray("records").single().asJsonObject
        assertEquals(
            setOf(
                "uuid",
                "date",
                "stake_amount",
                "payout_amount",
                "game_type",
                "memo",
                "created_at",
                "updated_at",
                "deleted_at",
            ),
            encodedRecord.keySet(),
        )
        assertFalse(encodedRecord.has("is_synced"))
        assertTrue(json.get("last_synced_at").isJsonPrimitive)
    }
}
