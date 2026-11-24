package ua.svitlo

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

const val GH_REPO = "RamzST-MC/svitlo"
val QUEUES = listOf("1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2")
private val UKRAINIAN_LATIN = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g", 'д' to "d", 'е' to "e",
    'є' to "ye", 'ж' to "zh", 'з' to "z", 'и' to "y", 'і' to "i", 'ї' to "yi", 'й' to "i",
    'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
    'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
    'ш' to "sh", 'щ' to "shch", 'ь' to "", 'ю' to "yu", 'я' to "ya"
)
private fun latinizeUkrainian(value: String) = buildString {
    value.lowercase().forEach { append(UKRAINIAN_LATIN[it] ?: it) }
}

@Composable
private fun AddressTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    label: String,
    placeholder: String,
    imeAction: Int
) {
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val outline = MaterialTheme.colorScheme.outline.toArgb()
    val suppressTextCallback = remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                EditText(context).apply {
                    setSingleLine(true)
                    hint = placeholder
                    setText(value)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(onSurface)
                    setHintTextColor(outline)
                    setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    imeHintLocales = android.os.LocaleList.forLanguageTags("uk")
                    this.imeOptions = imeAction
                    background = GradientDrawable().apply {
                        setColor(surface)
                        cornerRadius = 6 * density
                        setStroke((1 * density).toInt(), outline)
                    }
                    setOnClickListener { onFocusChanged(true) }
                    setOnFocusChangeListener { _, hasFocus -> onFocusChanged(hasFocus) }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (!suppressTextCallback.value) onValueChange(s?.toString().orEmpty())
                        }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                }
            },
            update = { editText ->
                if (editText.text.toString() != value) {
                    suppressTextCallback.value = true
                    editText.setText(value)
                    editText.setSelection(value.length)
                    suppressTextCallback.value = false
                }
                editText.imeHintLocales = android.os.LocaleList.forLanguageTags("uk")
            }
        )
    }
}

private data class AddressOption(val id: String, val text: String)

@Composable
private fun AddressSuggestions(
    options: List<AddressOption>,
    query: String,
    expanded: Boolean,
    onSelect: (AddressOption) -> Unit
) {
    if (!expanded || options.isEmpty()) return
    val normalized = query.trim().lowercase()
    val latinQuery = latinizeUkrainian(normalized)
    val matches = options.asSequence()
        .filter { normalized.isBlank() || it.text.lowercase().contains(normalized) || latinizeUkrainian(it.text).contains(latinQuery) }
        .distinctBy(AddressOption::id)
        .take(8)
        .toList()
    if (matches.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            matches.forEach { option ->
                Text(
                    option.text,
                    Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (option != matches.last()) HorizontalDivider()
            }
        }
    }
}

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
    var server: String
        get() = sp.getString("server", "http://10.0.2.2:3000")!!
        set(v) = sp.edit { putString("server", v.trim()) }
    var pin: String
        get() = sp.getString("pin", "1234")!!
        set(v) = sp.edit { putString("pin", v) }
    var token: String
        get() = sp.getString("token", "")!!
        set(v) = sp.edit { putString("token", v.trim()) }
    var queue: String
        get() = sp.getString("queue", "1.1")!!
        set(v) = sp.edit { putString("queue", v) }
    var showQueueSelector: Boolean
        get() = sp.getBoolean("show_queue_selector", false)
        set(v) = sp.edit { putBoolean("show_queue_selector", v) }
    var notificationsEnabled: Boolean
        get() = sp.getBoolean("notifications_enabled", false)
        set(v) = sp.edit { putBoolean("notifications_enabled", v) }
    var homeTown: String
        get() = sp.getString("home_town", "")!!
        set(v) = sp.edit { putString("home_town", v) }
    var homeStreet: String
        get() = sp.getString("home_street", "")!!
        set(v) = sp.edit { putString("home_street", v) }
    var homeHouse: String
        get() = sp.getString("home_house", "")!!
        set(v) = sp.edit { putString("home_house", v) }
    var cache: String?
        get() = sp.getString("cache", null)
        set(v) = sp.edit { putString("cache", v) }
    var themeMode: String
        get() = sp.getString("theme_mode", "system")!!
        set(v) = sp.edit { putString("theme_mode", v) }
    var maintenanceAccessCode: String
        get() = sp.getString("maintenance_access_code", "")!!
        set(v) = sp.edit { putString("maintenance_access_code", v) }

    private fun conn(path: String, method: String) = (URL(server.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 5000; readTimeout = 5000
        setRequestProperty("Cache-Control", "no-cache")
    }
    suspend fun load(): Result<Sched> = withContext(Dispatchers.IO) {
        runCatching { val t = conn("/schedule", "GET").inputStream.bufferedReader().readText(); cache = t; parse(t) }
    }
    suspend fun verifyMaintenanceAccess(code: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = conn("/maintenance-access", "POST")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(code.toByteArray(Charsets.UTF_8)) }
            c.responseCode == 200
        }.getOrDefault(false)
    }
    suspend fun latestRelease(): Result<Pair<Int, String>?> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$GH_REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "svitlo-app"); c.setRequestProperty("Accept", "application/vnd.github+json")
            val responseCode = c.responseCode
            if (responseCode == 404) return@runCatching null
            if (responseCode != 200) {
                val errorBody = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if ((responseCode == 403) && errorBody.contains("rate limit", ignoreCase = true)) {
                    error("GitHub тимчасово обмежив перевірки. Спробуйте пізніше.")
                }
                error("GitHub HTTP $responseCode")
            }
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = o.getString("tag_name").trimStart('v', 'V')
            val releaseVersionCode = o.optString("body")
                .lineSequence()
                .firstOrNull { it.startsWith("Android version code:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
            val v = releaseVersionCode ?: tag.toIntOrNull() ?: run {
                val parts = tag.split('.').mapNotNull(String::toIntOrNull)
                if (parts.size !in 2..3) return@runCatching null
                val (major, minor) = parts
                val patch = parts.getOrElse(2) { 0 }
                major * 10_000 + minor * 100 + patch
            }
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
    var adminAccess by mutableStateOf(false)
    var notice by mutableStateOf("")
    var maintenance by mutableStateOf(false)
    var maintenanceMessage by mutableStateOf("")
    var maintenanceUntil by mutableStateOf("")
    var maintenanceTimeZone by mutableStateOf("")
    var maintenanceBypass by mutableStateOf(false)
    var maintenanceAccessCodeInput by mutableStateOf("")
    var maintenanceAccessStatus by mutableStateOf("")
    var themeMode by mutableStateOf(repo.themeMode)
    var notificationsEnabled by mutableStateOf(repo.notificationsEnabled)
    var update by mutableStateOf<Pair<Int, String>?>(null)
    var updateTestMode by mutableStateOf(false)
    private var lastCheck = 0L
    private var refreshInProgress = false
    var updMsg by mutableStateOf("")
    val installedVersionCode: Long = BuildConfig.APP_VERSION_CODE.toLong()
    val installedVersionName: String = BuildConfig.APP_VERSION_NAME
    fun checkUpdate(force: Boolean = false, testMode: Boolean = false) = viewModelScope.launch {
        if (!force && System.currentTimeMillis() - lastCheck < 3_600_000) return@launch
        lastCheck = System.currentTimeMillis()
        if (force) updMsg = "Перевірка…"
        repo.latestRelease().onSuccess { r ->
            updateTestMode = testMode
            update = r?.takeIf { (versionCode, _) -> testMode || versionCode.toLong() > installedVersionCode }
            if (force) updMsg = when {
                r == null -> "На GitHub ще немає релізів (перевірте Actions)"
                testMode -> "Тест пройшов: реліз ${r.first} знайдено. Перевірте картку та кнопку завантаження."
                update != null -> "Є нова версія ${r.first}"
                else -> "У вас остання версія: $installedVersionCode, на GitHub: ${r.first}"
            }
        }.onFailure {
            updateTestMode = false
            if (force) updMsg = "Не вдалося перевірити: ${it.message}"
        }
    }
    var serverTimeOffsetMillis by mutableStateOf(0L)

    fun readNotice() {
        notice = runCatching {
            val data = JSONObject(repo.cache ?: "")
            adminAccess = data.optBoolean("adminAccess", false)
            maintenance = data.optBoolean("maintenance")
            maintenanceMessage = data.optString("maintenanceMessage")
            maintenanceUntil = data.optString("maintenanceUntil")
            maintenanceTimeZone = data.optString("maintenanceTimeZone")
            val serverTimeStr = data.optString("serverTime")
            if (serverTimeStr.isNotBlank()) {
                runCatching {
                    val serverMillis = Instant.parse(serverTimeStr).toEpochMilli()
                    serverTimeOffsetMillis = serverMillis - System.currentTimeMillis()
                }
            }
            buildList {
                if (maintenance) add(maintenanceMessage.ifBlank { "У застосунку тривають технічні роботи. Спробуйте пізніше." })
                data.optString("notice").takeIf(String::isNotBlank)?.let(::add)
            }.joinToString("\n")
        }.getOrDefault("")
    }
    fun updateNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
        repo.notificationsEnabled = enabled
    }

    fun changeThemeMode(mode: String) {
        themeMode = mode
        repo.themeMode = mode
    }

    fun leaveMaintenanceAccess() {
        repo.maintenanceAccessCode = ""
        maintenanceBypass = false
        maintenanceAccessCodeInput = ""
        maintenanceAccessStatus = ""
    }

    fun authorizeDuringMaintenance() = viewModelScope.launch {
        maintenanceAccessStatus = "Перевіряю код…"
        val code = maintenanceAccessCodeInput.trim()
        if (code.isNotBlank() && repo.verifyMaintenanceAccess(code)) {
            repo.maintenanceAccessCode = code
            maintenanceBypass = true
            maintenanceAccessCodeInput = ""
            maintenanceAccessStatus = "Доступ надано"
        } else {
            maintenanceAccessStatus = "Код невірний або доступ не налаштований"
        }
    }

    init { repo.cache?.let { runCatching { sched = parse(it) }; readNotice() }; refresh() }

    fun refresh() = viewModelScope.launch {
        if (refreshInProgress) return@launch
        refreshInProgress = true
        status = "Оновлення…"
        try {
            repo.load().onSuccess {
                sched = it
                status = "Оновлено"
                readNotice()
                if (maintenance) {
                    maintenanceBypass = repo.maintenanceAccessCode.isNotBlank() && repo.verifyMaintenanceAccess(repo.maintenanceAccessCode)
                } else {
                    maintenanceBypass = false
                    repo.maintenanceAccessCode = ""
                }
            }.onFailure { status = "Немає зв’язку з сервером, показано збережені дані" }
        } finally {
            refreshInProgress = false
        }
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

object PowerNotifications {
    private const val CHANNEL_ID = "power_schedule_alerts_v2"
    private const val PREFS = "scheduled_power_notifications"
    private const val IDS_KEY = "ids"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Відключення світла", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Сповіщення за годину до відключення та очікуваного ввімкнення світла"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                )
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context, queue: String, schedule: Sched) {
        cancel(context)
        if (!Repo(context).notificationsEnabled ||
            (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        ) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val ids = mutableSetOf<String>()
        for (offset in 0..2) {
            val date = today.plusDays(offset.toLong())
            val hours = schedule[queue]?.get(date.toString()) ?: continue
            for (hour in 0..23) {
                val current = hours.getOrElse(hour) { 0 }
                val previous = if (hour > 0) hours.getOrElse(hour - 1) { current }
                    else schedule[queue]?.get(date.minusDays(1).toString())?.getOrElse(23) { current } ?: current
                val kind = when {
                    current == 1 && previous != 1 -> "off"
                    current == 0 && previous == 1 -> "on"
                    else -> null
                } ?: continue
                val eventAt = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val notifyAt = eventAt - 60 * 60 * 1000
                val key = "$queue-$date-$hour-$kind"
                val id = key.hashCode() and 0x7fffffff
                if (notifyAt <= now) {
                    if (eventAt > now && !isDelivered(context, id)) {
                        deliver(context, id, queue, kind, "%02d:00".format(hour))
                        markDelivered(context, id)
                    }
                    continue
                }
                val pending = PendingIntent.getBroadcast(
                    context, id,
                    Intent(context, PowerNotificationReceiver::class.java)
                        .setAction("ua.svitlo.POWER_SCHEDULE_ALERT")
                        .putExtra("id", id)
                        .putExtra("queue", queue)
                        .putExtra("kind", kind)
                        .putExtra("time", "%02d:00".format(hour)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt, pending)
                ids += id.toString()
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(IDS_KEY, ids) }
    }

    private fun isDelivered(context: Context, id: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("delivered", emptySet()).orEmpty().contains(id.toString())

    fun markDelivered(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet("delivered", emptySet()).orEmpty().toMutableSet()
        delivered.add(id.toString())
        prefs.edit { putStringSet("delivered", delivered) }
    }

    fun deliver(context: Context, id: Int, queue: String, kind: String, time: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val title = if (kind == "off") "Через годину вимкнуть світло" else "Через годину очікується світло"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("$time · черга $queue")
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun testSound(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = soundUri?.let { RingtoneManager.getRingtone(context, it) }
        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone?.play()
        ringtone?.let { sound ->
            Handler(Looper.getMainLooper()).postDelayed({ if (sound.isPlaying) sound.stop() }, 2500)
        }

        Toast.makeText(context, "Тестовий звук сповіщення відтворено", Toast.LENGTH_SHORT).show()
    }

    fun cancel(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        prefs.getStringSet(IDS_KEY, emptySet()).orEmpty().forEach { value ->
            val id = value.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(
                context, id, Intent(context, PowerNotificationReceiver::class.java).setAction("ua.svitlo.POWER_SCHEDULE_ALERT"),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) alarmManager.cancel(pending)
        }
        prefs.edit { remove(IDS_KEY) }
    }

    fun restoreAfterReboot(context: Context) {
        val repo = Repo(context)
        if (!repo.notificationsEnabled) return
        val cached = repo.cache ?: return
        runCatching { schedule(context, repo.queue, parse(cached)) }
    }
}

class PowerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val kind = intent.getStringExtra("kind") ?: return
        val id = intent.getIntExtra("id", 0)
        PowerNotifications.markDelivered(context, id)
        PowerNotifications.deliver(
            context, id, intent.getStringExtra("queue").orEmpty(), kind, intent.getStringExtra("time").orEmpty()
        )
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) PowerNotifications.restoreAfterReboot(context)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerNotifications.createChannel(this)
        setContent { SvitloRoot() }
    }
}

@Composable
private fun SvitloRoot() {
    val vm: VM = viewModel()
    val darkTheme = when (vm.themeMode) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        App(vm)
    }
}

val GREEN = Color(0xFF2E9D5B); val RED = Color(0xFFD9463E); val AMBER = Color(0xFFE8A21C)
fun cellColor(v: Int) = when (v) { 1 -> RED; 2 -> AMBER; else -> GREEN }

private fun makeAutoQueueScript(town: String, street: String, house: String): String {
    val address = JSONObject().put("town", town).put("street", street).put("house", house)
    return """
        (async function() {
            const address = $address;
            const bridge = window.SvitloAutoQueueBridge;
            const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
            const norm = value => (value || "").toLocaleLowerCase("uk").replace(/\\s+/g, " ").trim();
            const clean = value => norm(value).replace(/[^0-9a-zа-яіїєґ]/g, "");
            const selects = Array.from(document.querySelectorAll("select"));
            const ajax = (url, term) => new Promise(resolve => window.jQuery.ajax({
                url: url, type: "GET", dataType: "json",
                data: { term: term, q: term, _type: "query", page: 1 },
                success: data => resolve(data || []), error: () => resolve([])
            }));
            const wrapperUrl = index => {
                const select = selects[index];
                return select && select.parentElement && select.parentElement.getAttribute("data-urlaction");
            };
            const setOption = (select, option) => {
                if (!Array.from(select.options).some(item => item.value === String(option.id))) {
                    select.add(new Option(option.text, option.id));
                }
                window.jQuery(select).val(String(option.id)).trigger("change");
            };
            try {
                if (!window.jQuery || selects.length < 3) throw new Error("Сторінка черг ще не готова");
                const townTerm = address.town.replace(/\\s*\\([^)]*\\)\\s*/g, " ").replace(/^(м\\.|с\\.)\\s*/i, "").trim();
                const towns = await ajax(wrapperUrl(0) + "/", townTerm);
                const townOption = towns.find(item => norm(item.text).includes(norm(townTerm)));
                if (!townOption) throw new Error("Населений пункт не знайдено");
                setOption(selects[0], townOption);
                await wait(500);

                const streetTerm = address.street.replace(/^(вул\\.|вулиця|пров\\.|провулок|просп\\.|проспект)\\s*/i, "").trim();
                const streets = await ajax(wrapperUrl(1) + "/" + encodeURIComponent(townOption.id), streetTerm);
                const streetOption = streets.find(item => norm(item.text).includes(norm(streetTerm)));
                if (!streetOption) throw new Error("Вулицю не знайдено");
                setOption(selects[1], streetOption);

                let houseOption = null;
                const desiredHouse = clean(address.house);
                for (let attempt = 0; attempt < 60 && !houseOption; attempt++) {
                    houseOption = Array.from(selects[2].options).find(item =>
                        clean(item.text) === desiredHouse || clean(item.value) === desiredHouse
                    );
                    if (!houseOption) await wait(200);
                }
                if (!houseOption) throw new Error("Будинок не знайдено");
                window.jQuery(selects[2]).val(houseOption.value).trigger("change");
                for (let attempt = 0; attempt < 24; attempt++) {
                    await wait(500);
                    const values = (document.body.innerText || "").match(/[1-6][.,][12]/g) || [];
                    const queues = Array.from(new Set(values.map(value => value.replace(",", "."))));
                    if (queues.length) {
                        bridge.reportQueues(queues.join(","));
                        return;
                    }
                }
                throw new Error("Сайт не повернув номер черги");
            } catch (error) {
                bridge.reportError(String(error && error.message || error));
            }
        })();
    """.trimIndent()
}

@Composable
private fun AutoQueueChecker(vm: VM, checkKey: Int) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var lastCheckKey by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val runCheck = {
        val town = vm.repo.homeTown
        val street = vm.repo.homeStreet
        val house = vm.repo.homeHouse
        if (!vm.maintenance && town.isNotBlank() && street.isNotBlank() && house.isNotBlank()) {
            webView?.evaluateJavascript(makeAutoQueueScript(town, street, house), null)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.removeJavascriptInterface("SvitloAutoQueueBridge")
            webView?.destroy()
            webView = null
        }
    }
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.javaScriptEnabled = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun reportQueues(value: String) {
                        handler.post {
                            val queues = value.split(',').map(String::trim).filter { it in QUEUES }.distinct()
                            if (queues.size == 1) {
                                vm.queue = queues.single()
                                vm.repo.queue = queues.single()
                            } else if (vm.queue !in queues && queues.isNotEmpty()) {
                                // Several service connections exist for this building; keep the user's selected queue.
                            }
                        }
                    }
                    @JavascriptInterface
                    fun reportError(message: String) = Unit
                }, "SvitloAutoQueueBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host !in setOf("hoe.com.ua", "www.hoe.com.ua")
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url.startsWith("https://hoe.com.ua/shutdown/queue")) {
                            handler.post {
                                ready = true
                                val town = vm.repo.homeTown
                                val street = vm.repo.homeStreet
                                val house = vm.repo.homeHouse
                                if (!vm.maintenance && town.isNotBlank() && street.isNotBlank() && house.isNotBlank()) {
                                    view.evaluateJavascript(makeAutoQueueScript(town, street, house), null)
                                }
                                lastCheckKey = checkKey
                            }
                        }
                    }
                }
                loadUrl("https://hoe.com.ua/shutdown/queue")
            }
        },
        update = {
            if (ready && checkKey != lastCheckKey) {
                runCheck()
                lastCheckKey = checkKey
            }
        }
    )
}

@Composable
fun App(vm: VM = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var autoCheckKey by remember { mutableIntStateOf(0) }
    var refreshCountdown by remember { mutableIntStateOf(3) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
                vm.checkUpdate()
                autoCheckKey++
                refreshCountdown = 3
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            if (refreshCountdown <= 1) {
                vm.refresh()
                autoCheckKey++
                refreshCountdown = 3
            } else {
                refreshCountdown--
            }
        }
    }
    AutoQueueChecker(vm, autoCheckKey)
    val adminTabEnabled = vm.adminAccess && vm.maintenance && vm.maintenanceBypass
    LaunchedEffect(adminTabEnabled) {
        if (!adminTabEnabled && tab == 1) tab = 0
    }
    val appContext = LocalContext.current
    LaunchedEffect(vm.sched, vm.queue, vm.notificationsEnabled, vm.maintenance) {
        if (vm.notificationsEnabled && !vm.maintenance) PowerNotifications.schedule(appContext, vm.queue, vm.sched)
        else PowerNotifications.cancel(appContext)
    }
    if (vm.maintenance && !vm.maintenanceBypass) {
        MaintenanceScreen(vm)
    } else {
        Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("💡") }, label = { Text("Графік") })
            if (adminTabEnabled) {
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("🛠️") }, label = { Text("Адмін") })
            }
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("⚙️") }, label = { Text("Налаштування") })
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> UserScreen(vm, refreshCountdown)
                1 -> AdminScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
    }
}

@Composable
private fun MaintenanceIllustration() {
    Canvas(Modifier.size(190.dp)) {
        val scale = size.width / 180f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        val purple = Color(0xFF7657B5)
        val dark = Color(0xFF4E4560)

        drawCircle(Color(0xFFEDE3F7), radius = 84f * scale, center = point(90f, 90f))
        drawCircle(Color.White.copy(alpha = 0.62f), radius = 58f * scale, center = point(90f, 105f))
        drawCircle(purple.copy(alpha = 0.32f), radius = 43f * scale, center = point(90f, 108f), style = Stroke(6f * scale))
        for (i in 0 until 8) {
            val angle = i * Math.PI / 4.0
            val x = cos(angle).toFloat()
            val y = sin(angle).toFloat()
            drawLine(purple.copy(alpha = 0.45f), point(90f + x * 43f, 108f + y * 43f), point(90f + x * 52f, 108f + y * 52f), 5f * scale)
        }
        drawCircle(purple.copy(alpha = 0.32f), radius = 17f * scale, center = point(90f, 108f), style = Stroke(5f * scale))

        drawRoundRect(Color(0xFF86B8A2), point(52f, 100f), Size(76f * scale, 49f * scale), CornerRadius(20f * scale))
        drawCircle(Color(0xFFFFD3B0), radius = 22f * scale, center = point(90f, 76f))
        drawCircle(dark, radius = 2f * scale, center = point(82f, 77f))
        drawCircle(dark, radius = 2f * scale, center = point(98f, 77f))
        drawLine(Color(0xFFB66F63), point(84f, 88f), point(96f, 88f), 2f * scale)

        drawRoundRect(Color(0xFFFFC857), point(63f, 52f), Size(54f * scale, 17f * scale), CornerRadius(9f * scale))
        drawRoundRect(Color(0xFFFFC857), point(57f, 65f), Size(66f * scale, 8f * scale), CornerRadius(4f * scale))
        drawRoundRect(Color(0xFFFFE29A), point(87f, 53f), Size(6f * scale, 14f * scale), CornerRadius(3f * scale))

        drawLine(purple, point(48f, 127f), point(61f, 114f), 6f * scale)
        drawCircle(Color(0xFFFFC857), radius = 7f * scale, center = point(46f, 130f), style = Stroke(4f * scale))
        drawLine(Color(0xFFE59B45), point(119f, 116f), point(133f, 130f), 6f * scale)
        drawRoundRect(Color(0xFFE59B45), point(129f, 128f), Size(9f * scale, 17f * scale), CornerRadius(4f * scale))
    }
}

@Composable
private fun MaintenanceScreen(vm: VM) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.maintenanceUntil) {
        while (vm.maintenanceUntil.isNotBlank()) {
            nowMillis = System.currentTimeMillis()
            delay(1.seconds)
        }
    }
    val maintenanceEndMillis = remember(vm.maintenanceUntil) {
        runCatching { Instant.parse(vm.maintenanceUntil).toEpochMilli() }.getOrNull()
    }
    val maintenanceZone = remember(vm.maintenanceTimeZone) {
        runCatching { ZoneId.of(vm.maintenanceTimeZone) }.getOrElse { ZoneId.systemDefault() }
    }
    val pageColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        Modifier.fillMaxSize().background(pageColor).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MaintenanceIllustration()
        Spacer(Modifier.height(16.dp))
        Text(
            "Технічні роботи",
            color = textColor,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Text(
                vm.maintenanceMessage.ifBlank { "У застосунку тривають технічні роботи. Спробуйте пізніше." },
                modifier = Modifier.padding(20.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Застосунок буде доступний після завершення робіт.",
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
        maintenanceEndMillis?.let { endMillis ->
            Spacer(Modifier.height(12.dp))
            val adjustedNowMillis = nowMillis + vm.serverTimeOffsetMillis
            val remaining = (endMillis - adjustedNowMillis).coerceAtLeast(0L)
            val totalSeconds = (remaining + 999) / 1_000
            val hours = totalSeconds / 3_600
            val minutes = (totalSeconds / 60) % 60
            val seconds = totalSeconds % 60
            val endTime = Instant.ofEpochMilli(endMillis)
                .atZone(maintenanceZone)
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            Text(
                "До завершення: %02d:%02d:%02d".format(hours, minutes, seconds),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Text("Орієнтовно до $endTime", color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = vm.maintenanceAccessCodeInput,
            onValueChange = { vm.maintenanceAccessCodeInput = it; vm.maintenanceAccessStatus = "" },
            label = { Text("Код доступу") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.authorizeDuringMaintenance() },
            modifier = Modifier.fillMaxWidth(),
            enabled = vm.maintenanceAccessCodeInput.isNotBlank()
        ) { Text("Увійти з кодом доступу") }
        if (vm.maintenanceAccessStatus.isNotBlank()) {
            Text(vm.maintenanceAccessStatus, color = textColor, style = MaterialTheme.typography.bodyMedium)
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
fun UserScreen(vm: VM, refreshCountdown: Int) {
    var dayOff by remember { mutableIntStateOf(0) }
    val now = LocalDateTime.now()
    val today = LocalDate.now().toString(); val tomorrow = LocalDate.now().plusDays(1).toString()
    val a = vm.day(vm.queue, today); val b = vm.day(vm.queue, tomorrow)
    val cur = a[now.hour]
    var i = 1
    while (i <= 48 && (if (now.hour + i < 24) a[now.hour + i] else b[(now.hour + i) % 24]) == cur) i++
    val left = i * 60 - now.minute

    Text("Графік відключень", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Ваша черга: ${vm.queue}",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        color = GREEN,
        textAlign = TextAlign.Center
    )
    if (vm.notice.isNotBlank()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AMBER.copy(alpha = 0.25f))) { Text(vm.notice, Modifier.padding(12.dp)) }
    if (vm.maintenance && vm.maintenanceBypass) {
        OutlinedButton(onClick = { vm.leaveMaintenanceAccess() }, modifier = Modifier.fillMaxWidth()) {
            Text("Вийти з доступу за кодом")
        }
    }
    vm.update?.let { u ->
        val ctx = LocalContext.current
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = GREEN.copy(alpha = 0.25f))) {
            Column(Modifier.padding(12.dp)) {
                Text(if (vm.updateTestMode) "Тестова картка оновлення (${u.first})" else "Доступна нова версія застосунку (${u.first})")
                Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, u.second.toUri())) }) { Text("Завантажити") }
            }
        }
    }
    if (vm.repo.showQueueSelector) {
        QueueChips(vm.queue) { vm.queue = it; vm.repo.queue = it }
    }
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

    Text(
        "${vm.status} · версія ${vm.installedVersionName} (${vm.installedVersionCode}) · наступне оновлення через ${refreshCountdown} с",
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(
        onClick = { vm.refresh() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Оновити графік")
    }
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
    var srv by remember { mutableStateOf(vm.repo.server) }
    var tok by remember { mutableStateOf(vm.repo.token) }; var np by remember { mutableStateOf("") }
    OutlinedTextField(srv, { srv = it }, Modifier.fillMaxWidth(), label = { Text("Адреса сервера графіків") })
    OutlinedTextField(tok, { tok = it }, Modifier.fillMaxWidth(), label = { Text("Адмін-токен сервера") }, visualTransformation = PasswordVisualTransformation())
    OutlinedTextField(np, { np = it }, Modifier.fillMaxWidth(), label = { Text("Новий PIN (мін. 4 символи)") }, visualTransformation = PasswordVisualTransformation())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ vm.repo.server = srv; vm.repo.token = tok; if (np.length >= 4) vm.repo.pin = np; vm.refresh() }) { Text("Зберегти") }
        OutlinedButton({ vm.admin = false }) { Text("Вийти") }
    }
    Text(vm.status, style = MaterialTheme.typography.bodySmall)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SettingsScreen(vm: VM) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateNotificationsEnabled(granted)
    }
    val testNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) PowerNotifications.testSound(context)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewReady by remember { mutableStateOf(false) }
    var pendingLookupScript by remember { mutableStateOf<String?>(null) }
    var town by remember { mutableStateOf(vm.repo.homeTown) }
    var street by remember { mutableStateOf(vm.repo.homeStreet) }
    var house by remember { mutableStateOf(vm.repo.homeHouse) }
    var foundQueues by remember { mutableStateOf<List<String>>(emptyList()) }
    var showQueueSelector by remember { mutableStateOf(vm.repo.showQueueSelector) }
    var townOptions by remember { mutableStateOf<List<AddressOption>>(emptyList()) }
    var streetOptions by remember { mutableStateOf<List<AddressOption>>(emptyList()) }
    var houseOptions by remember { mutableStateOf<List<AddressOption>>(emptyList()) }
    var townSuggestionsOpen by remember { mutableStateOf(false) }
    var streetSuggestionsOpen by remember { mutableStateOf(false) }
    var houseSuggestionsOpen by remember { mutableStateOf(false) }
    var lookupMessage by remember { mutableStateOf("") }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.removeJavascriptInterface("SvitloQueueBridge")
            webView?.destroy()
            webView = null
        }
    }

    fun requestAddressOptions(kind: String, query: String, savedTown: String = town) {
        if (!webViewReady || query.trim().isEmpty() || kind == "house") return
        val kindJson = JSONObject.quote(kind)
        val queryJson = JSONObject.quote(query.trim())
        val townJson = JSONObject.quote(
            savedTown.substringBefore("(").replace(Regex("^(м\\.|с\\.)\\s*"), "").trim()
        )
        val script = """
            (function() {
                const kind = $kindJson;
                const query = $queryJson;
                const townQuery = $townJson;
                const bridge = window.SvitloQueueBridge;
                const selects = Array.from(document.querySelectorAll("select"));
                const index = kind === "town" ? 0 : 1;
                const select = selects[index];
                if (!select || !window.jQuery) return;
                const wrapper = select.parentElement;
                let url = wrapper && wrapper.getAttribute("data-urlaction");
                if (!url) return;
                window.__svitloSearchTimers = window.__svitloSearchTimers || {};
                clearTimeout(window.__svitloSearchTimers[kind]);
                window.__svitloSearchTimers[kind] = setTimeout(async function() {
                    const ajax = (requestUrl, term) => new Promise(resolve => {
                        window.jQuery.ajax({
                            url: requestUrl,
                            type: "GET",
                            dataType: "json",
                            data: { term: term, q: term, _type: "query", page: 1 },
                            success: data => resolve(data || []),
                            error: () => resolve([])
                        });
                    });
                    if (kind === "street") {
                        const townSelect = selects[0];
                        let townId = townSelect && townSelect.value;
                        if (!townId && townQuery) {
                            const townWrapper = townSelect && townSelect.parentElement;
                            const townUrl = townWrapper && townWrapper.getAttribute("data-urlaction");
                            const towns = townUrl ? await ajax(townUrl + "/", townQuery) : [];
                            const norm = value => (value || "").replace(/\s+/g, " ").trim().toLocaleLowerCase("uk");
                            const match = towns.find(item => norm(item.text).includes(norm(townQuery)));
                            if (match) {
                                const option = new Option(match.text, match.id, true, true);
                                townSelect.add(option);
                                window.jQuery(townSelect).val(match.id).trigger("change");
                                townId = match.id;
                            }
                        }
                        if (!townId) {
                            bridge.reportOptions("street", "[]");
                            return;
                        }
                        url += "/" + encodeURIComponent(townId);
                    } else {
                        url += "/";
                    }
                    const results = await ajax(url, query);
                    bridge.reportOptions(kind, JSON.stringify(results));
                }, 220);
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script, null)
    }

    fun chooseAddressOption(index: Int, id: String, text: String, nextKind: String?) {
        val idJson = JSONObject.quote(id)
        val textJson = JSONObject.quote(text)
        val nextKindJson = nextKind?.let(JSONObject::quote) ?: "null"
        val script = """
            (async function() {
                const selects = Array.from(document.querySelectorAll("select"));
                const select = selects[$index];
                if (!select || !window.jQuery) return;
                const norm = value => (value || "").replace(/\s+/g, " ").trim().toLocaleLowerCase("uk");
                let option = $index < 2
                    ? Array.from(select.options).find(item => String(item.value) === $idJson)
                    : Array.from(select.options).find(item => norm(item.text) === norm($textJson));
                if (!option && $index < 2) {
                    option = new Option($textJson, $idJson, true, true);
                    select.add(option);
                }
                if (!option) return;
                window.jQuery(select).val(option.value).trigger("change");
                const nextKind = $nextKindJson;
                if (!nextKind || nextKind === "street") return;
                const getOptions = current => current ? Array.from(current.options)
                    .map(item => item.text.replace(/\s+/g, " ").trim())
                    .filter(value => value && value.toLocaleLowerCase() !== "-вибрати-")
                    .map(value => ({ id: value, text: value })) : [];
                for (let attempt = 0; attempt < 60; attempt++) {
                    const list = getOptions(selects[$index + 1]);
                    if (list.length) {
                        window.SvitloQueueBridge.reportOptions(nextKind, JSON.stringify(list));
                        return;
                    }
                    await new Promise(resolve => setTimeout(resolve, 200));
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script, null)
    }

    Text("Тема застосунку", style = MaterialTheme.typography.titleLarge)
    listOf("system" to "Як у системі", "light" to "Світла", "dark" to "Темна").forEach { (mode, label) ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.changeThemeMode(mode) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = vm.themeMode == mode, onClick = { vm.changeThemeMode(mode) })
            Text(label)
        }
    }
    if (vm.maintenance && vm.maintenanceBypass) {
        OutlinedButton(onClick = { vm.leaveMaintenanceAccess() }, modifier = Modifier.fillMaxWidth()) {
            Text("Вийти з доступу на час технічних робіт")
        }
    }

    Text("Моя адреса", style = MaterialTheme.typography.headlineSmall)
    Text("Вкажіть адресу в Хмельницькій області. Адреса передається безпосередньо Хмельницькобленерго для пошуку.")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Підказка для клавіатури", style = MaterialTheme.typography.titleSmall)
            Text("Вводьте назву українською або латиницею, наприклад Хмельницький / Khmelnytskyi чи Шевченка / Shevchenka. Почніть вводити місто або вулицю — з’являться підказки.")
        }
    }

    AddressTextField(
        value = town,
        onValueChange = {
            town = it
            townSuggestionsOpen = true
            requestAddressOptions("town", it)
            street = ""
            house = ""
            streetOptions = emptyList()
            houseOptions = emptyList()
        },
        onFocusChanged = { townSuggestionsOpen = it },
        label = "Населений пункт",
        placeholder = "Наприклад: Хмельницький",
        imeAction = EditorInfo.IME_ACTION_NEXT
    )
    AddressSuggestions(townOptions, town, townSuggestionsOpen) { selected ->
        town = selected.text
        townSuggestionsOpen = false
        street = ""
        house = ""
        streetSuggestionsOpen = false
        houseSuggestionsOpen = false
        streetOptions = emptyList()
        houseOptions = emptyList()
        chooseAddressOption(0, selected.id, selected.text, "street")
    }
    AddressTextField(
        value = street,
        onValueChange = {
            street = it
            streetSuggestionsOpen = true
            requestAddressOptions("street", it)
            house = ""
            houseOptions = emptyList()
        },
        onFocusChanged = { streetSuggestionsOpen = it },
        label = "Вулиця",
        placeholder = "Наприклад: Шевченка",
        imeAction = EditorInfo.IME_ACTION_NEXT
    )
    AddressSuggestions(streetOptions, street, streetSuggestionsOpen) { selected ->
        street = selected.text
        streetSuggestionsOpen = false
        house = ""
        houseOptions = emptyList()
        chooseAddressOption(1, selected.id, selected.text, "house")
    }
    AddressTextField(
        value = house,
        onValueChange = {
            house = it
            houseSuggestionsOpen = true
        },
        onFocusChanged = { houseSuggestionsOpen = it },
        label = "Номер будинку",
        placeholder = "Наприклад: 10А",
        imeAction = EditorInfo.IME_ACTION_DONE
    )
    AddressSuggestions(houseOptions, house, houseSuggestionsOpen) { selected ->
        house = selected.text
        houseSuggestionsOpen = false
        chooseAddressOption(2, selected.id, selected.text, null)
    }

    Button(
        onClick = {
            if (town.isBlank() || street.isBlank() || house.isBlank()) {
                lookupMessage = "Заповніть населений пункт, вулицю та номер будинку."
            } else if (town.count { it.isLetter() } < 2 || street.count { it.isLetter() } < 2) {
                lookupMessage = "Населений пункт і вулицю введіть буквами. Можна латиницею, наприклад Khmelnytskyi, Shevchenka."
            } else {
                vm.repo.homeTown = town.trim()
                vm.repo.homeStreet = street.trim()
                vm.repo.homeHouse = house.trim()
                foundQueues = emptyList()
                lookupMessage = "Шукаю адресу в Хмельницькобленерго…"
                val addressJson = JSONObject()
                    .put("town", town.trim())
                    .put("street", street.trim())
                    .put("house", house.trim())
                    .toString()
                val script = """
                    (async function() {
                        const address = $addressJson;
                        const bridge = window.SvitloQueueBridge;
                        const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
                        const norm = value => (value || "").toLocaleLowerCase("uk").replace(/\s+/g, " ").trim();
                        const latinize = value => norm(value).replace(/[абвгґдеєжзиіїйклмнопрстуфхцчшщьюя]/g, char => ({
                            "а":"a", "б":"b", "в":"v", "г":"h", "ґ":"g", "д":"d", "е":"e", "є":"ye",
                            "ж":"zh", "з":"z", "и":"y", "і":"i", "ї":"yi", "й":"i", "к":"k", "л":"l",
                            "м":"m", "н":"n", "о":"o", "п":"p", "р":"r", "с":"s", "т":"t", "у":"u",
                            "ф":"f", "х":"kh", "ц":"ts", "ч":"ch", "ш":"sh", "щ":"shch", "ь":"",
                            "ю":"yu", "я":"ya"
                        })[char]);
                        const compact = value => norm(value).replace(/[^0-9a-zа-яіїєґ]/g, "");
                        const forms = value => [...new Set([compact(value), compact(latinize(value))].filter(form => form.length > 1))];
                        const findOption = (select, value) => Array.from(select.options).find(option => {
                            const optionForms = [
                                ...forms(option.text),
                                ...forms(option.value)
                            ];
                            const queryForms = forms(value);
                            return optionForms.some(optionForm =>
                                queryForms.some(queryForm => optionForm.includes(queryForm))
                            );
                        });
                        const findHouseOption = (select, value) => {
                            const exactForms = [compact(value), compact(latinize(value))].filter(Boolean);
                            return Array.from(select.options).find(option => {
                                const optionForms = [compact(option.text), compact(latinize(option.text)),
                                    compact(option.value), compact(latinize(option.value))].filter(Boolean);
                                return optionForms.some(form => exactForms.includes(form));
                            });
                        };
                        const selects = () => Array.from(document.querySelectorAll("select"));
                        const ajax = (url, term) => new Promise(resolve => {
                            window.jQuery.ajax({
                                url: url,
                                type: "GET",
                                dataType: "json",
                                data: { term: term, q: term, _type: "query", page: 1 },
                                success: data => resolve(data || []),
                                error: () => resolve([])
                            });
                        });
                        const remoteOption = async (index, value, select) => {
                            const wrapper = select.parentElement;
                            let url = wrapper && wrapper.getAttribute("data-urlaction");
                            if (!url) return null;
                            let term = value.replace(/\s*\([^)]*\)\s*/g, " ").trim();
                            if (index === 0) {
                                term = term.replace(/^(м\.|с\.)\s*/i, "").trim();
                                url += "/";
                            } else {
                                const townId = selects()[0] && selects()[0].value;
                                if (!townId) return null;
                                url += "/" + encodeURIComponent(townId);
                                term = term.replace(/^(вул\.|вулиця|пров\.|провулок|просп\.|проспект)\s*/i, "").trim();
                            }
                            const queryForms = forms(term);
                            const records = await ajax(url, term);
                            const record = records.find(item =>
                                forms(item.text).some(optionForm =>
                                    queryForms.some(queryForm => optionForm.includes(queryForm))
                                )
                            );
                            return record || null;
                        };
                        const choose = async (index, value, label) => {
                            const select = selects()[index];
                            if (!select) throw new Error("Список " + label + " недоступний.");
                            let option = index === 2 ? findHouseOption(select, value) : findOption(select, value);
                            if (!option && index < 2) {
                                const record = await remoteOption(index, value, select);
                                if (record) {
                                    option = new Option(record.text, record.id, true, true);
                                    select.add(option);
                                }
                            }
                            if (option) {
                                window.jQuery(select).val(option.value).trigger("change");
                                await wait(index === 0 ? 350 : 700);
                                return;
                            }
                            if (index === 2) {
                                for (let attempt = 0; attempt < 60; attempt++) {
                                    option = findHouseOption(select, value);
                                    if (option) {
                                        window.jQuery(select).val(option.value).trigger("change");
                                        await wait(500);
                                        return;
                                    }
                                    await wait(250);
                                }
                            }
                            const available = Array.from(select.options)
                                .map(item => item.text.trim()).filter(Boolean).slice(0, 12).join(", ");
                            throw new Error("Не знайдено " + label + ": " + value +
                                (available ? ". Варіанти сайту: " + available : ""));
                        };
                        try {
                            await choose(0, address.town, "населений пункт");
                            await choose(1, address.street, "вулицю");
                            const beforeHouse = document.body.innerText || document.body.textContent || "";
                            await choose(2, address.house, "будинок");
                            let queueMatches = [];
                            let resultText = "";
                            for (let attempt = 0; attempt < 24; attempt++) {
                                await wait(500);
                                const afterText = document.body.innerText || document.body.textContent || "";
                                resultText = afterText;
                                const matches = resultText.match(/[1-6][.,][12]/g) || [];
                                queueMatches = Array.from(new Set(matches.map(value => value.replace(",", "."))));
                                if (queueMatches.length) {
                                    bridge.reportQueues(queueMatches.join(","));
                                    return;
                                }
                            }
                            bridge.reportError(
                                resultText
                                    ? "Сторінка черг не повернула номер для цієї адреси. Перевірте вибраний будинок на сайті Хмельницькобленерго."
                                    : "Адресу вибрано, але сторінка черг не показала результат. Спробуйте ще раз."
                            );
                        } catch (error) {
                            bridge.reportError(String(error && error.message || error));
                        }
                    })();
                """.trimIndent()
                pendingLookupScript = script
                webView?.reload()
            }
        },
        enabled = webViewReady,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Визначити мою чергу")
    }

    if (lookupMessage.isNotBlank()) Text(lookupMessage, style = MaterialTheme.typography.bodyMedium)

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Показувати вибір черг на головній")
            Switch(
                checked = showQueueSelector,
                onCheckedChange = {
                    showQueueSelector = it
                    vm.repo.showQueueSelector = it
                }
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Сповіщати про відключення та ввімкнення")
                Text("За 1 годину до зміни графіка, зі звуком", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = vm.notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        vm.updateNotificationsEnabled(false)
                    } else if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.updateNotificationsEnabled(true)
                    }
                }
            )
        }
    }
    OutlinedButton(
        onClick = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                testNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                PowerNotifications.testSound(context)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Перевірити звук сповіщення")
    }

    Text("Оновлення", style = MaterialTheme.typography.titleLarge)
    Text("Версія ${vm.installedVersionName} (${vm.installedVersionCode})", style = MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        OutlinedButton(
            onClick = { vm.checkUpdate(force = true) },
            modifier = Modifier.fillMaxWidth(0.86f).height(60.dp)
        ) {
            Text("Перевірити оновлення", fontSize = 18.sp, textAlign = TextAlign.Center)
        }
    }
    if (vm.updMsg.isNotBlank()) Text(vm.updMsg, style = MaterialTheme.typography.bodySmall)

    if (foundQueues.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (foundQueues.size == 1) "Ваша черга: ${foundQueues.single()}" else "Для будинку знайдено кілька черг",
                    style = MaterialTheme.typography.titleLarge,
                    color = GREEN
                )
                if (foundQueues.size > 1) {
                    Text("Будівля може мати кілька приєднань. Виберіть своє:")
                    foundQueues.chunked(3).forEach { rowQueues ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowQueues.forEach { queue ->
                                FilterChip(
                                    selected = vm.queue == queue,
                                    onClick = { vm.queue = queue; vm.repo.queue = queue },
                                    label = { Text(queue) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.javaScriptEnabled = true
                addJavascriptInterface(object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun reportQueues(value: String) {
                        mainHandler.post {
                            val queues = value.split(',').asSequence()
                                .map(String::trim)
                                .filter { it in QUEUES }
                                .distinct()
                                .toList()
                            foundQueues = queues
                            if (queues.size == 1) {
                                vm.queue = queues.single()
                                vm.repo.queue = queues.single()
                                lookupMessage = "Чергу збережено. На головній сторінці показано графік для неї."
                            } else if (queues.isNotEmpty()) {
                                lookupMessage = "Знайдено кілька черг. Виберіть свою."
                            }
                        }
                    }

                    @Suppress("unused")
                    @JavascriptInterface
                    fun reportError(message: String) {
                        mainHandler.post { lookupMessage = message }
                    }

                    @Suppress("unused")
                    @JavascriptInterface
                    fun reportOptions(kind: String, json: String) {
                        val options = runCatching {
                            val array = JSONArray(json)
                            List(array.length()) { index ->
                                when (val item = array.opt(index)) {
                                    is JSONObject -> AddressOption(
                                        id = item.optString("id"),
                                        text = item.optString("text").replace(Regex("\\s+"), " ").trim()
                                    )
                                    else -> item?.toString().orEmpty().let { AddressOption(it, it) }
                                }
                            }.filter { it.text.isNotBlank() }.distinctBy(AddressOption::id)
                        }.getOrDefault(emptyList())
                        mainHandler.post {
                            when (kind) {
                                "town" -> townOptions = options
                                "street" -> streetOptions = options
                                "house" -> houseOptions = options
                            }
                        }
                    }
                }, "SvitloQueueBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host !in setOf("hoe.com.ua", "www.hoe.com.ua")

                    override fun onPageFinished(view: WebView, url: String) {
                        if (url.startsWith("https://hoe.com.ua/shutdown/queue")) {
                            mainHandler.post {
                                webViewReady = true
                                pendingLookupScript?.let { script ->
                                    pendingLookupScript = null
                                    view.evaluateJavascript(script, null)
                                }
                            }
                        }
                    }
                }
                loadUrl("https://hoe.com.ua/shutdown/queue")
            }
        }
    )
}
