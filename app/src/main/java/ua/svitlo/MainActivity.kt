package ua.svitlo

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

const val GH_REPO = "RamzST-MC/svitlo"
val QUEUES = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")
typealias Sched = Map<String, Map<String, List<Int>>> // queue -> date -> 24 values: 0 є, 1 нема, 2 можливо

fun parse(json: String): Sched {
    val q = JSONObject(json).getJSONObject("queues")
    return q.keys().asSequence().associateWith { k ->
        val d = q.getJSONObject(k)
        d.keys().asSequence().associateWith { day -> val a = d.getJSONArray(day); List(24) { a.optInt(it) } }
    }
}
fun toJson(s: Sched) = JSONObject().put("queues", JSONObject().also { o ->
    s.forEach { (q, days) -> o.put(q, JSONObject().also { d -> days.forEach { (k, v) -> d.put(k, JSONArray(v)) } }) }
}).toString()

class Repo(ctx: Context) {
    private val sp = ctx.getSharedPreferences("svitlo", 0)
    var server: String get() = sp.getString("server", "http://10.0.2.2:3000")!!; set(v) = sp.edit().putString("server", v.trim()).apply()
    var pin: String get() = sp.getString("pin", "1234")!!; set(v) = sp.edit().putString("pin", v).apply()
    var token: String get() = sp.getString("token", "")!!; set(v) = sp.edit().putString("token", v.trim()).apply()
    var queue: String get() = sp.getString("queue", "1.1")!!; set(v) = sp.edit().putString("queue", v).apply()
    var cache: String? get() = sp.getString("cache", null); set(v) = sp.edit().putString("cache", v).apply()

    private fun conn(path: String, method: String) = (URL(server.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 8000; readTimeout = 8000
    }
    suspend fun load(): Result<Sched> = withContext(Dispatchers.IO) {
        runCatching { val t = conn("/schedule", "GET").inputStream.bufferedReader().readText(); cache = t; parse(t) }
    }
    suspend fun latestRelease(): Result<Pair<Int, String>?> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$GH_REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "svitlo-app"); c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode == 404) return@runCatching null
            if (c.responseCode != 200) error("GitHub HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val v = o.getString("tag_name").trim('v', 'V').toIntOrNull() ?: return@runCatching null
            val a = o.getJSONArray("assets")
            val url = if (a.length() > 0) a.getJSONObject(0).getString("browser_download_url") else o.getString("html_url")
            v to url
        }
    }
    suspend fun send(path: String, body: String, contentType: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = conn(path, if (path == "/schedule") "PUT" else "POST")
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType); c.setRequestProperty("X-Admin-Token", token)
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().readText()
        }
    }
}

class VM(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    var sched by mutableStateOf<Sched>(emptyMap())
    var status by mutableStateOf("")
    var queue by mutableStateOf(repo.queue)
    var admin by mutableStateOf(false)
    var notice by mutableStateOf("")
    var update by mutableStateOf<Pair<Int, String>?>(null)
    private var lastCheck = 0L
    var updMsg by mutableStateOf("")
    fun checkUpdate(force: Boolean = false) = viewModelScope.launch {
        if (!force && System.currentTimeMillis() - lastCheck < 3_600_000) return@launch
        lastCheck = System.currentTimeMillis()
        if (force) updMsg = "Перевірка…"
        repo.latestRelease().onSuccess { r ->
            update = r?.takeIf { it.first > BuildConfig.VERSION_CODE }
            if (force) updMsg = when {
                r == null -> "На GitHub ще немає релізів (перевірте Actions)"
                update != null -> "Є нова версія ${r.first}"
                else -> "У вас остання версія: ${BuildConfig.VERSION_CODE}, на GitHub: ${r.first}"
            }
        }.onFailure { if (force) updMsg = "Не вдалося перевірити: ${it.message}" }
    }
    fun readNotice() { notice = runCatching { JSONObject(repo.cache ?: "").optString("notice", "") }.getOrDefault("") }

    init { repo.cache?.let { runCatching { sched = parse(it) }; readNotice() }; refresh() }

    fun refresh() = viewModelScope.launch {
        status = "Оновлення…"
        repo.load().onSuccess { sched = it; status = "Оновлено"; readNotice() }.onFailure { status = "Немає зв’язку з сервером, показано збережені дані" }
    }
    fun day(q: String, date: String) = sched[q]?.get(date) ?: List(24) { 0 }
    fun cycle(q: String, date: String, h: Int) {
        val a = day(q, date).toMutableList(); a[h] = (a[h] + 1) % 3
        sched = sched + (q to ((sched[q] ?: emptyMap()) + (date to a)))
    }
    fun fill(q: String, date: String, v: Int) { sched = sched + (q to ((sched[q] ?: emptyMap()) + (date to List(24) { v }))) }
    fun push() = viewModelScope.launch {
        status = "Збереження…"
        repo.send("/schedule", toJson(sched), "application/json").onSuccess { status = "Збережено на сервері" }.onFailure { status = "Помилка: ${it.message}" }
    }
    fun importText(date: String, text: String) = viewModelScope.launch {
        status = "Імпорт…"
        repo.send("/import?date=$date", text, "text/plain").onSuccess { status = "Імпортовано"; refresh() }.onFailure { status = "Помилка: ${it.message}" }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { App() } }
    }
}

val GREEN = Color(0xFF2E9D5B); val RED = Color(0xFFD9463E); val AMBER = Color(0xFFE8A21C)
fun cellColor(v: Int) = when (v) { 1 -> RED; 2 -> AMBER; else -> GREEN }

@Composable
fun App(vm: VM = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { vm.refresh(); vm.checkUpdate() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { while (true) { delay(5 * 60_000L); vm.refresh() } }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("💡") }, label = { Text("Графік") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("⚙️") }, label = { Text("Адмін") })
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tab == 0) UserScreen(vm) else AdminScreen(vm)
        }
    }
}

@Composable
fun QueueChips(sel: String, onPick: (String) -> Unit) {
    QUEUES.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { q -> FilterChip(selected = q == sel, onClick = { onPick(q) }, label = { Text("Черга $q") }) }
        }
    }
}

@Composable
fun HourGrid(a: List<Int>, nowH: Int, onTap: ((Int) -> Unit)?) {
    a.indices.chunked(6).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { h ->
                Box(Modifier.weight(1f).aspectRatio(1.2f).background(cellColor(a[h]), RoundedCornerShape(8.dp))
                    .let { if (h == nowH) it.background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)) else it }
                    .let { m -> if (onTap != null) m.clickable { onTap(h) } else m }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d:00".format(h), color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("–%02d:00".format(h + 1), color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun UserScreen(vm: VM) {
    var dayOff by remember { mutableIntStateOf(0) }
    val now = LocalDateTime.now()
    val today = LocalDate.now().toString(); val tomorrow = LocalDate.now().plusDays(1).toString()
    val a = vm.day(vm.queue, today); val b = vm.day(vm.queue, tomorrow)
    val cur = a[now.hour]
    var i = 1
    while (i <= 48 && (if (now.hour + i < 24) a[now.hour + i] else b[(now.hour + i) % 24]) == cur) i++
    val left = i * 60 - now.minute

    Text("Графік відключень", style = MaterialTheme.typography.headlineSmall)
    if (vm.notice.isNotBlank()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AMBER.copy(alpha = 0.25f))) { Text(vm.notice, Modifier.padding(12.dp)) }
    vm.update?.let { u ->
        val ctx = LocalContext.current
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = GREEN.copy(alpha = 0.25f))) {
            Column(Modifier.padding(12.dp)) {
                Text("Доступна нова версія застосунку (${u.first})")
                Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.second))) }) { Text("Завантажити") }
            }
        }
    }
    QueueChips(vm.queue) { vm.queue = it; vm.repo.queue = it }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(when (cur) { 1 -> "Світла немає"; 2 -> "Можливе відключення"; else -> "Світло є" }, style = MaterialTheme.typography.headlineMedium, color = cellColor(cur))
            val at = "%02d:00".format((now.hour + i) % 24)
            Text(
                if (i > 48) "Змін найближчим часом не заплановано"
                else (if (cur != 0) "Світло з’явиться о $at" else "Наступне відключення о $at") + " (через ${left / 60} год ${left % 60} хв)",
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(dayOff == 0, { dayOff = 0 }, { Text("Сьогодні") }); FilterChip(dayOff == 1, { dayOff = 1 }, { Text("Завтра") })
    }
    val shown = if (dayOff == 0) a else b
    Text("Протягом доби", style = MaterialTheme.typography.titleMedium)
    Box(Modifier.fillMaxWidth().height(28.dp)) {
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
            shown.forEach { v -> Box(Modifier.weight(1f).fillMaxHeight().background(cellColor(v))) }
        }
        if (dayOff == 0) {
            val f = ((now.hour * 60 + now.minute) / 1440f).coerceIn(0.001f, 0.999f)
            Row(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(f))
                Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
                Spacer(Modifier.weight(1f - f))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
    var h = 0
    while (h < 24) {
        val s = shown[h]; var j = h; while (j < 24 && shown[j] == s) j++
        val st = h; val en = j
        val isNow = dayOff == 0 && now.hour in st until en
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cellColor(s).copy(alpha = if (isNow) 0.30f else 0.12f))) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(cellColor(s), RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("%02d:00 – %02d:00".format(st, en), fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(when (s) { 1 -> "Світла немає"; 2 -> "Можливе відключення"; else -> "Світло є" }, style = MaterialTheme.typography.bodySmall)
                }
                Text("${en - st} год" + if (isNow) " · зараз" else "", style = MaterialTheme.typography.bodyMedium)
            }
        }
        h = j
    }
    Text("${vm.status} · версія ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.refresh() }) { Text("Оновити графік") }
        OutlinedButton(onClick = { vm.checkUpdate(true) }) { Text("Перевірити оновлення") }
    }
    if (vm.updMsg.isNotBlank()) Text(vm.updMsg, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun AdminScreen(vm: VM) {
    var pin by remember { mutableStateOf("") }; var err by remember { mutableStateOf(false) }
    Text("Адмін-меню", style = MaterialTheme.typography.headlineSmall)
    if (!vm.admin) {
        OutlinedTextField(pin, { pin = it }, label = { Text("PIN (за замовчуванням 1234)") }, visualTransformation = PasswordVisualTransformation(), isError = err)
        Button(onClick = { if (pin == vm.repo.pin) vm.admin = true else err = true }) { Text("Увійти") }
        return
    }
    var q by remember { mutableStateOf("1.1") }; var dayOff by remember { mutableIntStateOf(0) }
    val date = LocalDate.now().plusDays(dayOff.toLong()).toString()
    QueueChips(q) { q = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(dayOff == 0, { dayOff = 0 }, { Text("Сьогодні") }); FilterChip(dayOff == 1, { dayOff = 1 }, { Text("Завтра") })
    }
    HourGrid(vm.day(q, date), -1) { vm.cycle(q, date, it) }
    Text("Натискайте години: є → немає → можливо", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ vm.fill(q, date, 1) }) { Text("Усі: немає") }; OutlinedButton({ vm.fill(q, date, 0) }) { Text("Усі: є") }
    }
    Button(onClick = { vm.push() }, Modifier.fillMaxWidth()) { Text("Зберегти на сервер") }

    Text("Імпорт з тексту джерела", style = MaterialTheme.typography.titleMedium)
    var txt by remember { mutableStateOf("") }
    OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("1.1: 00:00-04:00, 12:00-16:00") })
    Button(onClick = { vm.importText(date, txt) }) { Text("Імпортувати на сервер") }

    Text("Сервер і доступ", style = MaterialTheme.typography.titleMedium)
    var srv by remember { mutableStateOf(vm.repo.server) }; var tok by remember { mutableStateOf(vm.repo.token) }; var np by remember { mutableStateOf("") }
    OutlinedTextField(srv, { srv = it }, Modifier.fillMaxWidth(), label = { Text("Адреса сервера") })
    OutlinedTextField(tok, { tok = it }, Modifier.fillMaxWidth(), label = { Text("Адмін-токен сервера") }, visualTransformation = PasswordVisualTransformation())
    OutlinedTextField(np, { np = it }, Modifier.fillMaxWidth(), label = { Text("Новий PIN (мін. 4 символи)") }, visualTransformation = PasswordVisualTransformation())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ vm.repo.server = srv; vm.repo.token = tok; if (np.length >= 4) vm.repo.pin = np; vm.refresh() }) { Text("Зберегти") }
        OutlinedButton({ vm.admin = false }) { Text("Вийти") }
    }
    Text(vm.status, style = MaterialTheme.typography.bodySmall)
}
