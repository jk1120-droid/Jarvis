package com.jk1120.jarvis

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ChatMessage(val text: String, val fromUser: Boolean)
private enum class TutorMode(val title: String) { TEACHER("Učitel"), CODING("Programování"), FILM("Filmařina") }

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        DailyDigestNotifications.createChannel(this)
        DailyDigestNotifications.schedule(this)
        textToSpeech = TextToSpeech(this, this)
        setContent { JarvisApp(::startListening, ::speak) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("cs", "CZ"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun speak(text: String) {
        if (ttsReady) textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
    }

    private fun startListening(onText: (String) -> Unit, onError: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            onError("Pro soukromé hlasové zadávání je potřeba Android 12+ a nainstalovaný offline český hlasový balíček. Zatím můžeš psát.")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onError(error: Int) { onError("Offline řeč se nepodařilo rozpoznat. Zkontroluj český hlasový balíček a zkus to znovu.") }
                override fun onResults(results: android.os.Bundle?) {
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (phrase.isNullOrBlank()) onError("Nerozuměl jsem. Zkus to prosím znovu.") else onText(phrase)
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Mluv česky…")
            }
            recognizer.startListening(intent)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun JarvisApp(startListening: ((String) -> Unit, (String) -> Unit) -> Unit, speak: (String) -> Unit) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(LocalData.loadChat(context)) } }
    if (messages.isEmpty()) messages.add(ChatMessage("Ahoj! Jsem Jarvis. Tvoje chaty a vzpomínky zůstávají pouze v telefonu.", false))
    val memories = remember { mutableStateListOf<String>().apply { addAll(LocalData.loadMemories(context)) } }
    val memoryInput = remember { mutableStateOf("") }
    val input = remember { mutableStateOf("") }
    val voiceMode = remember { mutableStateOf(false) }
    val mode = remember { mutableStateOf(TutorMode.TEACHER) }
    val status = remember { mutableStateOf("Soukromý offline režim · žádné online AI") }
    val clock = remember { mutableStateOf(currentDateTime()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status.value = if (granted) "Oznámení povolena. Povol také Jarvisovi přístup k oznámením níže." else "Povol oznámení Jarvise, aby mohl ukázat přehled."
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening({ input.value = it; status.value = "Rozpoznáno offline" }, { status.value = it })
        else status.value = "Pro hlasový vstup povol přístup k mikrofonu."
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock.value = currentDateTime()
            delay(30_000)
        }
    }

    fun saveChat() = LocalData.saveChat(context, messages.toList())

    fun addMemory() {
        val fact = memoryInput.value.trim()
        if (fact.isBlank()) return
        if (memories.size >= 50) memories.removeAt(0)
        memories.add(fact.take(300))
        LocalData.saveMemories(context, memories.toList())
        memoryInput.value = ""
        status.value = "Uloženo jako soukromá vzpomínka v telefonu"
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty()) return
        messages.add(ChatMessage(question, true))
        input.value = ""
        val answer = LocalTutor.answer(question, mode.value, memories.toList())
        messages.add(ChatMessage(answer, false))
        while (messages.size > 100) messages.removeAt(0)
        saveChat()
        status.value = "Odpověď vytvořena lokálně v telefonu"
        if (voiceMode.value) speak(answer)
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    fun addDailySummary() {
        val summary = NotificationStore.summaryForToday(context)
        messages.add(ChatMessage(summary, false))
        saveChat()
        if (voiceMode.value) speak(summary)
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F6FA)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Jarvis", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Soukromý offline pomocník · český učitel", color = Color(0xFF526176))
                Text(clock.value, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                Text(status.value, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))

                Text("Režim", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    TutorMode.values().forEach { option ->
                        TextButton(onClick = { mode.value = option }) { Text(if (mode.value == option) "✓ ${option.title}" else option.title) }
                    }
                }

                Text("Soukromá paměť (${memories.size})", fontWeight = FontWeight.SemiBold)
                Text("Přidej věci, které si má Jarvis zapamatovat. Ukládají se jen v tomto zařízení.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = memoryInput.value,
                        onValueChange = { memoryInput.value = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Např. Učím se filmovou tvorbu") },
                        singleLine = true
                    )
                    Button(onClick = { addMemory() }) { Text("Uložit") }
                }
                if (memories.isNotEmpty()) {
                    Text(memories.takeLast(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    TextButton(onClick = {
                        memories.clear()
                        LocalData.saveMemories(context, emptyList())
                        status.value = "Soukromá paměť byla vymazána"
                    }) { Text("Zapomenout vše") }
                }

                Text("Oznámení", fontWeight = FontWeight.SemiBold)
                Text("Denní přehled se zobrazí přibližně v 18:00.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            DailyDigestNotifications.createChannel(context)
                            DailyDigestNotifications.schedule(context)
                            status.value = "Denní přehled naplánován přibližně na 18:00"
                        }
                    }) { Text("Zapnout přehled") }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Povolit přístup") }
                    TextButton(onClick = { addDailySummary() }) { Text("Dnešní přehled") }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Hlas odpovědí", modifier = Modifier.weight(1f))
                    TextButton(onClick = { voiceMode.value = !voiceMode.value }) { Text(if (voiceMode.value) "Zapnuto" else "Vypnuto") }
                    TextButton(onClick = {
                        messages.clear()
                        messages.add(ChatMessage("Nový chat. Co se chceš naučit?", false))
                        saveChat()
                    }) { Text("Nový chat") }
                }

                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages) { message ->
                        Surface(
                            color = if (message.fromUser) Color(0xFFDCEBFF) else Color.White,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(if (message.fromUser) 0.88f else 1f)
                        ) { Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = input.value,
                            onValueChange = { input.value = it },
                            modifier = Modifier.padding(14.dp),
                            singleLine = false,
                            decorationBox = { inner ->
                                if (input.value.isEmpty()) Text("Napiš otázku…", color = Color.Gray)
                                inner()
                            }
                        )
                    }
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startListening({ input.value = it; status.value = "Rozpoznáno offline" }, { status.value = it })
                        } else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("🎙") }
                    Button(onClick = { send(input.value) }) { Text("Odeslat") }
                }
            }
        }
    }
}

private fun currentDateTime(): String = SimpleDateFormat("EEEE d. MMMM yyyy · HH:mm", Locale("cs", "CZ")).format(Date())

private object LocalData {
    private const val PREFS = "jarvis_local_data"
    private const val CHAT = "chat_history"
    private const val MEMORIES = "private_memories"

    fun loadChat(context: Context): List<ChatMessage> = runCatching {
        val array = org.json.JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CHAT, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            ChatMessage(item.optString("text"), item.optBoolean("user"))
        }
    }.getOrDefault(emptyList()).takeLast(100)

    fun saveChat(context: Context, messages: List<ChatMessage>) {
        val array = org.json.JSONArray()
        messages.takeLast(100).forEach { array.put(org.json.JSONObject().put("text", it.text).put("user", it.fromUser)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CHAT, array.toString()).apply()
    }

    fun loadMemories(context: Context): List<String> = runCatching {
        val array = org.json.JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MEMORIES, "[]"))
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList()).takeLast(50)

    fun saveMemories(context: Context, memories: List<String>) {
        val array = org.json.JSONArray()
        memories.takeLast(50).forEach(array::put)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MEMORIES, array.toString()).apply()
    }
}

private object LocalTutor {
    fun answer(raw: String, mode: TutorMode, memories: List<String>): String {
        val q = raw.lowercase(Locale.ROOT)
        if (listOf("kolik je hodin", "jaký je čas", "aktuální čas", "kolikátého je", "jaké je datum", "dnešní datum", "jaký je dnes den").any(q::contains)) {
            return "Teď je ${currentDateTime()} podle času a data nastaveného v telefonu."
        }
        if (listOf("co si pamatuješ", "co si o mně pamatuješ", "jaké máš vzpomínky").any(q::contains)) {
            return if (memories.isEmpty()) "Zatím nemám žádné uložené vzpomínky. Můžeš mi je přidat v části Soukromá paměť." else "Tohle si o tobě pamatuji (uloženo jen v telefonu):\n" + memories.joinToString("\n") { "• $it" }
        }
        if (q.startsWith("zapamatuj si ")) return "Tuto větu ulož do části Soukromá paměť nahoře, abys mohl zkontrolovat, co si Jarvis pamatuje."
        val filmTerms = listOf("film", "kamera", "střih", "scénář", "režie", "osvětlení", "záběr", "filmař")
        if (mode == TutorMode.FILM || filmTerms.any(q::contains)) {
            return when {
                listOf("scénář", "příběh", "nápad").any(q::contains) -> "Začni jednovětým námětem: kdo něco chce, co mu v tom brání a co riskuje? Pak načrtni začátek, zlom uprostřed a konec. U každé scény si napiš její cíl, místo a změnu. Jaký film chceš natočit?"
                listOf("kamera", "záběr", "kompozic").any(q::contains) -> "Při plánování záběru zvaž velikost (celek/polodetail/detail), úhel kamery, pohyb a to, kam se dívá divák. Natoč nejdřív široký záběr pro orientaci a potom detaily, které příběh posunou."
                listOf("střih", "edit").any(q::contains) -> "Při střihu nejdřív vyber nejlepší záběry, potom sestav jasný příběh a až nakonec dolaď rytmus, zvuk a barvy. Stříhej podle významu a návaznosti pohybu."
                listOf("zvuk", "mikrofon", "dialog").any(q::contains) -> "Čistý zvuk je zásadní: nahraj dialog co nejblíž mikrofonem, hlídej rušivé zvuky a pořiď i několik sekund zvuku prostředí. Sluchátky zkontroluj šum a hlasitost."
                listOf("svět", "osvětlení").any(q::contains) -> "Začni dostupným světlem: hlavní světlo určuje tvář nebo objekt, odrazka může zjemnit stíny a protisvětlo oddělí postavu od pozadí. Před natáčením zkontroluj barvu světel a expozici."
                else -> "Rád pomůžu s filmovou tvorbou: námět, scénář, storyboard, kompozice, kamera, světlo, zvuk, střih i plán natáčení. Napiš, na čem právě pracuješ."
            }
        }
        if (mode == TutorMode.CODING || listOf("python", "kotlin", "program", "kód", "code", "java", "javascript", "chyba").any(q::contains)) {
            return "Programovací režim: rozděl problém na malé kroky, urči vstup a výstup a nejdřív napiš jednoduché řešení. Pak ho otestuj i s prázdným nebo neobvyklým vstupem. Pošli jazyk, kód a chybovou zprávu a projdeme to spolu."
        }
        val base = when {
            listOf("ahoj", "čau", "dobrý den").any(q::contains) -> "Ahoj! Co se chceš dnes naučit? Můžeme probrat školní látku, AI, programování nebo filmovou tvorbu."
            listOf("matemat", "rovnic", "zlom", "číslo").any(q::contains) -> "Pojďme na to krok za krokem. Napiš celé zadání; nejdřív určíme, co známe a co máme vypočítat, potom ověříme výsledek."
            listOf("umělá inteligence", " ai ", "model", "neuron", "strojové učení").any { q.contains(it.trim()) } -> "Umělá inteligence je oblast informatiky, která vytváří systémy řešící úlohy obvykle vyžadující lidské schopnosti. Strojové učení se učí vzory z příkladů. Chceš jednoduchý příklad?"
            else -> "Jsem soukromý offline prototyp. Odpovídám místními výukovými tipy, ne plnohodnotným jazykovým modelem. Napiš téma nebo otázku a zkusím tě navést."
        }
        return if (memories.isEmpty()) base else "$base\n\nMám uložené soukromé poznámky, které můžeš kdykoli zobrazit nebo smazat v části Soukromá paměť."
    }
}

private data class SavedNotification(val key: String, val app: String, val title: String, val text: String, val time: Long)

private object NotificationStore {
    private const val PREFS = "jarvis_local_data"
    private const val ITEMS = "captured_notifications"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    @Synchronized fun save(context: Context, item: SavedNotification) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = runCatching { org.json.JSONArray(prefs.getString(ITEMS, "[]")) }.getOrElse { org.json.JSONArray() }
        val next = org.json.JSONArray()
        for (i in 0 until previous.length()) {
            val existing = previous.optJSONObject(i) ?: continue
            if (existing.optString("key") != item.key) next.put(existing)
        }
        next.put(org.json.JSONObject().put("key", item.key).put("app", item.app).put("title", item.title).put("text", item.text).put("time", item.time))
        val trimmed = org.json.JSONArray()
        val start = (next.length() - 200).coerceAtLeast(0)
        for (i in start until next.length()) trimmed.put(next.getJSONObject(i))
        prefs.edit().putString(ITEMS, trimmed.toString()).apply()
    }

    fun summaryForToday(context: Context): String {
        val saved = runCatching { org.json.JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ITEMS, "[]")) }.getOrElse { org.json.JSONArray() }
        val today = dateFormat.format(Date())
        val items = (0 until saved.length()).mapNotNull { i ->
            val obj = saved.optJSONObject(i) ?: return@mapNotNull null
            val time = obj.optLong("time")
            if (dateFormat.format(Date(time)) != today) return@mapNotNull null
            SavedNotification(obj.optString("key"), obj.optString("app"), obj.optString("title"), obj.optString("text"), time)
        }.sortedByDescending { it.time }
        if (items.isEmpty()) return "Dnešní přehled: Jarvis dnes nezachytil žádná oznámení. Zkontroluj, zda má přístup k oznámením."
        val lines = items.take(12).mapIndexed { index, item ->
            val content = listOf(item.title, item.text).filter(String::isNotBlank).distinct().joinToString(" — ").take(260)
            "${index + 1}. ${item.app}: ${content.ifBlank { "nové oznámení" }}"
        }
        return "Dnešní přehled (${items.size} oznámení, nejnovějších ${lines.size}):\n" + lines.joinToString("\n")
    }
}

class NotificationCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty().take(180)
        val text = (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text"))?.toString()?.trim().orEmpty().take(500)
        if (title.isBlank() && text.isBlank()) return
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        NotificationStore.save(this, SavedNotification(sbn.key, app.take(100), title, text, sbn.postTime))
    }
}

class DailyDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) { DailyDigestNotifications.show(context) }
}

class RescheduleDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) DailyDigestNotifications.schedule(context)
    }
}

private object DailyDigestNotifications {
    private const val CHANNEL_ID = "jarvis_daily_digest"
    private const val REQUEST_CODE = 2026

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Jarvis – denní přehled", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Soukromý přehled oznámení uložených pouze v zařízení"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent(context))
    }

    fun show(context: Context) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val summary = NotificationStore.summaryForToday(context)
        val openApp = PendingIntent.getActivity(
            context, REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jarvis – denní přehled")
            .setContentText(summary.lineSequence().drop(1).firstOrNull()?.take(120) ?: "Ťukni pro dnešní přehled oznámení.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(REQUEST_CODE, notification)
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, DailyDigestReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
