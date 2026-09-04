package jp.local.kakeibo

import android.app.Application
import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import jp.local.kakeibo.category.orderedByExpenseFrequency
import jp.local.kakeibo.category.forTransactionType
import jp.local.kakeibo.category.categoryIcon
import jp.local.kakeibo.data.CategoryEntity
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import jp.local.kakeibo.data.syncFailureMessage
import jp.local.kakeibo.expense.calendarDates
import jp.local.kakeibo.expense.coerceDay
import jp.local.kakeibo.expense.dailyTotals
import jp.local.kakeibo.expense.monthlyExpenses
import jp.local.kakeibo.navigation.KakeiboApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MONTH_PAGER_PAGE_COUNT = 2_401
private const val MONTH_PAGER_INITIAL_PAGE = MONTH_PAGER_PAGE_COUNT / 2

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
    private val kakeiboApp = app as KakeiboApplication
    private val repository = kakeiboApp.repository
    private val gamblingRepository = kakeiboApp.gamblingRepository
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
        type: String,
        categoryUuid: String,
        memo: String,
    ) = launch {
        repository.saveExpense(expense?.uuid, date, amount, type, categoryUuid, memo, expense?.createdAt)
    }

    fun deleteExpense(expense: ExpenseEntity) = launch { repository.deleteExpense(expense) }

    fun saveCategory(
        category: CategoryEntity?,
        name: String,
        type: String,
    ) = launch { repository.saveCategory(category, name, type) }

    fun deleteCategory(category: CategoryEntity) = launch { repository.deleteCategory(category) }

    fun restoreExpense(expense: ExpenseEntity) = launch { repository.restoreExpense(expense) }

    fun restoreCategory(category: CategoryEntity) = launch { repository.restoreCategory(category) }

    fun sync() =
        launch {
            syncStatus.value = true to null
            syncStatus.value =
                try {
                    repository.sync()
                    gamblingRepository.sync()
                    false to "同期しました"
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("KakeiboSync", "同期に失敗しました: ${BuildConfig.API_BASE_URL}", error)
                    false to syncFailureMessage(error)
                }
        }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent { KakeiboTheme { KakeiboApp() } }
    }
}

@Composable
fun KakeiboTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF176B4D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCDEBDD),
        onPrimaryContainer = Color(0xFF073B2A),
        secondary = Color(0xFF815512),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF8E8C8),
        onSecondaryContainer = Color(0xFF342504),
        tertiary = Color(0xFF5E5A8B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE6E3F4),
        onTertiaryContainer = Color(0xFF29264D),
        background = Color(0xFFF3F7F4),
        onBackground = Color(0xFF18201B),
        surface = Color.White,
        onSurface = Color(0xFF18201B),
        surfaceVariant = Color(0xFFE4ECE7),
        onSurfaceVariant = Color(0xFF46534B),
        outline = Color(0xFF738178),
        outlineVariant = Color(0xFFC7D2CB),
        error = Color(0xFFBA1A1A),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KakeiboScreen(
    viewModel: MainViewModel = viewModel(),
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseEditorOpen by remember { mutableStateOf(false) }
    var categoryEditorOpen by remember { mutableStateOf(false) }
    var summaryOpen by rememberSaveable { mutableStateOf(false) }
    var graphOpen by rememberSaveable { mutableStateOf(false) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var initialExpenseDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var reportAnchorDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var expenseListScrolling by remember { mutableStateOf(false) }
    val selectedMonth = YearMonth.parse(selectedMonthText)
    val selectedDate = LocalDate.parse(selectedDateText)
    val snackbar = remember { SnackbarHostState() }
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val closeSummary = {
        val reportDate = LocalDate.parse(reportAnchorDateText)
        val reportMonth = YearMonth.from(reportDate)
        selectedMonthText = reportMonth.toString()
        selectedDateText = reportMonth.coerceDay(reportDate.dayOfMonth).toString()
        summaryOpen = false
    }

    val fullscreen = expenseEditorOpen || editingExpense != null || categoryEditorOpen
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    DisposableEffect(Unit) {
        onDispose { onFullscreenChange(false) }
    }

    LaunchedEffect(summaryOpen, view) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !summaryOpen
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long,
            )
        }
    }

    BackHandler(enabled = summaryOpen, onBack = closeSummary)

    if (expenseEditorOpen || editingExpense != null) {
        val closeEditor = {
            expenseEditorOpen = false
            editingExpense = null
        }
        BackHandler(onBack = closeEditor)
        ExpenseEditorPage(
            current = editingExpense,
            categories = state.categories,
            initialDate = initialExpenseDate,
            initialCategoryUuid = "",
            onClose = closeEditor,
            onSave = { expense, date, amount, type, categoryUuid, memo ->
                viewModel.saveExpense(expense, date, amount, type, categoryUuid, memo)
                closeEditor()
            },
            onDelete = { expense ->
                viewModel.deleteExpense(expense)
                closeEditor()
            },
        )
        return
    }

    if (categoryEditorOpen) {
        val closeEditor = { categoryEditorOpen = false }
        BackHandler(onBack = closeEditor)
        CategoryEditorPage(
            categories = state.categories,
            onClose = closeEditor,
            onSave = viewModel::saveCategory,
            onDelete = viewModel::deleteCategory,
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                )
                NavigationDrawerItem(
                    label = { Text("カレンダー") },
                    selected = !summaryOpen && !graphOpen && !trashOpen,
                    icon = { Icon(Icons.Outlined.CalendarMonth, null) },
                    onClick = {
                        summaryOpen = false
                        graphOpen = false
                        trashOpen = false
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("グラフ") },
                    selected = graphOpen,
                    icon = { Icon(Icons.Outlined.ShowChart, null) },
                    onClick = {
                        graphOpen = true
                        summaryOpen = false
                        trashOpen = false
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("カテゴリ") },
                    selected = false,
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    onClick = {
                        categoryEditorOpen = true
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("ゴミ箱") },
                    selected = trashOpen,
                    icon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = {
                        trashOpen = true
                        summaryOpen = false
                        graphOpen = false
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text(if (state.syncing) "同期中…" else "同期") },
                    selected = false,
                    icon = { Icon(Icons.Outlined.Sync, null) },
                    onClick = {
                        if (!state.syncing) {
                            viewModel.sync()
                            scope.launch { drawerState.close() }
                        }
                    },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            when {
                                trashOpen -> Text("ゴミ箱", fontWeight = FontWeight.Bold)
                                summaryOpen -> Text("レポート", fontWeight = FontWeight.Bold)
                                graphOpen -> Text("グラフ", fontWeight = FontWeight.Bold)
                            }
                        },
                        navigationIcon = {
                            if (summaryOpen) {
                                IconButton(onClick = closeSummary) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "カレンダーに戻る")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Outlined.Menu, "メニューを開く")
                                }
                            }
                        },
                        actions = {
                            if (!summaryOpen && !graphOpen && !trashOpen) {
                                IconButton(
                                    onClick = {
                                        reportAnchorDateText = selectedDate.toString()
                                        summaryOpen = true
                                        graphOpen = false
                                        trashOpen = false
                                    },
                                ) {
                                    Icon(Icons.Outlined.BarChart, "レポートを開く")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor =
                                if (summaryOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            titleContentColor =
                                if (summaryOpen) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            navigationIconContentColor =
                                if (summaryOpen) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            actionIconContentColor =
                                if (summaryOpen) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        ),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            floatingActionButton = {
                if (!summaryOpen && !graphOpen && !trashOpen) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            initialExpenseDate = selectedDate
                            expenseEditorOpen = true
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.Add,
                                if (expenseListScrolling) "収支を追加" else null,
                            )
                        },
                        text = { Text("収支を追加") },
                        expanded = !expenseListScrolling,
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
                SummaryScreen(
                    expenses = state.expenses,
                    categories = state.categories,
                    initialAnchorDate = LocalDate.parse(reportAnchorDateText),
                    onAnchorDateChange = { reportAnchorDateText = it.toString() },
                    modifier = Modifier.padding(padding),
                )
            } else if (graphOpen) {
                MonthlyGraphScreen(
                    expenses = state.expenses,
                    categories = state.categories,
                    modifier = Modifier.padding(padding),
                )
            } else {
                ExpenseList(
                    state = state,
                    selectedMonth = selectedMonth,
                    modifier = Modifier.padding(padding),
                    onMonthChange = { month ->
                        selectedMonthText = month.toString()
                        selectedDateText = month.coerceDay(selectedDate.dayOfMonth).toString()
                    },
                    onEdit = { editingExpense = it },
                    selectedDate = selectedDate,
                    onDateClick = { date ->
                        selectedDateText = date.toString()
                    },
                    onScrollStateChange = { expenseListScrolling = it },
                )
            }
        }
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

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
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
                Text("収支", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.deletedExpenses, key = { "expense-${it.uuid}" }) { expense ->
                ListItem(
                    headlineContent = { Text(expense.memo.ifBlank { transactionLabel(expense.type) }) },
                    supportingContent = {
                        val category = allCategories.find { it.uuid == expense.categoryUuid }
                        Text("${expense.date}  ${category?.name ?: "未分類"}")
                    },
                    trailingContent = {
                        IconButton(onClick = { onRestoreExpense(expense) }) {
                            Icon(Icons.Outlined.Restore, "収支を復元")
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
                    supportingContent = { Text("${transactionLabel(category.type)}カテゴリ") },
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
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    onMonthChange: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    onEdit: (ExpenseEntity) -> Unit,
    onScrollStateChange: (Boolean) -> Unit,
) {
    val pagerAnchorMonth = remember { selectedMonth }
    val pagerState =
        rememberPagerState(
            initialPage = MONTH_PAGER_INITIAL_PAGE,
            pageCount = { MONTH_PAGER_PAGE_COUNT },
        )

    LaunchedEffect(pagerState, pagerAnchorMonth) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val pageMonth =
                    pagerAnchorMonth.plusMonths((page - MONTH_PAGER_INITIAL_PAGE).toLong())
                onMonthChange(pageMonth)
            }
    }
    LaunchedEffect(selectedMonth, pagerAnchorMonth) {
        val monthOffset = ChronoUnit.MONTHS.between(pagerAnchorMonth, selectedMonth)
        val targetPage = MONTH_PAGER_INITIAL_PAGE.toLong() + monthOffset
        if (
            targetPage in 0 until MONTH_PAGER_PAGE_COUNT.toLong() &&
            pagerState.currentPage != targetPage.toInt()
        ) {
            pagerState.animateScrollToPage(targetPage.toInt())
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            ExpenseToolbar(
                selectedMonth = selectedMonth,
                onMonthChange = onMonthChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it },
        ) { page ->
            val pageMonth =
                pagerAnchorMonth.plusMonths((page - MONTH_PAGER_INITIAL_PAGE).toLong())
            ExpenseMonthPage(
                state = state,
                month = pageMonth,
                selectedDate = pageMonth.coerceDay(selectedDate.dayOfMonth),
                onDateClick = onDateClick,
                onEdit = onEdit,
                isActive = page == pagerState.settledPage,
                onScrollStateChange = onScrollStateChange,
            )
        }
    }
}

@Composable
private fun ExpenseMonthPage(
    state: UiState,
    month: YearMonth,
    selectedDate: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    onEdit: (ExpenseEntity) -> Unit,
    isActive: Boolean,
    onScrollStateChange: (Boolean) -> Unit,
) {
    val visibleExpenses = state.expenses.monthlyExpenses(month)
    val selectedExpenses = visibleExpenses.filter { it.date == selectedDate.toString() }
    val listState = rememberLazyListState()

    LaunchedEffect(listState, isActive, selectedExpenses.isEmpty()) {
        if (!isActive) return@LaunchedEffect
        if (selectedExpenses.isEmpty()) {
            onScrollStateChange(false)
            return@LaunchedEffect
        }
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(onScrollStateChange)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            MonthCalendar(
                month = month,
                selectedDate = selectedDate,
                expenses = visibleExpenses,
                onDateClick = onDateClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MonthlyBalance(visibleExpenses)
        }
        Text(
            selectedDate.format(DateTimeFormatter.ofPattern("M月d日（E）")),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (selectedExpenses.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "この日の収支はありません\n収支を追加ボタンから登録できます",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        top = 8.dp,
                        end = 12.dp,
                        bottom = 96.dp,
                    ),
            ) {
                items(selectedExpenses, key = { it.uuid }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        categories = state.categories,
                        onEdit = { onEdit(expense) },
                    )
                }
            }
        }
        if (state.syncing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    expenses: List<ExpenseEntity>,
    onDateClick: (LocalDate) -> Unit,
) {
    val totals = remember(expenses) { dailyTotals(expenses) }
    val dates = remember(month) { calendarDates(month) }
    val weekDays = listOf("日", "月", "火", "水", "木", "金", "土")
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEachIndexed { index, label ->
                Text(
                    label,
                    textAlign = TextAlign.Center,
                    color = when (index) { 0 -> expenseColor(); 6 -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(64.dp))
                    } else {
                        val dayTotal = totals[date]
                        val selected = date == selectedDate
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .padding(1.dp)
                                .then(
                                    if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                    else Modifier,
                                )
                                .clickable { onDateClick(date) }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            dayTotal?.expense?.takeIf { it > 0 }?.let {
                                CalendarAmountText("-¥%,d".format(it), expenseColor())
                            }
                            dayTotal?.income?.takeIf { it > 0 }?.let {
                                CalendarAmountText("+¥%,d".format(it), incomeColor())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarAmountText(
    text: String,
    color: Color,
) {
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.labelSmall

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = constraints.maxWidth
        val fittedFontSize = remember(text, availableWidth, baseStyle) {
            val preferredSize = baseStyle.fontSize.value
            val measuredWidth =
                textMeasurer.measure(
                    text = text,
                    style = baseStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width

            if (availableWidth <= 0 || measuredWidth <= availableWidth) {
                baseStyle.fontSize
            } else {
                (preferredSize * availableWidth / measuredWidth * 0.98f).sp
            }
        }

        Text(
            text = text,
            color = color,
            style = baseStyle.copy(fontSize = fittedFontSize),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MonthlyBalance(expenses: List<ExpenseEntity>) {
    val expense = expenses.filter { it.type != TransactionType.INCOME }.sumOf { it.amount }
    val income = expenses.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        BalanceItem("収入", income, incomeColor())
        BalanceItem("支出", expense, expenseColor())
        BalanceItem("収支", income - expense, if (income >= expense) incomeColor() else expenseColor())
    }
}

@Composable
private fun BalanceItem(label: String, amount: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("¥%,d".format(amount), color = color, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseToolbar(
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
}

@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    categories: List<CategoryEntity>,
    onEdit: () -> Unit,
) {
    val categoryName = categories.find { it.uuid == expense.categoryUuid }?.name ?: "未分類"
    val amountLabel =
        "%s%,d円".format(
            if (expense.type == TransactionType.INCOME) "+" else "-",
            expense.amount,
        )
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(
                    onClickLabel = "${categoryName}を編集",
                    onClick = onEdit,
                ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = categoryPastelColor(expense.categoryUuid)),
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = categoryIcon(categoryName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                amountLabel,
                style = MaterialTheme.typography.titleLarge,
                color =
                    if (expense.type == TransactionType.INCOME) {
                        incomeColor()
                    } else {
                        expenseColor()
                    },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditorPage(
    current: ExpenseEntity?,
    categories: List<CategoryEntity>,
    initialDate: LocalDate,
    initialCategoryUuid: String,
    onClose: () -> Unit,
    onSave: (ExpenseEntity?, LocalDate, Long, String, String, String) -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
) {
    var date by remember { mutableStateOf(current?.date?.let(LocalDate::parse) ?: initialDate) }
    var amount by remember { mutableStateOf(current?.amount?.toString().orEmpty()) }
    var type by remember { mutableStateOf(current?.type ?: TransactionType.EXPENSE) }
    val availableCategories = categories.forTransactionType(type)
    var categoryUuid by remember {
        val initialType = current?.type ?: TransactionType.EXPENSE
        val categoriesForInitialType = categories.forTransactionType(initialType)
        mutableStateOf(
            current?.categoryUuid
                ?.takeIf { uuid -> categoriesForInitialType.any { it.uuid == uuid } }
                ?: initialCategoryUuid
                    .takeIf { uuid -> categoriesForInitialType.any { it.uuid == uuid } }
                ?: categoriesForInitialType.firstOrNull()?.uuid.orEmpty(),
        )
    }
    var memo by remember { mutableStateOf(current?.memo.orEmpty()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var deleteConfirmationOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val amountFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(amountFocusRequester) {
        if (current == null) {
            amountFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (current == null) "収支を追加" else "収支を編集",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, "入力画面を閉じる")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text("金額") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(amountFocusRequester),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick = {
                            type = TransactionType.EXPENSE
                            categoryUuid = categories
                                .forTransactionType(TransactionType.EXPENSE)
                                .firstOrNull()
                                ?.uuid
                                .orEmpty()
                        },
                        label = { Text("支出") },
                    )
                    FilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick = {
                            type = TransactionType.INCOME
                            categoryUuid = categories
                                .forTransactionType(TransactionType.INCOME)
                                .firstOrNull()
                                ?.uuid
                                .orEmpty()
                        },
                        label = { Text("収入") },
                    )
                }
            }
            ExposedDropdownMenuBox(
                expanded = categoryMenuOpen,
                onExpandedChange = { categoryMenuOpen = !categoryMenuOpen },
            ) {
                OutlinedTextField(
                    value = availableCategories.find { it.uuid == categoryUuid }?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("${transactionLabel(type)}カテゴリ") },
                    leadingIcon = {
                        availableCategories.find { it.uuid == categoryUuid }?.let { category ->
                            Icon(categoryIcon(category.name), contentDescription = null)
                        }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false },
                ) {
                    availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            leadingIcon = {
                                Icon(categoryIcon(category.name), contentDescription = null)
                            },
                            onClick = {
                                categoryUuid = category.uuid
                                categoryMenuOpen = false
                            },
                        )
                    }
                }
            }
            if (availableCategories.isEmpty()) {
                Text(
                    "${transactionLabel(type)}カテゴリを先に作成してください",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("メモ") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = amount.toLongOrNull()?.let { it > 0 } == true && categoryUuid.isNotEmpty(),
                onClick = { onSave(current, date, amount.toLong(), type, categoryUuid, memo) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("保存")
            }
            if (current != null) {
                OutlinedButton(
                    onClick = { deleteConfirmationOpen = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("この収支を削除")
                }
            }
            Image(
                painter = painterResource(R.drawable.spicky_mascot),
                contentDescription = "スピッキー",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
    if (deleteConfirmationOpen && current != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationOpen = false },
            title = { Text("収支を削除") },
            text = { Text("この収支を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmationOpen = false
                        onDelete(current)
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationOpen = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun incomeColor(): Color = Color(0xFF176B4D)

@Composable
private fun expenseColor(): Color = MaterialTheme.colorScheme.error

private val categoryPastelPalette =
    listOf(
        Color(0xFFE7F4EC),
        Color(0xFFFFF3D6),
        Color(0xFFF0EBFA),
        Color(0xFFE7F1FA),
        Color(0xFFFAE9E7),
        Color(0xFFE3F4F2),
        Color(0xFFF6EBDD),
        Color(0xFFECEEF8),
    )

private fun categoryPastelColor(uuid: String): Color =
    categoryPastelPalette[Math.floorMod(uuid.hashCode(), categoryPastelPalette.size)]

private fun transactionLabel(type: String): String =
    if (type == TransactionType.INCOME) "収入" else "支出"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CategoryEditorPage(
    categories: List<CategoryEntity>,
    onClose: () -> Unit,
    onSave: (CategoryEntity?, String, String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryPendingDeletion by remember { mutableStateOf<CategoryEntity?>(null) }
    val visibleCategories = categories.forTransactionType(type)

    fun clearEditing() {
        name = ""
        editingCategory = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("カテゴリ管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, "カテゴリ管理を閉じる")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TransactionType.EXPENSE,
                    enabled = editingCategory == null,
                    onClick = {
                        type = TransactionType.EXPENSE
                        clearEditing()
                    },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = type == TransactionType.INCOME,
                    enabled = editingCategory == null,
                    onClick = {
                        type = TransactionType.INCOME
                        clearEditing()
                    },
                    label = { Text("収入") },
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (editingCategory == null) "新しいカテゴリ名" else "カテゴリ名を編集") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(editingCategory, name.trim(), type)
                    clearEditing()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(
                    if (editingCategory == null) Icons.Outlined.Add else Icons.Outlined.Check,
                    null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (editingCategory == null) "カテゴリを追加" else "変更を保存")
            }
            if (editingCategory != null) {
                OutlinedButton(
                    onClick = ::clearEditing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("編集をキャンセル")
                }
            }
            Text(
                "${transactionLabel(type)}カテゴリ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (visibleCategories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "${transactionLabel(type)}カテゴリはまだありません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(visibleCategories, key = { it.uuid }) { category ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClickLabel = "${category.name}を削除",
                                    onLongClick = { categoryPendingDeletion = category },
                                ),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            ListItem(
                                headlineContent = { Text(category.name) },
                                leadingContent = {
                                    Icon(
                                        categoryIcon(category.name),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (editingCategory?.uuid == category.uuid) {
                                            "${transactionLabel(category.type)}カテゴリ・編集中"
                                        } else {
                                            "${transactionLabel(category.type)}カテゴリ"
                                        },
                                        color = if (editingCategory?.uuid == category.uuid) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingCategory = category
                                                name = category.name
                                                type = category.type
                                            },
                                        ) {
                                            Icon(Icons.Outlined.Edit, "${category.name}を編集")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    categoryPendingDeletion?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryPendingDeletion = null },
            title = { Text("カテゴリの削除") },
            text = { Text("「${category.name}」を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(category)
                        if (editingCategory?.uuid == category.uuid) {
                            clearEditing()
                        }
                        categoryPendingDeletion = null
                    },
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryPendingDeletion = null }) {
                    Text("No")
                }
            },
        )
    }
}
