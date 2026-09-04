package jp.local.kakeibo.gambling

import jp.local.kakeibo.data.GamblingRecordEntity

data class GamblingTotals(
    val totalStake: Long,
    val totalPayout: Long,
    val balance: Long,
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
