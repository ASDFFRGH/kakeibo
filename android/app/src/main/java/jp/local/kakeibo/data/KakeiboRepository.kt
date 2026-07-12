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

    suspend fun saveCategory(
        current: CategoryEntity?,
        name: String,
    ) {
        val now = Instant.now().toString()
        database.categories().upsert(
            CategoryEntity(
                uuid = current?.uuid ?: UUID.randomUUID().toString(),
                name = name,
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

    suspend fun sync() {
        val preferences = context.getSharedPreferences(SYNC_PREFERENCES, Context.MODE_PRIVATE)
        val request =
            SyncRequest(
                lastSyncedAt = preferences.getString(LAST_SYNCED_AT, null),
                categories = database.categories().unsynced(),
                expenses = database.expenses().unsynced(),
            )
        val response = api.sync(request)
        check(response.success)

        database.withTransaction {
            database.categories().upsert(response.data.categories.map { it.copy(isSynced = true) })
            database.expenses().upsert(response.data.expenses.map { it.copy(isSynced = true) })
        }
        preferences.edit { putString(LAST_SYNCED_AT, response.serverTime) }
    }

    companion object {
        private const val SYNC_PREFERENCES = "sync"
        private const val LAST_SYNCED_AT = "last"

        fun create(context: Context): KakeiboRepository {
            val database =
                Room
                    .databaseBuilder(context, AppDatabase::class.java, "kakeibo.db")
                    .addCallback(InitialCategoryCallback())
                    .build()
            val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
            val api =
                Retrofit
                    .Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                    .create(KakeiboApi::class.java)
            return KakeiboRepository(database, api, context)
        }
    }
}

private class InitialCategoryCallback : RoomDatabase.Callback() {
    override fun onCreate(database: SupportSQLiteDatabase) {
        super.onCreate(database)
        val now = Instant.now().toString()
        INITIAL_CATEGORIES.forEach { (uuid, name) ->
            database.execSQL(
                "INSERT INTO categories(uuid,name,created_at,updated_at,deleted_at,is_synced) VALUES(?,?,?,?,NULL,0)",
                arrayOf(uuid, name, now, now),
            )
        }
    }
}

private val INITIAL_CATEGORIES =
    listOf(
        "10000000-0000-4000-8000-000000000001" to "食費",
        "10000000-0000-4000-8000-000000000002" to "日用品",
        "10000000-0000-4000-8000-000000000003" to "交通費",
    )
