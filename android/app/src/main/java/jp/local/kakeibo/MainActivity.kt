package jp.local.kakeibo

import android.app.Application
import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.local.kakeibo.category.orderedByExpenseFrequency
import jp.local.kakeibo.data.CategoryEntity
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.expense.defaultExpenseDate
import jp.local.kakeibo.expense.monthlyExpenses
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class UiState(
    val expenses: List<ExpenseEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val deletedExpenses: List<ExpenseEntity> = emptyList(),
    val deletedCategories: List<CategoryEntity> = emptyList(),
    val syncing: Boolean = false,
    val message: String? = null,
)

class MainViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repository = (app as KakeiboApplication).repository
    private val syncStatus = MutableStateFlow(false to null as String?)

    val state =
        combine(
            combine(
                repository.expenses,
                repository.categories,
                repository.deletedExpenses,
                repository.deletedCategories,
            ) { expenses, categories, deletedExpenses, deletedCategories ->
                UiState(
                    expenses = expenses,
                    categories = categories.orderedByExpenseFrequency(expenses),
                    deletedExpenses = deletedExpenses,
                    deletedCategories = deletedCategories,
                )
            },
            syncStatus,
        ) { state, status ->
            state.copy(syncing = status.first, message = status.second)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun saveExpense(
        expense: ExpenseEntity?,
        date: LocalDate,
        amount: Long,
        categoryUuid: String,
        memo: String,
    ) = launch {
        repository.saveExpense(expense?.uuid, date, amount, categoryUuid, memo, expense?.createdAt)
    }

    fun deleteExpense(expense: ExpenseEntity) = launch { repository.deleteExpense(expense) }

    fun saveCategory(
        category: CategoryEntity?,
        name: String,
    ) = launch { repository.saveCategory(category, name) }

    fun deleteCategory(category: CategoryEntity) = launch { repository.deleteCategory(category) }

    fun restoreExpense(expense: ExpenseEntity) = launch { repository.restoreExpense(expense) }

    fun restoreCategory(category: CategoryEntity) = launch { repository.restoreCategory(category) }

    fun sync() =
        launch {
            syncStatus.value = true to null
            syncStatus.value =
                try {
                    repository.sync()
                    false to "同期しました"
                } catch (_: Exception) {
                    false to "同期に失敗しました"
                }
        }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KakeiboTheme { KakeiboScreen() } }
    }
}

@Composable
fun KakeiboTheme(content: @Composable () -> Unit) {
    val colors =
        lightColorScheme(
            primary = Color(0xFF176B4D),
            surface = Color.White,
            background = Color(0xFFF4F6F5),
        )
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KakeiboScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseEditorOpen by remember { mutableStateOf(false) }
    var categoryEditorOpen by remember { mutableStateOf(false) }
    var summaryOpen by remember { mutableStateOf(false) }
    var trashOpen by remember { mutableStateOf(false) }
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedCategoryUuid by rememberSaveable { mutableStateOf("") }
    val selectedMonth = YearMonth.parse(selectedMonthText)
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.categories, selectedCategoryUuid) {
        if (
            selectedCategoryUuid.isNotEmpty() &&
            state.categories.none { it.uuid == selectedCategoryUuid }
        ) {
            selectedCategoryUuid = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            summaryOpen = !summaryOpen
                            if (summaryOpen) trashOpen = false
                        },
                    ) {
                        if (summaryOpen) {
                            Icon(Icons.AutoMirrored.Outlined.List, "支出一覧")
                        } else {
                            Icon(Icons.Outlined.BarChart, "サマリー")
                        }
                    }
                    IconButton(
                        onClick = {
                            trashOpen = !trashOpen
                            if (trashOpen) summaryOpen = false
                        },
                    ) {
                        if (trashOpen) {
                            Icon(Icons.AutoMirrored.Outlined.List, "支出一覧")
                        } else {
                            Icon(Icons.Outlined.Delete, "ゴミ箱")
                        }
                    }
                    IconButton(onClick = { categoryEditorOpen = true }) {
                        Icon(Icons.Outlined.Settings, "カテゴリ")
                    }
                    IconButton(enabled = !state.syncing, onClick = viewModel::sync) {
                        Icon(Icons.Outlined.Sync, "同期")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!summaryOpen && !trashOpen) {
                ExtendedFloatingActionButton(
                    onClick = { expenseEditorOpen = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("支出を追加") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (trashOpen) {
            TrashScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onRestoreExpense = viewModel::restoreExpense,
                onRestoreCategory = viewModel::restoreCategory,
            )
        } else if (summaryOpen) {
            SummaryScreen(expenses = state.expenses, modifier = Modifier.padding(padding))
        } else {
            ExpenseList(
                state = state,
                selectedMonth = selectedMonth,
                selectedCategoryUuid = selectedCategoryUuid,
                modifier = Modifier.padding(padding),
                onMonthChange = { selectedMonthText = it.toString() },
                onCategoryChange = { selectedCategoryUuid = it },
                onEdit = { editingExpense = it },
                onDelete = viewModel::deleteExpense,
            )
        }
    }

    if (expenseEditorOpen || editingExpense != null) {
        ExpenseEditor(
            current = editingExpense,
            categories = state.categories,
            initialDate = selectedMonth.defaultExpenseDate(),
            initialCategoryUuid = selectedCategoryUuid,
            onClose = {
                expenseEditorOpen = false
                editingExpense = null
            },
            onSave = { expense, date, amount, categoryUuid, memo ->
                viewModel.saveExpense(expense, date, amount, categoryUuid, memo)
                expenseEditorOpen = false
                editingExpense = null
            },
        )
    }
    if (categoryEditorOpen) {
        CategoryEditor(
            categories = state.categories,
            onClose = { categoryEditorOpen = false },
            onSave = viewModel::saveCategory,
            onDelete = viewModel::deleteCategory,
        )
    }
}

@Composable
private fun TrashScreen(
    state: UiState,
    modifier: Modifier = Modifier,
    onRestoreExpense: (ExpenseEntity) -> Unit,
    onRestoreCategory: (CategoryEntity) -> Unit,
) {
    val allCategories = state.categories + state.deletedCategories

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text(
                "ゴミ箱",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Text(
                "復元したデータは次回の同期でサーバーにも反映されます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
        if (state.deletedExpenses.isEmpty() && state.deletedCategories.isEmpty()) {
            item { Text("削除済みデータはありません", Modifier.padding(vertical = 24.dp)) }
        }
        if (state.deletedExpenses.isNotEmpty()) {
            item {
                Text("支出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.deletedExpenses, key = { "expense-${it.uuid}" }) { expense ->
                ListItem(
                    headlineContent = { Text(expense.memo.ifBlank { "支出" }) },
                    supportingContent = {
                        val category = allCategories.find { it.uuid == expense.categoryUuid }
                        Text("${expense.date}  ${category?.name ?: "未分類"}")
                    },
                    trailingContent = {
                        IconButton(onClick = { onRestoreExpense(expense) }) {
                            Icon(Icons.Outlined.Restore, "支出を復元")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
        if (state.deletedCategories.isNotEmpty()) {
            item {
                Text(
                    "カテゴリ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            items(state.deletedCategories, key = { "category-${it.uuid}" }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    trailingContent = {
                        IconButton(onClick = { onRestoreCategory(category) }) {
                            Icon(Icons.Outlined.Restore, "カテゴリを復元")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ExpenseList(
    state: UiState,
    selectedMonth: YearMonth,
    selectedCategoryUuid: String,
    modifier: Modifier = Modifier,
    onMonthChange: (YearMonth) -> Unit,
    onCategoryChange: (String) -> Unit,
    onEdit: (ExpenseEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
) {
    val visibleExpenses =
        state.expenses.monthlyExpenses(selectedMonth, selectedCategoryUuid.ifEmpty { null })
    val currentMonth = selectedMonth == YearMonth.now()

    Column(modifier.fillMaxSize()) {
        val total = visibleExpenses.sumOf { it.amount }
        Column(Modifier.padding(20.dp, 16.dp)) {
            Text(
                if (currentMonth) "今月の支出" else "${selectedMonth.monthValue}月の支出",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "%,d円".format(total),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${visibleExpenses.size}件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpenseToolbar(
            selectedMonth = selectedMonth,
            categories = state.categories,
            selectedCategoryUuid = selectedCategoryUuid,
            onMonthChange = onMonthChange,
            onCategoryChange = onCategoryChange,
        )
        HorizontalDivider()
        if (visibleExpenses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("この月の支出はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(visibleExpenses, key = { it.uuid }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        categories = state.categories,
                        onEdit = { onEdit(expense) },
                        onDelete = { onDelete(expense) },
                    )
                }
            }
        }
        if (state.syncing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseToolbar(
    selectedMonth: YearMonth,
    categories: List<CategoryEntity>,
    selectedCategoryUuid: String,
    onMonthChange: (YearMonth) -> Unit,
    onCategoryChange: (String) -> Unit,
) {
    var categoryMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, "前月")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronRight, "翌月")
            }
        }
        ExposedDropdownMenuBox(
            expanded = categoryMenuOpen,
            onExpandedChange = { categoryMenuOpen = !categoryMenuOpen },
        ) {
            OutlinedTextField(
                value =
                    stateCategoryName(
                        categories = categories,
                        selectedCategoryUuid = selectedCategoryUuid,
                    ),
                onValueChange = {},
                readOnly = true,
                label = { Text("カテゴリで絞り込み") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = categoryMenuOpen,
                onDismissRequest = { categoryMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("すべてのカテゴリ") },
                    onClick = {
                        onCategoryChange("")
                        categoryMenuOpen = false
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onCategoryChange(category.uuid)
                            categoryMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

private fun stateCategoryName(
    categories: List<CategoryEntity>,
    selectedCategoryUuid: String,
): String =
    categories.find { it.uuid == selectedCategoryUuid }?.name ?: "すべてのカテゴリ"

@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    categories: List<CategoryEntity>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(expense.memo.ifBlank { "支出" }) },
        supportingContent = {
            val categoryName = categories.find { it.uuid == expense.categoryUuid }?.name ?: "未分類"
            Text("${expense.date}  $categoryName")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%,d円".format(expense.amount), fontWeight = FontWeight.Bold)
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "編集") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "削除") }
            }
        },
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditor(
    current: ExpenseEntity?,
    categories: List<CategoryEntity>,
    initialDate: LocalDate,
    initialCategoryUuid: String,
    onClose: () -> Unit,
    onSave: (ExpenseEntity?, LocalDate, Long, String, String) -> Unit,
) {
    var date by remember { mutableStateOf(current?.date?.let(LocalDate::parse) ?: initialDate) }
    var amount by remember { mutableStateOf(current?.amount?.toString().orEmpty()) }
    var categoryUuid by remember {
        mutableStateOf(
            current?.categoryUuid
                ?: initialCategoryUuid.ifEmpty { categories.firstOrNull()?.uuid.orEmpty() },
        )
    }
    var memo by remember { mutableStateOf(current?.memo.orEmpty()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (current == null) "支出を追加" else "支出を編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(DateTimeFormatter.ISO_DATE))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text("金額") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = categoryMenuOpen,
                    onExpandedChange = { categoryMenuOpen = !categoryMenuOpen },
                ) {
                    OutlinedTextField(
                        value = categories.find { it.uuid == categoryUuid }?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("カテゴリ") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryUuid = category.uuid
                                    categoryMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("メモ") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = amount.toLongOrNull()?.let { it > 0 } == true && categoryUuid.isNotEmpty(),
                onClick = { onSave(current, date, amount.toLong(), categoryUuid, memo) },
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("キャンセル") } },
    )
}

@Composable
private fun CategoryEditor(
    categories: List<CategoryEntity>,
    onClose: () -> Unit,
    onSave: (CategoryEntity?, String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("カテゴリ管理") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("カテゴリ名") },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onSave(editingCategory, name)
                            name = ""
                            editingCategory = null
                        },
                    ) {
                        val icon = if (editingCategory == null) Icons.Outlined.Add else Icons.Outlined.Check
                        Icon(icon, "保存")
                    }
                }
                categories.forEach { category ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(category.name, Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                editingCategory = category
                                name = category.name
                            },
                        ) {
                            Icon(Icons.Outlined.Edit, "編集")
                        }
                        IconButton(onClick = { onDelete(category) }) {
                            Icon(Icons.Outlined.Delete, "削除")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("閉じる") } },
    )
}
