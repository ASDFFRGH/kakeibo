package jp.local.kakeibo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.local.kakeibo.data.CategoryEntity
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import jp.local.kakeibo.graph.MonthlyGraphCalculator
import jp.local.kakeibo.graph.MonthlyGraphPoint
import java.time.LocalDate
import kotlin.math.abs

private enum class GraphScope(val displayName: String) {
    OVERALL("全体"),
    CATEGORY("カテゴリ別"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyGraphScreen(
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var selectedMonthIndex by rememberSaveable { mutableIntStateOf(today.monthValue - 1) }
    var scope by rememberSaveable { mutableStateOf(GraphScope.OVERALL.name) }
    var selectedCategoryUuid by rememberSaveable { mutableStateOf("") }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val categoryScope = scope == GraphScope.CATEGORY.name

    LaunchedEffect(categoryScope, categories, selectedCategoryUuid) {
        if (
            categoryScope &&
            (selectedCategoryUuid.isEmpty() || categories.none { it.uuid == selectedCategoryUuid })
        ) {
            selectedCategoryUuid = categories.firstOrNull()?.uuid.orEmpty()
        }
    }

    val graphCategoryUuid =
        if (categoryScope) {
            selectedCategoryUuid.ifEmpty { "__no_category__" }
        } else {
            null
        }
    val points =
        remember(year, expenses, graphCategoryUuid) {
            MonthlyGraphCalculator.calculate(
                year = year,
                expenses = expenses,
                categoryUuid = graphCategoryUuid,
            )
        }
    val selectedPoint = points[selectedMonthIndex]
    val hasData = points.any { it.expense != 0L || it.income != 0L }
    val selectedCategory = categories.find { it.uuid == selectedCategoryUuid }
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error
    val balanceColor = MaterialTheme.colorScheme.tertiary

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GraphScope.entries.forEach { option ->
                    FilterChip(
                        selected = scope == option.name,
                        onClick = { scope = option.name },
                        label = { Text(option.displayName) },
                    )
                }
            }
        }

        if (categoryScope) {
            item {
                ExposedDropdownMenuBox(
                    expanded = categoryMenuOpen,
                    onExpandedChange = { categoryMenuOpen = !categoryMenuOpen },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.let(::graphCategoryLabel) ?: "カテゴリがありません",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("表示するカテゴリ") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen)
                        },
                        modifier =
                            Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(graphCategoryLabel(category)) },
                                onClick = {
                                    selectedCategoryUuid = category.uuid
                                    categoryMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            YearNavigator(
                year = year,
                onPrevious = { year-- },
                onNext = { year++ },
            )
        }

        item {
            SelectedMonthSummary(
                point = selectedPoint,
                incomeColor = incomeColor,
                expenseColor = expenseColor,
                balanceColor = balanceColor,
                onPreviousMonth = {
                    if (selectedMonthIndex > 0) selectedMonthIndex--
                },
                onNextMonth = {
                    if (selectedMonthIndex < points.lastIndex) selectedMonthIndex++
                },
            )
        }

        if (!hasData) {
            item {
                GraphEmptyState(categoryScope = categoryScope)
            }
        }

        item {
            MonthlyBarChartCard(
                title = "支出",
                points = points,
                valueOf = { it.expense },
                color = expenseColor,
                signed = false,
                selectedIndex = selectedMonthIndex,
                onSelected = { selectedMonthIndex = it },
            )
        }
        item {
            MonthlyBarChartCard(
                title = "収入",
                points = points,
                valueOf = { it.income },
                color = incomeColor,
                signed = false,
                selectedIndex = selectedMonthIndex,
                onSelected = { selectedMonthIndex = it },
            )
        }
        item {
            MonthlyBarChartCard(
                title = "収支",
                points = points,
                valueOf = { it.balance },
                color = balanceColor,
                signed = true,
                selectedIndex = selectedMonthIndex,
                onSelected = { selectedMonthIndex = it },
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun YearNavigator(
    year: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Outlined.ChevronLeft, "前年")
            }
            Text("${year}年", fontWeight = FontWeight.Bold)
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.ChevronRight, "翌年")
            }
        }
    }
}

@Composable
private fun SelectedMonthSummary(
    point: MonthlyGraphPoint,
    incomeColor: Color,
    expenseColor: Color,
    balanceColor: Color,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    enabled = point.month.monthValue > 1,
                ) {
                    Icon(Icons.Outlined.ChevronLeft, "前の月を選択")
                }
                Text(
                    "${point.month.monthValue}月の収支",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onNextMonth,
                    enabled = point.month.monthValue < 12,
                ) {
                    Icon(Icons.Outlined.ChevronRight, "次の月を選択")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GraphValue("支出", point.expense, expenseColor)
                GraphValue("収入", point.income, incomeColor)
                GraphValue(
                    "収支",
                    point.balance,
                    if (point.balance < 0) expenseColor else balanceColor,
                    signed = true,
                )
            }
        }
    }
}

@Composable
private fun GraphEmptyState(categoryScope: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            if (categoryScope) {
                "この年は選択したカテゴリの収支がありません"
            } else {
                "この年の収支はありません"
            },
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RowScope.GraphValue(
    label: String,
    value: Long,
    color: Color,
    signed: Boolean = false,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            formatYen(value, signed),
            color = color,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MonthlyBarChartCard(
    title: String,
    points: List<MonthlyGraphPoint>,
    valueOf: (MonthlyGraphPoint) -> Long,
    color: Color,
    signed: Boolean,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = points.map(valueOf)
    val selectedValue = values[selectedIndex]
    val annualValue = values.sum()
    val negativeColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    val description =
        buildString {
            append("${title}の月別グラフ。")
            points.forEachIndexed { index, point ->
                if (index > 0) append("、")
                append("${point.month.monthValue}月${formatYen(values[index], signed)}")
            }
        }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(color, RoundedCornerShape(3.dp)),
                    )
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(
                            "年間 ${formatYen(annualValue, signed)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "${selectedIndex + 1}月 ${formatYen(selectedValue, signed)}",
                    color = if (signed && selectedValue < 0) negativeColor else color,
                    fontWeight = FontWeight.Bold,
                )
            }

            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 12.dp)
                        .semantics { contentDescription = description }
                        .pointerInput(values) {
                            detectTapGestures { offset ->
                                val index =
                                    ((offset.x / size.width) * values.size)
                                        .toInt()
                                        .coerceIn(values.indices)
                                onSelected(index)
                            }
                        },
            ) {
                val slotWidth = size.width / values.size
                val baseline = if (signed) size.height / 2f else size.height - 6.dp.toPx()
                val availableHeight =
                    if (signed) {
                        size.height / 2f - 10.dp.toPx()
                    } else {
                        size.height - 18.dp.toPx()
                    }
                val maxValue =
                    values.maxOfOrNull { if (signed) abs(it) else it }
                        ?.coerceAtLeast(1L)
                        ?: 1L

                drawRoundRect(
                    color = selectedColor,
                    topLeft = Offset(slotWidth * selectedIndex + slotWidth * 0.08f, 0f),
                    size = Size(slotWidth * 0.84f, size.height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, baseline),
                    end = Offset(size.width, baseline),
                    strokeWidth = 1.dp.toPx(),
                )

                values.forEachIndexed { index, value ->
                    if (value == 0L) return@forEachIndexed
                    val barHeight = abs(value).toFloat() / maxValue.toFloat() * availableHeight
                    val barWidth = slotWidth * 0.48f
                    val left = slotWidth * index + (slotWidth - barWidth) / 2f
                    val top =
                        if (signed && value < 0) {
                            baseline
                        } else {
                            baseline - barHeight
                        }
                    drawRoundRect(
                        color = if (signed && value < 0) negativeColor else color,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                points.forEachIndexed { index, point ->
                    Text(
                        "${point.month.monthValue}",
                        modifier = Modifier.weight(1f),
                        color =
                            if (index == selectedIndex) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontSize = 10.sp,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun graphCategoryLabel(category: CategoryEntity): String =
    "${if (category.type == TransactionType.INCOME) "収入" else "支出"}・${category.name}"

private fun formatYen(
    value: Long,
    signed: Boolean,
): String =
    when {
        signed && value > 0 -> "+%,d円".format(value)
        else -> "%,d円".format(value)
    }
