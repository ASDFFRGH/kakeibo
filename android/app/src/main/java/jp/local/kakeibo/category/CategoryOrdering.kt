package jp.local.kakeibo.category

import jp.local.kakeibo.data.CategoryEntity
import jp.local.kakeibo.data.ExpenseEntity

/**
 * Orders categories by the number of active expenses that reference them.
 * The incoming order is retained when counts are equal.
 */
fun List<CategoryEntity>.orderedByExpenseFrequency(
    expenses: List<ExpenseEntity>,
): List<CategoryEntity> {
    val counts =
        expenses
            .asSequence()
            .filter { it.deletedAt == null }
            .groupingBy { it.categoryUuid }
            .eachCount()
    val originalPositions = withIndex().associate { (index, category) -> category.uuid to index }

    return sortedWith(
        compareByDescending<CategoryEntity> { counts[it.uuid] ?: 0 }
            .thenBy { originalPositions.getValue(it.uuid) },
    )
}

fun List<CategoryEntity>.forTransactionType(type: String): List<CategoryEntity> =
    filter { it.type == type }
