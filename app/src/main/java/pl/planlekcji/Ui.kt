package pl.planlekcji

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- motyw

@Composable
fun PlanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------------------------------------------------------- nawigacja

@Composable
fun AppRoot() {
    val st by Repo.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = screen != 0) { screen = 0 }
    when (screen) {
        1 -> HistoryScreen(st, onBack = { screen = 0 })
        2 -> SettingsScreen(st, onBack = { screen = 0 })
        else -> PlanScreen(st, onHistory = { screen = 1 }, onSettings = { screen = 2 })
    }
}

private val dateTimeFmt get() = SimpleDateFormat("d.MM, HH:mm", Locale.forLanguageTag("pl-PL"))

private fun defaultDay(): Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
    Calendar.TUESDAY -> 1
    Calendar.WEDNESDAY -> 2
    Calendar.THURSDAY -> 3
    Calendar.FRIDAY -> 4
    else -> 0 // poniedziałek, a w weekend najbliższy dzień szkolny
}

private fun Lesson.matches(q: String): Boolean {
    val t = q.trim().lowercase()
    if (t.isEmpty()) return true
    return listOf(klass, room, building, subject, teacher).any { it.lowercase().contains(t) }
}

/** Klasy, które mają w tej samej godzinie te same: salę, przedmiot i nauczyciela = zajęcia łączone. */
private fun jointPartners(day: List<Lesson>): Map<String, List<String>> {
    val out = HashMap<String, List<String>>()
    val groups = day
        .filter { it.subject.isNotBlank() && it.teacher.isNotBlank() }
        .groupBy { "${it.slot}|${it.room}|${it.subject.lowercase()}|${it.teacher.lowercase()}" }
    for (g in groups.values) {
        if (g.size > 1) for (l in g) out[l.key] = g.filter { it !== l }.map { it.klass }
    }
    return out
}

// ---------------------------------------------------------------- ekran planu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(st: UiState, onHistory: () -> Unit, onSettings: () -> Unit) {
    val s = st.settings
    val scope = rememberCoroutineScope()
    var day by rememberSaveable { mutableIntStateOf(defaultDay()) }
    var query by rememberSaveable { mutableStateOf("") }

    val dayLessons = remember(st.lessons, day) { st.lessons.filter { it.day == day } }
    val partners = remember(dayLessons) { jointPartners(dayLessons) }
    val classes = remember(st.lessons) {
        st.lessons.distinctBy { it.klassId }.sortedWith(compareBy<Lesson>({ it.grade }, { it.klassId }))
    }

    val natural = compareBy<Lesson>({ it.grade }, { it.klassId })
    val cmp: Comparator<Lesson> = if (!s.sortByClass) {
        val primary = compareBy<Lesson> { it.slot }
        (if (s.descending) primary.reversed() else primary).thenComparator { a, b -> natural.compare(a, b) }
    } else {
        (if (s.descending) natural.reversed() else natural).thenBy { it.slot }
    }
    val shown = dayLessons
        .filter { (s.pinned.isEmpty() || it.klassId in s.pinned) && it.matches(query) }
        .sortedWith(cmp)
    val groups = shown.groupBy { if (s.sortByClass) it.klassId else it.slot.toString() }.values.toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan lekcji") },
                actions = {
                    IconButton(onClick = { scope.launch { Repo.sync() } }) {
                        if (st.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
                    }
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.Notifications, contentDescription = "Historia zmian")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (st.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(st.error, style = MaterialTheme.typography.bodyMedium)
                        if (st.lessons.isNotEmpty()) {
                            Text(
                                "Pokazuję ostatnio pobrany plan.",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = day) {
                DAY_SHORT.forEachIndexed { i, label ->
                    Tab(selected = day == i, onClick = { day = i }, text = { Text(label) })
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Sortuj:", style = MaterialTheme.typography.labelLarge)
                FilterChip(
                    selected = !s.sortByClass,
                    onClick = { Repo.updateSettings { it.copy(sortByClass = false) } },
                    label = { Text("Godzina") },
                )
                FilterChip(
                    selected = s.sortByClass,
                    onClick = { Repo.updateSettings { it.copy(sortByClass = true) } },
                    label = { Text("Klasa") },
                )
                IconButton(onClick = { Repo.updateSettings { it.copy(descending = !it.descending) } }) {
                    Icon(
                        if (s.descending) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (s.descending) "Malejąco" else "Rosnąco",
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Szukaj: nauczyciel, sala, przedmiot") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Wyczyść")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            if (classes.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = s.pinned.isEmpty(),
                            onClick = { Repo.updateSettings { it.copy(pinned = emptySet()) } },
                            label = { Text("Wszystkie") },
                        )
                    }
                    items(classes, key = { it.klassId }) { c ->
                        FilterChip(
                            selected = c.klassId in s.pinned,
                            onClick = {
                                Repo.updateSettings {
                                    it.copy(pinned = if (c.klassId in it.pinned) it.pinned - c.klassId else it.pinned + c.klassId)
                                }
                            },
                            label = { Text(c.klass) },
                        )
                    }
                }
            }

            if (st.lastOk > 0L) {
                Text(
                    "Plan pobrano: ${dateTimeFmt.format(Date(st.lastOk))}" +
                        if (s.pinned.isNotEmpty()) " · Moje klasy: ${s.pinned.size}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            when {
                                st.lessons.isEmpty() && st.loading -> "Pobieranie planu…"
                                st.lessons.isEmpty() -> "Brak danych. Sprawdź link w Ustawieniach i odśwież."
                                else -> "Brak lekcji dla wybranego dnia i filtrów."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                for (g in groups) {
                    val first = g.first()
                    item(key = "h|${if (s.sortByClass) first.klassId else first.slot}") {
                        Text(
                            if (s.sortByClass) {
                                first.klass + (first.students?.let { " · $it uczniów" } ?: "")
                            } else {
                                "${first.slot}. lekcja · ${first.start}–${first.end}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(g, key = { it.key }) { l ->
                        LessonCard(l, byClass = s.sortByClass, partners = partners[l.key])
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LessonCard(l: Lesson, byClass: Boolean, partners: List<String>?) {
    val joint = l.yellow || partners != null
    val dark = isSystemInDarkTheme()
    val container = when {
        joint && dark -> Color(0xFF4D4420)
        joint -> Color(0xFFFFF3BF)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (byClass) "${l.start}–${l.end}" else l.klass + (l.students?.let { " · $it uczn." } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    l.subject.ifBlank { if (l.substitution) "Zastępstwo" else "—" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (l.teacher.isNotBlank()) {
                    Text(
                        l.teacher,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (l.substitution || joint) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (l.substitution) {
                            Pill("Zastępstwo", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                        }
                        if (joint) {
                            Pill(
                                if (!partners.isNullOrEmpty()) "Łączone z: ${partners.joinToString(", ")}" else "Zajęcia łączone",
                                Color(0xFFE0A800), Color(0xFF241A00),
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    l.room.ifBlank { "—" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (l.building.isNotBlank()) {
                    Text(
                        l.building,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (byClass) {
                    Text(
                        "${l.slot}. lekcja",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- historia zmian

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(st: UiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historia zmian") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    if (st.history.isNotEmpty()) {
                        IconButton(onClick = { Repo.clearHistory() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Wyczyść historię")
                        }
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(inner).fillMaxSize(),
        ) {
            if (st.history.isEmpty()) {
                item {
                    Text(
                        "Brak wykrytych zmian. Pojawią się tutaj, gdy plan w pliku zostanie zmieniony.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(st.history) { c ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Wykryto: ${dateTimeFmt.format(Date(c.ts))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(c.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(c.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ustawienia

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(st: UiState, onBack: () -> Unit) {
    val s = st.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable(s.url) { mutableStateOf(s.url) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Link do pliku Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Udostępniony link (SharePoint, OneDrive, Google Sheets). Aplikacja sama pobiera najnowszą wersję.") },
            )
            Button(onClick = {
                Repo.setUrl(url)
                scope.launch { Repo.sync() }
            }) { Text("Zapisz i odśwież") }

            HorizontalDivider()

            Text("Sprawdzaj zmiany w tle co", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val options = listOf(15 to "15 min", 30 to "30 min", 60 to "1 godz.", 120 to "2 godz.", 360 to "6 godz.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { (m, label) ->
                    FilterChip(
                        selected = s.intervalMin == m,
                        onClick = { Repo.updateSettings { it.copy(intervalMin = m) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Android pozwala na sprawdzanie nie częściej niż co 15 minut, a w trybie oszczędzania baterii system może to opóźniać. " +
                    "Gdy aplikacja jest otwarta, plan odświeża się co 2 minuty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text("Powiadomienia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SwitchRow("Powiadamiaj o zmianach w planie", s.notify) { v -> Repo.updateSettings { it.copy(notify = v) } }
            SwitchRow("Tylko dla wybranych klas (chipy na ekranie głównym)", s.onlyPinned) { v ->
                Repo.updateSettings { it.copy(onlyPinned = v) }
            }
            if (!Notifier.canNotify(ctx)) {
                Text(
                    "Powiadomienia są obecnie zablokowane dla tej aplikacji w systemie.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    Notifier.notifyChanges(
                        ctx,
                        listOf(
                            Change(
                                System.currentTimeMillis(), 0, 1, "8:00", "4TIE", "MOD",
                                "Sala 3.3 → 1.2 (to jest powiadomienie testowe)",
                            )
                        ),
                    )
                }) { Text("Wyślij test") }
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Ustawienia systemowe") }
            }

            HorizontalDivider()

            Text("Działanie w tle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Jeśli powiadomienia nie przychodzą, gdy aplikacja jest zamknięta, wyłącz dla niej optymalizację baterii " +
                    "(w niektórych telefonach także „uśpione aplikacje”).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Ustawienia baterii") }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
