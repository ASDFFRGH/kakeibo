package jp.local.kakeibo.gambling

import jp.local.kakeibo.data.GamblingRecordEntity
import java.time.LocalDate
import java.time.YearMonth

data class GamblingTotals(
    val totalStake: Long,
    val totalPayout: Long,
    val balance: Long,
)

data class GamblingGameTypeSummary(
    val gameType: String,
    val totals: GamblingTotals,
    val count: Int,
)

data class GamblingMonthSummary(
    val month: YearMonth,
    val totals: GamblingTotals,
    val count: Int,
)

enum class GamblingInputError(
    val message: String,
) {
    NEGATIVE_STAKE("投資額は0円以上で入力してください"),
    NEGATIVE_PAYOUT("回収額は0円以上で入力してください"),
    EMPTY_AMOUNTS("投資額または回収額を入力してください"),
    BLANK_GAME_TYPE("種目を入力してください"),
}

fun validateGamblingInput(
    stakeAmount: Long,
    payoutAmount: Long,
    gameType: String,
): GamblingInputError? =
    when {
        stakeAmount < 0 -> GamblingInputError.NEGATIVE_STAKE
        payoutAmount < 0 -> GamblingInputError.NEGATIVE_PAYOUT
        stakeAmount == 0L && payoutAmount == 0L -> GamblingInputError.EMPTY_AMOUNTS
        gameType.isBlank() -> GamblingInputError.BLANK_GAME_TYPE
        else -> null
    }

fun requireValidGamblingInput(
    stakeAmount: Long,
    payoutAmount: Long,
    gameType: String,
) {
    val error = validateGamblingInput(stakeAmount, payoutAmount, gameType)
    require(error == null) { error?.message.orEmpty() }
}

fun calculateGamblingTotals(records: Iterable<GamblingRecordEntity>): GamblingTotals {
    var totalStake = 0L
    var totalPayout = 0L
    records.forEach { record ->
        if (record.deletedAt == null) {
            totalStake += record.stakeAmount
            totalPayout += record.payoutAmount
        }
    }
    return GamblingTotals(
        totalStake = totalStake,
        totalPayout = totalPayout,
        balance = totalPayout - totalStake,
    )
}

/** Active records for [month], grouped by game type in a stable, useful order. */
fun calculateGamblingGameTypeSummaries(
    records: Iterable<GamblingRecordEntity>,
    month: YearMonth,
): List<GamblingGameTypeSummary> =
    records
        .filter { record -> record.deletedAt == null && record.yearMonthOrNull() == month }
        .groupBy { it.gameType.trim().ifBlank { "未分類" } }
        .map { (gameType, gameRecords) ->
            GamblingGameTypeSummary(gameType, calculateGamblingTotals(gameRecords), gameRecords.size)
        }
        .sortedWith(compareByDescending<GamblingGameTypeSummary> { it.totals.totalStake }.thenBy { it.gameType })

/** Active records grouped by month, newest month first. Invalid legacy dates are skipped. */
fun calculateGamblingMonthSummaries(records: Iterable<GamblingRecordEntity>): List<GamblingMonthSummary> =
    records
        .filter { it.deletedAt == null }
        .mapNotNull { record -> record.yearMonthOrNull()?.let { it to record } }
        .groupBy({ it.first }, { it.second })
        .map { (month, monthRecords) ->
            GamblingMonthSummary(month, calculateGamblingTotals(monthRecords), monthRecords.size)
        }
        .sortedByDescending { it.month }

private fun GamblingRecordEntity.yearMonthOrNull(): YearMonth? =
    runCatching { YearMonth.from(LocalDate.parse(date)) }.getOrNull()
