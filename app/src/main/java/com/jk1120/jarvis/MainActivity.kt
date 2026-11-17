package com.jk1120.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("V tomto zařízení není dostupné rozpoznávání řeči.")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onError(error: Int) { onError("Řeč se nepodařilo rozpoznat. Zkus to prosím znovu.") }
                override fun onResults(results: Bundle?) {
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
    val messages = remember {
        mutableStateListOf(ChatMessage("Ahoj! Jsem Jarvis, tvůj studijní pomocník. Můžeš mě používat offline, nebo připojit online AI.", false))
    }
    val input = remember { mutableStateOf("") }
    val voiceMode = remember { mutableStateOf(false) }
    val onlineMode = remember { mutableStateOf(false) }
    val endpoint = remember { mutableStateOf("https://api.openai.com/v1/chat/completions") }
    val model = remember { mutableStateOf("gpt-4o-mini") }
    val apiKey = remember { mutableStateOf("") }
    val loading = remember { mutableStateOf(false) }
    val status = remember { mutableStateOf("Offline režim · odpovědi se tvoří přímo v telefonu") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening({ input.value = it; status.value = "Rozpoznáno" }, { status.value = it })
        else status.value = "Pro hlasový vstup povol přístup k mikrofonu."
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || loading.value) return
        input.value = ""
        messages.add(ChatMessage(question, true))
        if (onlineMode.value) {
            val key = apiKey.value.trim()
            if (key.isEmpty()) {
                messages.add(ChatMessage("Pro online režim nejdřív zadej API klíč. Klíč se ukládá jen v paměti aplikace a po jejím zavření se smaže.", false))
                status.value = "Chybí API klíč"
            } else {
                loading.value = true
                status.value = "Online AI přemýšlí…"
                val conversation = messages.toList()
                scope.launch {
                    val result = runCatching {
                        OnlineTutor.reply(conversation, endpoint.value.trim(), model.value.trim(), key)
                    }
                    val answer = result.getOrElse { "Online požadavek se nezdařil: ${it.message ?: "zkontroluj připojení a nastavení"}" }
                    messages.add(ChatMessage(answer, false))
                    loading.value = false
                    status.value = if (result.isSuccess) "Odpověď od online AI" else "Online chyba · offline režim je stále dostupný"
                    if (voiceMode.value) speak(answer)
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
        } else {
            val answer = LocalTutor.answer(question)
            messages.add(ChatMessage(answer, false))
            status.value = "Odpověď vytvořena offline v telefonu"
            if (voiceMode.value) speak(answer)
            scope.launch { listState.animateScrollToItem(messages.lastIndex) }
        }
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F6FA)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Jarvis", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Učitel · čeština · offline i online", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF526176))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Režim: ${if (onlineMode.value) "online AI" else "offline"}", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onlineMode.value = !onlineMode.value
                        status.value = if (onlineMode.value) "Online režim · vyžaduje internet a vlastní API klíč" else "Offline režim · odpovědi se tvoří přímo v telefonu"
                    }) { Text(if (onlineMode.value) "Přepnout offline" else "Zapnout online") }
                }
                if (onlineMode.value) {
                    OutlinedTextField(
                        value = endpoint.value,
                        onValueChange = { endpoint.value = it },
                        label = { Text("API endpoint (kompatibilní s OpenAI)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = model.value, onValueChange = { model.value = it }, label = { Text("Model") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(
                            value = apiKey.value,
                            onValueChange = { apiKey.value = it },
                            label = { Text("API klíč") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                    Text("Online zprávy se odešlou poskytovateli API. Zadej jen důvěryhodný HTTPS endpoint. Klíč se neukládá na disk.", style = MaterialTheme.typography.labelSmall)
                }
                Text(status.value, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        Surface(
                            color = if (message.fromUser) Color(0xFFDCEBFF) else Color.White,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(if (message.fromUser) 0.88f else 1f)
                        ) {
                            Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text("Hlas odpovědí", modifier = Modifier.weight(1f))
                    TextButton(onClick = { voiceMode.value = !voiceMode.value }) { Text(if (voiceMode.value) "Zapnuto" else "Vypnuto") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            startListening({ input.value = it; status.value = "Rozpoznáno" }, { status.value = it })
                        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("🎙") }
                    Button(onClick = { send(input.value) }, enabled = !loading.value) { Text(if (loading.value) "…" else "Odeslat") }
                }
            }
        }
    }
}

private object OnlineTutor {
    suspend fun reply(messages: List<ChatMessage>, endpoint: String, model: String, apiKey: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(endpoint.startsWith("https://", ignoreCase = true)) { "Endpoint musí používat HTTPS." }
            require(model.isNotBlank()) { "Zadej název modelu." }
            val url = java.net.URL(endpoint)
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val apiMessages = org.json.JSONArray().apply {
                    put(org.json.JSONObject().put("role", "system").put("content", "Jsi Jarvis, přátelský a trpělivý učitel pro školu, umělou inteligenci a programování. Odpovídej česky, pokud uživatel nepíše jiným jazykem. Vysvětluj krok za krokem a podporuj učení."))
                    messages.drop(1).forEach { message ->
                        put(org.json.JSONObject().put("role", if (message.fromUser) "user" else "assistant").put("content", message.text))
                    }
                }
                val body = org.json.JSONObject().put("model", model).put("messages", apiMessages).toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val statusCode = connection.responseCode
                val responseStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (statusCode !in 200..299) {
                    val detail = runCatching { org.json.JSONObject(responseBody).optJSONObject("error")?.optString("message") }.getOrNull()
                    throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "API odpovědělo HTTP $statusCode")
                }
                val answer = org.json.JSONObject(responseBody)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content")
                answer.takeIf { it.isNotBlank() } ?: throw IllegalStateException("API vrátilo prázdnou odpověď.")
            } finally {
                connection.disconnect()
            }
        }
}

private object LocalTutor {
    fun answer(raw: String): String {
        val q = raw.lowercase(Locale.ROOT)
        return when {
            listOf("ahoj", "čau", "dobrý den").any(q::contains) ->
                "Ahoj! Co se chceš dnes naučit? Můžeme probrat školní látku, umělou inteligenci nebo programování."
            listOf("python", "kotlin", "program", "kód", "code", "java", "javascript").any(q::contains) ->
                "Při programování si úlohu rozděl na malé kroky: 1) popiš vstup a výstup, 2) napiš jednoduché řešení, 3) vyzkoušej okrajové případy. Pošli mi konkrétní kód nebo chybu a zkusím ti vysvětlit další krok."
            listOf("umělá inteligence", " ai ", "model", "neuron", "strojové učení").any { q.contains(it.trim()) } ->
                "Umělá inteligence je oblast informatiky, která vytváří systémy řešící úlohy, jež obvykle vyžadují lidské schopnosti. Strojové učení je její část: model hledá vzory v příkladech. Chceš jednoduchý příklad?"
            listOf("matemat", "rovnic", "zlom", "číslo").any(q::contains) ->
                "Pojďme na to krok za krokem. Napiš celé zadání a ukážu postup i kontrolu výsledku. Zkus nejdřív určit, co znáš a co máš vypočítat."
            listOf("škola", "učit", "vysvětli", "co je", "jak funguje").any(q::contains) ->
                "Rád ti to vysvětlím jednoduše a postupně. Napiš téma nebo přesné zadání; můžu přidat příklad a krátkou otázku na ověření, že tomu rozumíš."
            else ->
                "Jsem jednoduchý offline studijní pomocník. Pro volné odpovědi přepni na online AI; offline režim funguje i bez Wi-Fi."
        }
    }
}
