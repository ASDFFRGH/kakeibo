package jp.local.kakeibo.data

fun CategoryEntity.restoredAt(updatedAt: String): CategoryEntity =
    copy(deletedAt = null, updatedAt = updatedAt, isSynced = false)

fun ExpenseEntity.restoredAt(updatedAt: String): ExpenseEntity =
    copy(deletedAt = null, updatedAt = updatedAt, isSynced = false)

fun GamblingRecordEntity.restoredAt(updatedAt: String): GamblingRecordEntity =
    copy(deletedAt = null, updatedAt = updatedAt, isSynced = false)
