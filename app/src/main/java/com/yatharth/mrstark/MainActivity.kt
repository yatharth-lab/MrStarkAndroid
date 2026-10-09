package com.yatharth.mrstark

import androidx.activity.compose.setContent
import android.Manifest
import android.content.Intent
import android.app.SearchManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private val Navy = Color(0xFF06111F)
private val Panel = Color(0xFF10243A)
private val Cyan = Color(0xFF43D9FF)
private val Muted = Color(0xFF9CB7CA)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Cyan,
                background = Navy,
                surface = Panel,
                onPrimary = Navy,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                MrStarkScreen(
                    speak = { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mrstark_reply") }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

data class ChatLine(val who: String, val text: String)

@Composable
private fun MrStarkScreen(speak: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mrstark_prefs", 0) }
    val messages = remember {
        mutableStateListOf(
            ChatLine("MR STARK", "नमस्ते! मैं Mr Stark हूँ। नीचे लिखकर या माइक्रोफोन से बात शुरू करो।")
        )
    }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("SYSTEM ONLINE • VERSION 0.1") }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSpeechRecognition(context) { recognized ->
            input = recognized
        } else {
            Toast.makeText(context, "Voice के लिए microphone permission जरूरी है।", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        // Load the last few local chat lines. This is local-only memory on this device.
        val saved = prefs.getString("chat_memory", null)
        if (!saved.isNullOrBlank()) {
            runCatching {
                val arr = org.json.JSONArray(saved)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    messages.add(ChatLine(obj.optString("who"), obj.optString("text")))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF071525), Navy, Color(0xFF02060C))))
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("MR STARK", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Cyan)
                    Text(status, fontSize = 10.sp, color = Muted, letterSpacing = 1.2.sp)
                }
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Cyan)
                }
            }

            if (showSettings) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AI SETTINGS", color = Cyan, fontWeight = FontWeight.Bold)
                        Text("Gemini API key (केवल निजी परीक्षण के लिए)", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API key") },
                            singleLine = true
                        )
                        Text("सुरक्षा: इस key को GitHub पर कभी upload न करें। सार्वजनिक ऐप के लिए सुरक्षित backend चाहिए।", color = Muted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                prefs.edit().putString("gemini_api_key", apiKey.trim()).apply()
                                showSettings = false
                                status = if (apiKey.isBlank()) "LOCAL MODE • API KEY NOT SET" else "AI KEY SAVED LOCALLY"
                            }) { Text("Save key") }
                            Button(onClick = {
                                apiKey = ""
                                prefs.edit().remove("gemini_api_key").apply()
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF713B4B))) { Text("Clear") }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2034)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .background(Brush.radialGradient(listOf(Color(0xFF155B7C), Color(0xFF0B2339), Navy)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("STARK", color = Cyan, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (busy) "PROCESSING..." else "READY TO ASSIST", color = Cyan, fontWeight = FontWeight.Bold)
                    Text("Voice • Intelligence • Tasks", color = Muted, fontSize = 12.sp)
                }
            }

            Text("CONVERSATION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { line ->
                    val isUser = line.who == "YOU"
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF123A50) else Panel),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(line.who, color = if (isUser) Cyan else Color(0xFF8CE9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(line.text, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startSpeechRecognition(context) { input = it }
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.background(Color(0xFF123A50), CircleShape)
                ) { Icon(Icons.Default.Mic, contentDescription = "Speak", tint = Cyan) }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Stark से बात करो…") },
                    maxLines = 3
                )
                IconButton(
                    onClick = {
                        val prompt = input.trim()
                        if (prompt.isNotEmpty() && !busy) {
                            messages.add(ChatLine("YOU", prompt))
                            input = ""
                            busy = true
                            status = "THINKING..."
                            saveMemory(prefs, messages)
                            kotlinx.coroutines.MainScope().launch {
                                val reply = if (apiKey.isNotBlank()) {
                                    callGemini(apiKey.trim(), prompt, messages.toList())
                                } else {
                                    localReply(prompt)
                                }
                                messages.add(ChatLine("MR STARK", reply))
                                saveMemory(prefs, messages)
                                speak(reply)
                                busy = false
                                status = if (apiKey.isBlank()) "LOCAL MODE • ADD API KEY FOR AI" else "SYSTEM ONLINE • AI CONNECTED"
                            }
                        }
                    },
                    modifier = Modifier.background(Cyan, CircleShape)
                ) { Icon(Icons.Default.Send, contentDescription = "Send", tint = Navy) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, input.ifBlank { "आज की मुख्य खबरें" })
                        runCatching { context.startActivity(intent) }.onFailure {
                            Toast.makeText(context, "इस डिवाइस पर web search उपलब्ध नहीं है।", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Cyan)
                    Text(" Web search")
                }
                Button(
                    onClick = {
                        messages.clear()
                        messages.add(ChatLine("MR STARK", "नई बातचीत शुरू हो गई।"))
                        prefs.edit().remove("chat_memory").apply()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel)
                ) { Text("Clear chat") }
            }
        }
    }
}

private fun startSpeechRecognition(
    context: android.content.Context,
    onResult: (String) -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Toast.makeText(context, "Speech recognition service उपलब्ध नहीं है।", Toast.LENGTH_LONG).show()
        return
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            Toast.makeText(context, "आवाज़ समझ नहीं आई। फिर कोशिश करो।", Toast.LENGTH_SHORT).show()
            recognizer.destroy()
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) onResult(text)
            recognizer.destroy()
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Mr Stark को अपना निर्देश बोलें")
    }
    recognizer.startListening(intent)
}

private suspend fun callGemini(apiKey: String, prompt: String, history: List<ChatLine>): String =
    withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${URLEncoder.encode(apiKey, "UTF-8")}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val contents = org.json.JSONArray()
            history.takeLast(8).forEach { line ->
                val role = if (line.who == "YOU") "user" else "model"
                contents.put(JSONObject().put("role", role).put("parts", org.json.JSONArray().put(JSONObject().put("text", line.text))))
            }
            // Ensure current prompt is present even if history was updated asynchronously.
            if (history.none { it.who == "YOU" && it.text == prompt }) {
                contents.put(JSONObject().put("role", "user").put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt))))
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", "You are Mr Stark, a helpful Android personal assistant. Reply clearly in Hindi by default. Be honest about limitations. Do not claim to perform device actions unless actually done."))))
                .put("contents", contents)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(response)
            if (!json.has("candidates")) {
                "AI API error: " + json.optJSONObject("error")?.optString("message", "जवाब नहीं मिला")
            } else {
                json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text", "जवाब खाली मिला।")
            }
        } catch (e: Exception) {
            "AI से कनेक्ट नहीं हो पाया: ${e.localizedMessage ?: "network error"}। इंटरनेट और API key जाँचो।"
        }
    }

private fun localReply(prompt: String): String {
    val p = prompt.lowercase(Locale.ROOT)
    return when {
        p.contains("समय") || p.contains("time") -> "मैं अभी लोकल मोड में हूँ। समय देखने के लिए अपने टैबलेट की घड़ी देखो।"
        p.contains("यूट्यूब") || p.contains("youtube") -> "मैं YouTube workflow में मदद कर सकता हूँ। अभी यह starter version अपने-आप चैनल में बदलाव नहीं करता।"
        p.contains("नमस्ते") || p.contains("hello") || p.contains("hi") -> "नमस्ते! AI जवाब पाने के लिए Settings में अपनी Gemini API key जोड़ो।"
        else -> "अभी मैं लोकल मोड में हूँ। Settings में Gemini API key जोड़ने पर AI जवाब चालू होगा। मैं अभी केवल ऐप के अंदर की सुविधाएँ देता हूँ।"
    }
}

private fun saveMemory(
    prefs: android.content.SharedPreferences,
    messages: List<ChatLine>
) {
    val arr = org.json.JSONArray()
    messages.takeLast(40).forEach {
        arr.put(JSONObject().put("who", it.who).put("text", it.text))
    }
    prefs.edit().putString("chat_memory", arr.toString()).apply()
}
