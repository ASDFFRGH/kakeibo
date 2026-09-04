package jp.local.kakeibo.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow

object TransactionType {
    const val EXPENSE = "expense"
    const val INCOME = "income"
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    @field:Expose
    val uuid: String,
    @field:Expose
    val name: String,
    @ColumnInfo(name = "transaction_type", defaultValue = "'expense'")
    @field:Expose
    @SerializedName("type")
    val type: String = TransactionType.EXPENSE,
    @ColumnInfo(name = "created_at")
    @field:Expose
    @SerializedName("created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    @field:Expose
    @SerializedName("updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "deleted_at")
    @field:Expose
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["category_uuid"],
        ),
    ],
    indices = [Index("category_uuid")],
)
data class ExpenseEntity(
    @PrimaryKey
    @field:Expose
    val uuid: String,
    @field:Expose
    val date: String,
    @field:Expose
    val amount: Long,
    @ColumnInfo(name = "transaction_type", defaultValue = "'expense'")
    @field:Expose
    @SerializedName("type")
    val type: String = TransactionType.EXPENSE,
    @ColumnInfo(name = "category_uuid")
    @field:Expose
    @SerializedName("category_uuid")
    val categoryUuid: String,
    @field:Expose
    val memo: String,
    @ColumnInfo(name = "created_at")
    @field:Expose
    @SerializedName("created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    @field:Expose
    @SerializedName("updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "deleted_at")
    @field:Expose
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
)

@Entity(
    tableName = "gambling_records",
    indices = [Index("date"), Index("updated_at")],
)
data class GamblingRecordEntity(
    @PrimaryKey
    @field:Expose
    val uuid: String,
    @field:Expose
    val date: String,
    @ColumnInfo(name = "stake_amount")
    @field:Expose
    @SerializedName("stake_amount")
    val stakeAmount: Long,
    @ColumnInfo(name = "payout_amount")
    @field:Expose
    @SerializedName("payout_amount")
    val payoutAmount: Long,
    @ColumnInfo(name = "game_type")
    @field:Expose
    @SerializedName("game_type")
    val gameType: String,
    @field:Expose
    val memo: String,
    @ColumnInfo(name = "created_at")
    @field:Expose
    @SerializedName("created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    @field:Expose
    @SerializedName("updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "deleted_at")
    @field:Expose
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
)

data class SyncRequest(
    @field:Expose
    @SerializedName("last_synced_at")
    val lastSyncedAt: String?,
    @field:Expose
    val categories: List<CategoryEntity>,
    @field:Expose
    val expenses: List<ExpenseEntity>,
)

data class SyncData(
    @field:Expose
    val categories: List<CategoryEntity>,
    @field:Expose
    val expenses: List<ExpenseEntity>,
)

data class SyncResponse(
    @field:Expose
    val success: Boolean,
    @field:Expose
    @SerializedName("server_time")
    val serverTime: String,
    @field:Expose
    val synced: List<String>,
    @field:Expose
    val data: SyncData,
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted_at IS NULL ORDER BY name")
    fun observe(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeDeleted(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE uuid = :uuid")
    suspend fun find(uuid: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE is_synced = 0")
    suspend fun unsynced(): List<CategoryEntity>

    @Upsert
    suspend fun upsert(items: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(item: CategoryEntity)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE deleted_at IS NULL ORDER BY date DESC, created_at DESC")
    fun observe(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeDeleted(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE is_synced = 0")
    suspend fun unsynced(): List<ExpenseEntity>

    @Upsert
    suspend fun upsert(items: List<ExpenseEntity>)

    @Upsert
    suspend fun upsert(item: ExpenseEntity)
}

@Dao
interface GamblingRecordDao {
    @Query("SELECT * FROM gambling_records WHERE deleted_at IS NULL ORDER BY date DESC, created_at DESC")
    fun observe(): Flow<List<GamblingRecordEntity>>

    @Query("SELECT * FROM gambling_records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeDeleted(): Flow<List<GamblingRecordEntity>>

    @Query("SELECT * FROM gambling_records WHERE uuid = :uuid")
    suspend fun find(uuid: String): GamblingRecordEntity?

    @Query("SELECT * FROM gambling_records WHERE is_synced = 0")
    suspend fun unsynced(): List<GamblingRecordEntity>

    @Upsert
    suspend fun upsert(items: List<GamblingRecordEntity>)

    @Upsert
    suspend fun upsert(item: GamblingRecordEntity)
}

@Database(
    entities = [CategoryEntity::class, ExpenseEntity::class, GamblingRecordEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao

    abstract fun expenses(): ExpenseDao

    abstract fun gamblingRecords(): GamblingRecordDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE expenses ADD COLUMN transaction_type TEXT NOT NULL DEFAULT 'expense'",
                    )
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN transaction_type TEXT NOT NULL DEFAULT 'expense'",
                    )
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO categories(
                            uuid, name, transaction_type, created_at, updated_at, deleted_at, is_synced
                        ) VALUES(
                            '10000000-0000-4000-8000-000000000004',
                            '給与',
                            'income',
                            strftime('%Y-%m-%dT%H:%M:%fZ', 'now'),
                            strftime('%Y-%m-%dT%H:%M:%fZ', 'now'),
                            NULL,
                            0
                        )
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `gambling_records` (
                            `uuid` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `stake_amount` INTEGER NOT NULL,
                            `payout_amount` INTEGER NOT NULL,
                            `game_type` TEXT NOT NULL,
                            `memo` TEXT NOT NULL,
                            `created_at` TEXT NOT NULL,
                            `updated_at` TEXT NOT NULL,
                            `deleted_at` TEXT,
                            `is_synced` INTEGER NOT NULL,
                            PRIMARY KEY(`uuid`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_gambling_records_date` ON `gambling_records` (`date`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_gambling_records_updated_at` ON `gambling_records` (`updated_at`)",
                    )
                }
            }
    }
}
