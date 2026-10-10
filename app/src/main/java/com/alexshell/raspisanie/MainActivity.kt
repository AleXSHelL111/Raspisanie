package com.alexshell.raspisanie

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.alexshell.raspisanie.ui.components.LessonSheet
import com.alexshell.raspisanie.ui.components.NeonBackground
import com.alexshell.raspisanie.ui.screens.AddHomeworkDialog
import com.alexshell.raspisanie.ui.screens.GroupGrid
import com.alexshell.raspisanie.ui.screens.HomeworkScreen
import com.alexshell.raspisanie.ui.screens.SettingsScreen
import com.alexshell.raspisanie.ui.screens.TodayScreen
import com.alexshell.raspisanie.ui.screens.WeekScreen
import com.alexshell.raspisanie.ui.theme.Neon
import com.alexshell.raspisanie.ui.theme.NeonTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NeonTheme {
                NeonBackground { App(vm) }
            }
        }
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

private enum class Tab(val title: String) { TODAY("Сегодня"), WEEK("Неделя"), HOMEWORK("ДЗ"), SETTINGS("Ещё") }

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
        vm.schedule == null && vm.loadError == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Neon.Cyan)
        }
        vm.schedule == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(vm.loadError.orEmpty(), color = Neon.Magenta, textAlign = TextAlign.Center)
        }
        vm.group == null -> Onboarding(vm)
        else -> MainScaffold(vm, now, launchImport)
    }
}

@Composable
private fun Onboarding(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "NEON\nРАСПИСАНИЕ",
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
            "Выберите свою группу",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Magenta,
            style = TextStyle(shadow = Neon.glow(Neon.Magenta, 16f)),
        )
        Spacer(Modifier.height(16.dp))
        GroupGrid(vm.schedule?.groups.orEmpty(), null, vm::selectGroup)
        Spacer(Modifier.height(16.dp))
        Text("Группу можно сменить позже во вкладке «Ещё»", color = Neon.Dim, fontSize = 13.sp)
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, now: LocalDateTime, onImport: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedLesson by remember { mutableStateOf<Lesson?>(null) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (Tab.entries[tab] == Tab.HOMEWORK) {
                FloatingActionButton(
                    onClick = { addDialog = true },
                    containerColor = Neon.Magenta,
                    contentColor = Color.Black,
                ) { Icon(Icons.Filled.Add, "Добавить ДЗ") }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Neon.Surface.copy(alpha = 0.96f), tonalElevation = 0.dp) {
                Tab.entries.forEachIndexed { i, t ->
                    val pending = if (t == Tab.HOMEWORK) vm.groupHomework().count { !it.done && it.dueDate >= now.toLocalDate() } else 0
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.TODAY -> Icons.Filled.Home
                                    Tab.WEEK -> Icons.Filled.DateRange
                                    Tab.HOMEWORK -> Icons.Filled.Edit
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = t.title,
                            )
                        },
                        label = { Text(if (pending > 0) "${t.title} · $pending" else t.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            indicatorColor = Neon.Cyan,
                            selectedTextColor = Neon.Cyan,
                            unselectedIconColor = Neon.Muted,
                            unselectedTextColor = Neon.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = Tab.entries[tab],
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tabs",
        ) { t ->
            val open: (Lesson) -> Unit = { openedLesson = it }
            when (t) {
                Tab.TODAY -> TodayScreen(vm, now, padding, open)
                Tab.WEEK -> WeekScreen(vm, now, padding, open)
                Tab.HOMEWORK -> HomeworkScreen(vm, now, padding, open)
                Tab.SETTINGS -> SettingsScreen(vm, padding, onImport)
            }
        }
    }

    openedLesson?.let { LessonSheet(it, vm) { openedLesson = null } }
    if (addDialog) AddHomeworkDialog(vm, now) { addDialog = false }
}
