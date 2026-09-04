package jp.local.kakeibo.data

import android.content.Context
import androidx.core.content.edit
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.GsonBuilder
import jp.local.kakeibo.BuildConfig
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface KakeiboApi {
    @POST("sync")
    suspend fun sync(
        @Body request: SyncRequest,
    ): SyncResponse
}

class KakeiboRepository(
    private val database: AppDatabase,
    private val api: KakeiboApi,
    private val context: Context,
) {
    val categories: Flow<List<CategoryEntity>> = database.categories().observe()
    val expenses: Flow<List<ExpenseEntity>> = database.expenses().observe()
    val deletedCategories: Flow<List<CategoryEntity>> = database.categories().observeDeleted()
    val deletedExpenses: Flow<List<ExpenseEntity>> = database.expenses().observeDeleted()

    suspend fun saveCategory(
        current: CategoryEntity?,
        name: String,
        type: String,
    ) {
        val now = Instant.now().toString()
        database.categories().upsert(
            CategoryEntity(
                uuid = current?.uuid ?: UUID.randomUUID().toString(),
                name = name,
                type = type,
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                isSynced = false,
            ),
        )
    }

    suspend fun saveExpense(
        uuid: String?,
        date: LocalDate,
        amount: Long,
        type: String,
        categoryUuid: String,
        memo: String,
        createdAt: String? = null,
    ) {
        val now = Instant.now().toString()
        database.expenses().upsert(
            ExpenseEntity(
                uuid = uuid ?: UUID.randomUUID().toString(),
                date = date.toString(),
                amount = amount,
                type = type,
                categoryUuid = categoryUuid,
                memo = memo,
                createdAt = createdAt ?: now,
                updatedAt = now,
                isSynced = false,
            ),
        )
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        val now = Instant.now().toString()
        database.expenses().upsert(
            expense.copy(deletedAt = now, updatedAt = now, isSynced = false),
        )
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        val now = Instant.now().toString()
        database.categories().upsert(
            category.copy(deletedAt = now, updatedAt = now, isSynced = false),
        )
    }

    suspend fun restoreCategory(category: CategoryEntity) {
        val now = Instant.now().toString()
        database.categories().upsert(
            category.restoredAt(now),
        )
    }

    suspend fun restoreExpense(expense: ExpenseEntity) {
        val now = Instant.now().toString()
        database.withTransaction {
            database.categories().find(expense.categoryUuid)?.let { category ->
                if (category.deletedAt != null) {
                    database.categories().upsert(
                        category.restoredAt(now),
                    )
                }
            }
            database.expenses().upsert(
                expense.restoredAt(now),
            )
        }
    }

    suspend fun sync() {
        val preferences = context.getSharedPreferences(SYNC_PREFERENCES, Context.MODE_PRIVATE)
        val request =
            SyncRequest(
                lastSyncedAt = preferences.getString(LAST_SYNCED_AT, null),
                categories = database.categories().unsynced(),
                expenses = database.expenses().unsynced(),
            )
        val response = api.sync(request)
        check(response.success) { "サーバーが同期の失敗を返しました" }

        database.withTransaction {
            database.categories().upsert(
                response.data.categories.map { category ->
                    category.copy(
                        type = category.type
                            .takeIf { it == TransactionType.INCOME }
                            ?: TransactionType.EXPENSE,
                        isSynced = true,
                    )
                },
            )
            database.expenses().upsert(response.data.expenses.map { it.copy(isSynced = true) })
        }
        preferences.edit { putString(LAST_SYNCED_AT, response.serverTime) }
    }

    companion object {
        private const val SYNC_PREFERENCES = "sync"
        private const val LAST_SYNCED_AT = "last"

        fun create(context: Context): KakeiboRepository = KakeiboDataContainer(context).repository
    }
}

internal class KakeiboDataContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room
            .databaseBuilder(applicationContext, AppDatabase::class.java, "kakeibo.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            ).addCallback(InitialCategoryCallback())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val repository: KakeiboRepository by lazy {
        KakeiboRepository(
            database = database,
            api = retrofit.create(KakeiboApi::class.java),
            context = applicationContext,
        )
    }

    val gamblingRepository: GamblingRepository by lazy {
        GamblingRepository(
            database = database,
            api = retrofit.create(GamblingApi::class.java),
            context = applicationContext,
        )
    }
}

private class InitialCategoryCallback : RoomDatabase.Callback() {
    override fun onCreate(database: SupportSQLiteDatabase) {
        super.onCreate(database)
        val now = Instant.now().toString()
        INITIAL_CATEGORIES.forEach { (uuid, name, type) ->
            database.execSQL(
                "INSERT INTO categories(uuid,name,transaction_type,created_at,updated_at,deleted_at,is_synced) VALUES(?,?,?,?,?,NULL,0)",
                arrayOf(uuid, name, type, now, now),
            )
        }
    }
}

private val INITIAL_CATEGORIES =
    listOf(
        Triple("10000000-0000-4000-8000-000000000001", "食費", TransactionType.EXPENSE),
        Triple("10000000-0000-4000-8000-000000000002", "日用品", TransactionType.EXPENSE),
        Triple("10000000-0000-4000-8000-000000000003", "交通費", TransactionType.EXPENSE),
        Triple("10000000-0000-4000-8000-000000000004", "給与", TransactionType.INCOME),
    )
