package jp.local.kakeibo.gambling

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.local.kakeibo.BuildConfig
import jp.local.kakeibo.KakeiboApplication
import jp.local.kakeibo.data.GamblingRecordEntity
import jp.local.kakeibo.data.syncFailureMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GamblingUiState(
    val records: List<GamblingRecordEntity> = emptyList(),
    val deletedRecords: List<GamblingRecordEntity> = emptyList(),
    val syncing: Boolean = false,
    val message: String? = null,
)

class GamblingViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repository = (app as KakeiboApplication).gamblingRepository
    private val syncStatus = MutableStateFlow(false to null as String?)

    val state =
        combine(repository.records, repository.deletedRecords, syncStatus) { records, deleted, status ->
            GamblingUiState(
                records = records,
                deletedRecords = deleted,
                syncing = status.first,
                message = status.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamblingUiState())

    fun save(
        current: GamblingRecordEntity?,
        date: LocalDate,
        stakeAmount: Long,
        payoutAmount: Long,
        gameType: String,
        memo: String,
    ) = viewModelScope.launch {
        repository.save(current, date, stakeAmount, payoutAmount, gameType, memo)
    }

    fun delete(record: GamblingRecordEntity) = viewModelScope.launch { repository.delete(record) }

    fun restore(record: GamblingRecordEntity) = viewModelScope.launch { repository.restore(record) }

    fun sync() =
        viewModelScope.launch {
            syncStatus.value = true to null
            syncStatus.value =
                try {
                    repository.sync()
                    false to "ギャンブル収支を同期しました"
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("GamblingSync", "同期に失敗しました: ${BuildConfig.API_BASE_URL}", error)
                    false to syncFailureMessage(error)
                }
        }
}
