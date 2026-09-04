package jp.local.kakeibo.data

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import jp.local.kakeibo.gambling.requireValidGamblingInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class GamblingSyncRequest(
    @field:Expose
    @SerializedName("last_synced_at")
    val lastSyncedAt: String?,
    @field:Expose
    val records: List<GamblingRecordEntity>,
)

data class GamblingSyncData(
    @field:Expose
    val records: List<GamblingRecordEntity>,
)

data class GamblingSyncResponse(
    @field:Expose
    val success: Boolean,
    @field:Expose
    @SerializedName("server_time")
    val serverTime: String,
    @field:Expose
    val synced: List<String>,
    @field:Expose
    val data: GamblingSyncData,
)

interface GamblingApi {
    @POST("gambling/sync")
    suspend fun sync(
        @Body request: GamblingSyncRequest,
    ): GamblingSyncResponse
}

class GamblingRepository(
    private val database: AppDatabase,
    private val api: GamblingApi,
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            GAMBLING_SYNC_PREFERENCES,
            Context.MODE_PRIVATE,
        )
    private val syncMutex = Mutex()

    val records: Flow<List<GamblingRecordEntity>> = database.gamblingRecords().observe()
    val deletedRecords: Flow<List<GamblingRecordEntity>> = database.gamblingRecords().observeDeleted()

    suspend fun save(
        current: GamblingRecordEntity?,
        date: LocalDate,
        stakeAmount: Long,
        payoutAmount: Long,
        gameType: String,
        memo: String,
    ) {
        requireValidGamblingInput(stakeAmount, payoutAmount, gameType)
        val now = Instant.now().toString()
        database.gamblingRecords().upsert(
            GamblingRecordEntity(
                uuid = current?.uuid ?: UUID.randomUUID().toString(),
                date = date.toString(),
                stakeAmount = stakeAmount,
                payoutAmount = payoutAmount,
                gameType = gameType.trim(),
                memo = memo,
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
                isSynced = false,
            ),
        )
    }

    suspend fun delete(record: GamblingRecordEntity) {
        val now = Instant.now().toString()
        database.gamblingRecords().upsert(
            record.copy(deletedAt = now, updatedAt = now, isSynced = false),
        )
    }

    suspend fun restore(record: GamblingRecordEntity) {
        database.gamblingRecords().upsert(record.restoredAt(Instant.now().toString()))
    }

    suspend fun sync() =
        syncMutex.withLock {
            val outgoing = database.gamblingRecords().unsynced()
            val response =
                api.sync(
                    GamblingSyncRequest(
                        lastSyncedAt = preferences.getString(LAST_GAMBLING_SYNCED_AT, null),
                        records = outgoing,
                    ),
                )
            check(response.success) { "サーバーがギャンブル収支同期の失敗を返しました" }

            val outgoingByUuid = outgoing.associateBy(GamblingRecordEntity::uuid)
            val returnedUuids = response.data.records.mapTo(mutableSetOf(), GamblingRecordEntity::uuid)
            database.withTransaction {
                response.data.records.forEach { remote ->
                    val local = database.gamblingRecords().find(remote.uuid)
                    if (local == null || local.isSynced || !local.updatedAt.isAfter(remote.updatedAt)) {
                        database.gamblingRecords().upsert(remote.copy(isSynced = true))
                    }
                }
                response.synced
                    .asSequence()
                    .filterNot(returnedUuids::contains)
                    .forEach { uuid ->
                        val sent = outgoingByUuid[uuid] ?: return@forEach
                        val current = database.gamblingRecords().find(uuid) ?: return@forEach
                        if (current.updatedAt == sent.updatedAt) {
                            database.gamblingRecords().upsert(current.copy(isSynced = true))
                        }
                    }
            }
            preferences.edit { putString(LAST_GAMBLING_SYNCED_AT, response.serverTime) }
        }

    private fun String.isAfter(other: String): Boolean =
        runCatching { Instant.parse(this).isAfter(Instant.parse(other)) }
            .getOrElse { this > other }

    private companion object {
        const val GAMBLING_SYNC_PREFERENCES = "gambling_sync"
        const val LAST_GAMBLING_SYNCED_AT = "last"
    }
}
