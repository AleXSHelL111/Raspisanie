package com.alexshell.raspisanie

import com.alexshell.raspisanie.ui.tr
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.ui.components.LessonEditor
import com.alexshell.raspisanie.ui.components.LessonSheet
import com.alexshell.raspisanie.ui.components.NavItem
import com.alexshell.raspisanie.ui.components.NeonBackground
import com.alexshell.raspisanie.ui.components.NeonBottomBar
import com.alexshell.raspisanie.notify.Notifier
import com.alexshell.raspisanie.ui.screens.AddHomeworkDialog
import com.alexshell.raspisanie.ui.screens.GroupGrid
import com.alexshell.raspisanie.ui.screens.StreamPicker
import com.alexshell.raspisanie.ui.screens.GroupScreen
import com.alexshell.raspisanie.ui.components.NeonIconButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import com.alexshell.raspisanie.ui.screens.HomeworkScreen
import com.alexshell.raspisanie.ui.screens.SettingsOverlay
import com.alexshell.raspisanie.ui.components.NeonCard
import com.alexshell.raspisanie.ui.components.FormSpacer
import com.alexshell.raspisanie.ui.components.GradientButton
import com.alexshell.raspisanie.ui.components.NeonField
import com.alexshell.raspisanie.ui.components.OutlineButton
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.alexshell.raspisanie.ui.theme.Palettes
import com.alexshell.raspisanie.ui.theme.ThemeMode
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.toArgb
import com.alexshell.raspisanie.data.Repository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.alexshell.raspisanie.ui.screens.TodayScreen
import com.alexshell.raspisanie.ui.screens.WeekScreen
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.theme.NeonTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.alexshell.raspisanie.ui.I18n.init(this)
        Notifier.createChannels(this)
        com.alexshell.raspisanie.notify.GroupWatch.schedule(this)
        // Тема — до первой отрисовки, чтобы не было «мигания» цветов.
        val prefs = Repository.prefs(this)
        Neon.palette = Palettes.byId(prefs.getString("theme", null))
        Neon.mode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "DARK") }
            .getOrDefault(ThemeMode.DARK)
        Neon.systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // Цвет иконок статус-бара и фон окна — под текущую тему.
            val dark = Neon.isDark
            val bg = Neon.Bg
            LaunchedEffect(dark, bg) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.setBackgroundDrawable(ColorDrawable(bg.toArgb()))
            }
            NeonTheme {
                // Смена языка пересобирает интерфейс целиком; арабский — справа налево.
                val lang = com.alexshell.raspisanie.ui.I18n.lang
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        if (com.alexshell.raspisanie.ui.I18n.isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
                        else androidx.compose.ui.unit.LayoutDirection.Ltr,
                ) {
                    androidx.compose.runtime.key(lang) { NeonBackground { App(vm) } }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Файл .xlsx, открытый через «Открыть с помощью…». */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let(vm::importSchedule)
    }
}

private enum class Tab(val title: String) { TODAY("Сегодня"), WEEK("Неделя"), AI("KGMU AI"), HOMEWORK("ДЗ"), GROUP("Моя группа") }

@Composable
private fun App(vm: AppViewModel) {
    // Текущее время, обновляется ровно на границе каждой секунды.
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importSchedule)
    }
    val launchImport = {
        importer.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/octet-stream",
            )
        )
    }

    when {
        // Без профиля приложением пользоваться нельзя — сначала вход или регистрация.
        !vm.cloud.loggedIn -> AuthGate(vm)
        vm.needsFile -> WelcomeImport(vm, launchImport)
        vm.schedule == null && vm.loadError == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Neon.Cyan)
        }
        vm.schedule == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(vm.loadError.orEmpty(), color = Neon.Magenta, textAlign = TextAlign.Center)
        }
        vm.group == null -> Onboarding(vm)
        vm.needsStream -> StreamOnboarding(vm)
        else -> MainScaffold(vm, now, launchImport)
    }
    com.alexshell.raspisanie.ui.screens.RestoreAskDialog(vm)
}

/** Экран входа: регистрация обязательна. */
@Composable
private fun AuthGate(vm: AppViewModel) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                tr("КГМУ\nРАСПИСАНИЕ"),
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black,
                color = Neon.Cyan,
                style = TextStyle(shadow = Neon.glow(Neon.Cyan, 36f)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Войдите или создайте профиль — так ваши ДЗ, правки, настройки и группа сохранятся в облаке и не потеряются."),
                color = Neon.Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(18.dp))
            com.alexshell.raspisanie.ui.screens.AuthCard(vm)
        }
    }
}

/** Первый запуск: расписание нужно загрузить самому. */
@Composable
private fun WelcomeImport(vm: AppViewModel, onImport: () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            tr("КГМУ\nРАСПИСАНИЕ"),
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Black,
            color = Neon.Cyan,
            style = TextStyle(shadow = Neon.glow(Neon.Cyan, 36f)),
        )
        Spacer(Modifier.height(24.dp))
        NeonCard(accent = Neon.Magenta, glow = true) {
            Text(tr("Начало работы"), color = Neon.Magenta, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(tr("Загрузите файл расписания"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Файл .xlsx с сайта учебного отдела или из чата группы. Приложение само разберёт пары, недели и дисциплины по выбору."),
                color = Neon.Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)))
                    .clickable(enabled = !vm.importing, onClick = onImport),
                contentAlignment = Alignment.Center,
            ) {
                if (vm.importing) CircularProgressIndicator(color = Neon.OnAccent, modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                else Text(tr("Выбрать файл .xlsx"), color = Neon.OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Можно также открыть файл из Telegram или проводника через «Открыть с помощью → КГМУ расписание»."),
                color = Neon.Dim, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restoreBackupFile) }
        OutlineButton(tr("Восстановить из файла копии"), Neon.Lime) {
            restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
    }
    }
}

/** Шаг после выбора группы — если в расписании есть занятия по потокам. */
@Composable
private fun StreamOnboarding(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            tr("КГМУ\nРАСПИСАНИЕ"),
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Black,
            color = Neon.Cyan,
            style = TextStyle(shadow = Neon.glow(Neon.Cyan, 36f)),
        )
        Spacer(Modifier.height(32.dp))
        Text(
            tr("Выберите свой поток"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Magenta,
            style = TextStyle(shadow = Neon.glow(Neon.Magenta, 16f)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Группа {0}. От потока зависят дни лекций и физкультуры.", vm.group),
            color = Neon.Muted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        StreamPicker(vm.schedule?.streams.orEmpty(), vm.stream, vm::selectStream)
        Spacer(Modifier.height(16.dp))
        Text(tr("Поток можно сменить позже в настройках"), color = Neon.Dim, fontSize = 13.sp)
    }
}

@Composable
private fun Onboarding(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            tr("КГМУ\nРАСПИСАНИЕ"),
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Black,
            color = Neon.Cyan,
            style = TextStyle(shadow = Neon.glow(Neon.Cyan, 36f)),
        )
        Spacer(Modifier.height(8.dp))
        Text(vm.schedule?.title.orEmpty(), color = Neon.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        Text(
            tr("Выберите свою группу"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Magenta,
            style = TextStyle(shadow = Neon.glow(Neon.Magenta, 16f)),
        )
        Spacer(Modifier.height(16.dp))
        GroupGrid(vm.schedule?.groups.orEmpty(), null, vm::selectGroup)
        Spacer(Modifier.height(16.dp))
        Text(tr("Группу можно сменить позже в настройках"), color = Neon.Dim, fontSize = 13.sp)
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, now: LocalDateTime, onImport: () -> Unit) {
    // Приложение открывается на чате с KGMU AI.
    var tab by rememberSaveable { mutableIntStateOf(Tab.AI.ordinal) }
    var proDialog by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var openedLesson by remember { mutableStateOf<Lesson?>(null) }
    // Редактор: (занятие или null для нового, дата для нового)
    var editor by remember { mutableStateOf<Pair<Lesson?, LocalDate>?>(null) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsSection by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    // Android 13+: один раз спрашиваем разрешение на уведомления.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !vm.askedNotifications()) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Свежие объявления группы — для значка на вкладке.
    LaunchedEffect(Unit) { vm.cloud.pullGroup(quiet = true) }

    // KGMU AI попросил открыть раздел.
    LaunchedEffect(vm.ai.openRequest) {
        when (vm.ai.openRequest) {
            "settings" -> { settingsSection = 0; showSettings = true }
            "profile" -> { settingsSection = 1; showSettings = true }
            "group" -> tab = Tab.GROUP.ordinal
            "hw" -> tab = Tab.HOMEWORK.ordinal
            "week" -> tab = Tab.WEEK.ordinal
            "today" -> tab = Tab.TODAY.ordinal
            "pro" -> proDialog = true
            "site" -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kgmuraspisanie.ru")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "tg" -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(com.alexshell.raspisanie.ui.components.TELEGRAM_LINK)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        vm.ai.openRequest = null
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Neon.Text,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (Tab.entries[tab] == Tab.HOMEWORK) {
                FloatingActionButton(
                    onClick = { addDialog = true },
                    containerColor = Neon.Magenta,
                    contentColor = Neon.OnAccent,
                ) { Icon(Icons.Filled.Add, tr("Добавить ДЗ")) }
            }
        },
        bottomBar = {
            NeonBottomBar(
                items = Tab.entries.map { t ->
                    val pending = when (t) {
                        Tab.HOMEWORK -> vm.groupHomework().count { !it.done && it.dueDate >= now.toLocalDate() }
                        Tab.GROUP -> vm.cloud.unseenNews()
                        else -> 0
                    }
                    NavItem(
                        title = tr(t.title),
                        icon = when (t) {
                            Tab.TODAY -> Icons.Filled.Home
                            Tab.WEEK -> Icons.Filled.DateRange
                            Tab.AI -> null
                            Tab.HOMEWORK -> Icons.Filled.Edit
                            Tab.GROUP -> Icons.Filled.Person
                        },
                        badge = pending,
                        iconRes = if (t == Tab.AI) R.drawable.ic_ai_sparkle else null,
                        special = t == Tab.AI,
                    )
                },
                selected = tab,
                onSelect = { tab = it },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = Tab.entries[tab],
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tabs",
        ) { t ->
            val open: (Lesson) -> Unit = { openedLesson = it }
            when (t) {
                Tab.TODAY -> TodayScreen(
                    vm, now, padding, open,
                    onOpenSettings = { settingsSection = 0; showSettings = true },
                    onOpenHomework = { tab = Tab.HOMEWORK.ordinal },
                    onOpenAi = { tab = Tab.AI.ordinal },
                )
                Tab.AI -> com.alexshell.raspisanie.ui.screens.AiScreen(vm, padding)
                Tab.WEEK -> WeekScreen(vm, now, padding, open, onAdd = { d -> editor = null to d })
                Tab.HOMEWORK -> HomeworkScreen(vm, now, padding, open)
                Tab.GROUP -> GroupScreen(vm, padding, onOpenProfile = { settingsSection = 1; showSettings = true })
            }
        }
    }

    // Настройки — отдельный экран поверх вкладок (открываются шестерёнкой на главном).
    AnimatedVisibility(
        visible = showSettings,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
        BackHandler { showSettings = false }
        SettingsOverlay(vm, onImport, onBack = { showSettings = false }, startSection = settingsSection)
    }

    openedLesson?.let {
        LessonSheet(it, vm, onEdit = { l -> openedLesson = null; editor = l to l.date }) { openedLesson = null }
    }
    editor?.let { (lesson, date) -> LessonEditor(vm, lesson, date) { editor = null } }
    if (addDialog) AddHomeworkDialog(vm, now) { addDialog = false }
    if (proDialog) com.alexshell.raspisanie.ui.components.ProDialog(onOpenAi = { tab = Tab.AI.ordinal }) { proDialog = false }
}
