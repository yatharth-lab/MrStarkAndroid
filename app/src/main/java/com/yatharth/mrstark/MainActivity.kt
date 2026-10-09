
package com.yatharth.mrstark

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF06111F)
private val Panel = Color(0xFF10243A)
private val Cyan = Color(0xFF43D9FF)
private val Muted = Color(0xFF9CB7CA)
private val UserPanel = Color(0xFF123A50)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    background = Navy,
                    surface = Panel,
                    onPrimary = Navy,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                MrStarkScreen(
                    speak = { text ->
                        tts?.speak(
                            text.take(3500),
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "mrstark_reply"
                        )
                    }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

data class ChatLine(val who: String, val text: String)

@Composable
private fun MrStarkScreen(speak: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("mrstark_prefs", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember {
        mutableStateListOf<ChatLine>()
    }

    var input by remember { mutableStateOf("") }
    var apiKey by remember {
        mutableStateOf(prefs.getString("gemini_api_key", "") ?: "")
    }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("SYSTEM ONLINE • ADVANCED MODE") }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognition(context) { recognized ->
                input = recognized
            }
        } else {
            Toast.makeText(
                context,
                "Voice के लिए microphone permission जरूरी है।",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("chat_memory", null)
        var loaded = false

        if (!saved.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(saved)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val who = obj.optString("who")
                    val text = obj.optString("text")
                    if (who.isNotBlank() && text.isNotBlank()) {
                        messages.add(ChatLine(who, text))
                        loaded = true
                    }
                }
            }
        }

        if (!loaded) {
            messages.add(
                ChatLine(
                    "MR STARK",
                    "नमस्ते! मैं Mr Stark हूँ। लिखकर या माइक्रोफोन से बात करो। Settings में Gemini API key जोड़कर AI चालू कर सकते हो।"
                )
            )
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun addMessage(who: String, text: String) {
        messages.add(ChatLine(who, text))
        while (messages.size > 60) {
            messages.removeAt(0)
        }
        saveMemory(prefs, messages)
    }

    fun sendMessage() {
        val prompt = input.trim()
        if (prompt.isBlank() || busy) return

        input = ""
        addMessage("YOU", prompt)
        busy = true
        status = "ANALYSING REQUEST..."

        scope.launch {
            try {
                val quickReply = runQuickCommand(context, prompt)

                val reply = when {
                    quickReply != null -> quickReply
                    apiKey.isBlank() -> localReply(prompt)
                    else -> callGemini(
                        apiKey = apiKey.trim(),
                        prompt = prompt,
                        history = messages.toList()
                    )
                }

                addMessage("MR STARK", reply)

                status = when {
                    quickReply != null -> "TASK PROCESSED"
                    apiKey.isBlank() -> "LOCAL MODE • ADD API KEY"
                    reply.startsWith("AI connection failed") -> "AI CONNECTION ISSUE"
                    else -> "SYSTEM ONLINE"
                }

                if (speakReplies) speak(reply)
            } catch (e: Exception) {
                val error = "कुछ गड़बड़ हो गई। दोबारा कोशिश करो।"
                addMessage("MR STARK", error)
                status = "RECOVERABLE ERROR"
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071525), Navy, Color(0xFF02060C))
                )
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "MR STARK",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                        color = Cyan
                    )
                    Text(status, fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
                }

                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Cyan
                    )
                }
            }

            if (showSettings) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("AI SETTINGS", color = Cyan, fontWeight = FontWeight.Bold)
                        Text(
                            "Gemini API key",
                            color = Muted,
                            fontSize = 12.sp
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API key") },
                            singleLine = true
                        )

                        Text(
                            "Key केवल इस डिवाइस की app preferences में सेव होगी। GitHub पर key न डालें। यह storage सार्वजनिक ऐप के लिए पूरी तरह सुरक्षित नहीं है।",
                            color = Muted,
                            fontSize = 11.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = {
                                prefs.edit()
                                    .putString("gemini_api_key", apiKey.trim())
                                    .apply()
                                apiKey = apiKey.trim()
                                showSettings = false
                                status = if (apiKey.isBlank()) {
                                    "LOCAL MODE • API KEY NOT SET"
                                } else {
                                    "API KEY SAVED"
                                }
                                Toast.makeText(
                                    context,
                                    "Settings सेव हो गईं।",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }) {
                                Text("Save")
                            }

                            Button(
                                onClick = {
                                    apiKey = ""
                                    prefs.edit()
                                        .remove("gemini_api_key")
                                        .apply()
                                    Toast.makeText(
                                        context,
                                        "API key हटाई गई।",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF713B4B)
                                )
                            ) {
                                Text("Clear key")
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = speakReplies,
                                onCheckedChange = { speakReplies = it }
                            )
                            Text("जवाब बोलकर सुनाएँ", color = Color.White)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0B2034)
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(94.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF258AB0),
                                        Color(0xFF0B2339),
                                        Navy
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "STARK",
                            color = Cyan,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        if (busy) "PROCESSING..." else "READY TO ASSIST",
                        color = Cyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "VOICE • INTELLIGENCE • QUICK ACTIONS",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CONVERSATION",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("${messages.size} messages", color = Muted, fontSize = 10.sp)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { line ->
                    val isUser = line.who == "YOU"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) UserPanel else Panel
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                line.who,
                                color = if (isUser) Cyan else Color(0xFF8CE9FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                line.text,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (busy) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Cyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Mr Stark सोच रहा है...", color = Muted)
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                IconButton(
                    onClick = {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startSpeechRecognition(context) { input = it }
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.background(UserPanel, CircleShape)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Speak", tint = Cyan)
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Stark से बात करो…") },
                    maxLines = 3
                )

                IconButton(
                    onClick = { sendMessage() },
                    enabled = !busy && input.isNotBlank(),
                    modifier = Modifier.background(
                        if (!busy && input.isNotBlank()) Cyan else Panel,
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (!busy && input.isNotBlank()) Navy else Muted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val query = input.trim().ifBlank { "आज की मुख्य खबरें" }
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, query)
                        }
                        runCatching {
                            context.startActivity(intent)
                        }.onFailure {
                            openUrl(context, "https://www.google.com/search?q=${Uri.encode(query)}")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Cyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Search")
                }

                Button(
                    onClick = {
                        if (busy) return@Button
                        messages.clear()
                        messages.add(
                            ChatLine("MR STARK", "नई बातचीत शुरू हो गई। बताओ, क्या करना है?")
                        )
                        saveMemory(prefs, messages)
                        status = "NEW SESSION"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel)
                ) {
                    Text("New chat")
                }
            }
        }
    }
}

private fun startSpeechRecognition(
    context: Context,
    onResult: (String) -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Toast.makeText(
            context,
            "इस डिवाइस पर speech recognition उपलब्ध नहीं है।",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            Toast.makeText(
                context,
                "आवाज़ समझ नहीं आई। फिर कोशिश करो।",
                Toast.LENGTH_SHORT
            ).show()
            recognizer.destroy()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()

            if (!text.isNullOrBlank()) {
                onResult(text)
            }
            recognizer.destroy()
        }
    })

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Mr Stark को अपना निर्देश बोलें")
    }

    runCatching {
        recognizer.startListening(intent)
    }.onFailure {
        recognizer.destroy()
        Toast.makeText(
            context,
            "Voice service शुरू नहीं हो सकी।",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private suspend fun callGemini(
    apiKey: String,
    prompt: String,
    history: List<ChatLine>
): String = withContext(Dispatchers.IO) {

    val models = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash"
    )

    var lastError = "Unknown API error"

    for (model in models) {
        var moveToNextModel = false

        for (attempt in 0..1) {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                )

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                }

                val contents = JSONArray()
                val recent = history.takeLast(12)

                recent.forEach { line ->
                    val role = if (line.who == "YOU") "user" else "model"
                    contents.put(
                        JSONObject()
                            .put("role", role)
                            .put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", line.text))
                            )
                    )
                }

                // अगर current prompt history में नहीं है, तभी उसे जोड़ें।
                if (recent.none { it.who == "YOU" && it.text == prompt }) {
                    contents.put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", prompt))
                            )
                    )
                }

                val systemPrompt = """
                    You are Mr Stark, a helpful personal assistant inside an Android app.
                    Reply in natural, easy Hindi by default. If the user writes in English,
                    you may reply in English. Be practical, clear and concise unless the user
                    asks for detail. Use conversation history for context. Never claim that
                    you changed a device setting, opened an app, sent a message, or performed
                    an action unless the app actually performed it. If you are unsure, say so.
                """.trimIndent()

                val body = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", systemPrompt))
                        )
                    )
                    .put("contents", contents)
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.7)
                            .put("maxOutputTokens", 1800)
                    )

                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(response) }.getOrNull()

                if (code in 200..299 && json != null) {
                    val candidates = json.optJSONArray("candidates")
                    val parts = candidates
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")

                    val answer = buildString {
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.optJSONObject(i)
                                val text = part?.optString("text", "").orEmpty()
                                if (text.isNotBlank()) {
                                    if (isNotEmpty()) append("\n")
                                    append(text)
                                }
                            }
                        }
                    }.trim()

                    if (answer.isNotBlank()) {
                        return@withContext answer
                    }

                    lastError = "$model: जवाब खाली मिला।"
                    moveToNextModel = true
                    break
                }

                val message = json
                    ?.optJSONObject("error")
                    ?.optString("message", "")
                    .orEmpty()

                lastError = "$model (HTTP $code): ${
                    message.ifBlank { "API ने अनुरोध पूरा नहीं किया।" }
                }"

                val temporary = code == 429 ||
                    code == 500 ||
                    code == 502 ||
                    code == 503 ||
                    code == 504

                if (!temporary) {
                    return@withContext "AI connection failed: $lastError"
                }

                if (attempt == 0) {
                    delay(1200)
                } else {
                    moveToNextModel = true
                }
            } catch (e: Exception) {
                lastError = "$model: ${
                    e.localizedMessage ?: "नेटवर्क कनेक्शन की समस्या"
                }"

                if (attempt == 0) {
                    delay(1000)
                } else {
                    moveToNextModel = true
                }
            } finally {
                connection?.disconnect()
            }

            if (moveToNextModel) break
        }
    }

    "AI connection failed: सभी configured models से जवाब नहीं मिला। " +
        "$lastError इंटरनेट, API key, quota और उपलब्ध model की जाँच करो।"
}

private fun runQuickCommand(context: Context, prompt: String): String? {
    val p = prompt.lowercase(Locale.ROOT)

    return when {
        p.contains("समय बताओ") ||
            p == "समय" ||
            p == "time" ||
            p.contains("अभी कितने बजे") -> {
            val time = SimpleDateFormat("hh:mm a", Locale("hi", "IN")).format(Date())
            "अभी समय है $time।"
        }

        p.contains("आज की तारीख") ||
            p.contains("आज कौन सी तारीख") ||
            p == "date" -> {
            val date = SimpleDateFormat("dd MMMM yyyy", Locale("hi", "IN")).format(Date())
            "आज की तारीख $date है।"
        }

        p.contains("सेटिंग खोलो") ||
            p.contains("open settings") ||
            p.contains("खोलो सेटिंग") -> {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.getOrElse {
                return "डिवाइस की Settings नहीं खुल सकीं।"
            }
            "डिवाइस की Settings खोलने का अनुरोध भेज दिया है।"
        }

        p.contains("youtube खोलो") ||
            p.contains("यूट्यूब खोलो") ||
            p == "open youtube" -> {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage("com.google.android.youtube")

            runCatching {
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    openUrl(context, "https://www.youtube.com")
                }
            }.getOrElse {
                return "YouTube नहीं खुल सका।"
            }
            "YouTube खोलने का अनुरोध भेज दिया है।"
        }

        p.contains("chrome खोलो") ||
            p == "open chrome" -> {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage("com.android.chrome")

            runCatching {
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    openUrl(context, "https://www.google.com")
                }
            }.getOrElse {
                return "Browser नहीं खुल सका।"
            }
            "Browser खोलने का अनुरोध भेज दिया है।"
        }

        p.startsWith("गूगल पर खोजो ") ||
            p.startsWith("search google for ") -> {
            val query = if (p.startsWith("गूगल पर खोजो ")) {
                prompt.substringAfter("गूगल पर खोजो ").trim()
            } else {
                prompt.substringAfter("search google for ").trim()
            }

            if (query.isBlank()) {
                "कृपया बताओ कि Google पर क्या खोजना है।"
            } else {
                openUrl(
                    context,
                    "https://www.google.com/search?q=${Uri.encode(query)}"
                )
                "Google पर खोज खोल दी है: $query"
            }
        }

        else -> null
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

private fun localReply(prompt: String): String {
    val p = prompt.lowercase(Locale.ROOT)

    return when {
        p.contains("नमस्ते") ||
            p.contains("hello") ||
            p == "hi" -> {
            "नमस्ते! मैं Mr Stark हूँ। AI जवाब पाने के लिए Settings में अपनी Gemini API key सेव करो।"
        }

        p.contains("तुम कौन हो") ||
            p.contains("who are you") -> {
            "मैं Mr Stark हूँ, तुम्हारा Android assistant। मैं इस version में chat, voice input, बोलकर जवाब और कुछ quick actions कर सकता हूँ।"
        }

        p.contains("क्या कर सकते हो") ||
            p.contains("what can you do") -> {
            "मैं voice input ले सकता हूँ, जवाब बोल सकता हूँ, समय और तारीख बता सकता हूँ, Settings/YouTube/Browser खोल सकता हूँ और Google search शुरू कर सकता हूँ। AI chat के लिए API key चाहिए।"
        }

        else -> {
            "अभी AI key सेव नहीं है, इसलिए मैं सीमित local mode में हूँ। Settings खोलकर Gemini API key सेव करो, फिर अपना सवाल दोबारा पूछो।"
        }
    }
}

private fun saveMemory(
    prefs: android.content.SharedPreferences,
    messages: List<ChatLine>
) {
    val arr = JSONArray()

    messages.takeLast(50).forEach { line ->
        arr.put(
            JSONObject()
                .put("who", line.who)
                .put("text", line.text)
        )
    }

    prefs.edit().putString("chat_memory", arr.toString()).apply()
}
