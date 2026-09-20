package com.yk.notiffilter.classify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Groq adapter, talking to the OpenAI-compatible chat-completions endpoint.
 *
 * Chosen over Gemini's free tier because Groq does not retain inference data by
 * default, whereas Gemini's free tier is explicitly licensed for training and human
 * review — which matters for a service that reads notification text.
 */
class GroqClassifier(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : NotificationClassifier {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun classify(request: ClassifyRequest): Result<Classification> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = ChatRequest(
                    model = model,
                    temperature = 0.0,
                    responseFormat = ResponseFormat("json_object"),
                    messages = listOf(
                        ChatMessage("system", systemPrompt(request)),
                        ChatMessage("user", userPrompt(request)),
                    ),
                )
                val raw = post(json.encodeToString(ChatRequest.serializer(), body))
                val parsed = json.decodeFromString(ChatResponse.serializer(), raw)
                val content = parsed.choices.firstOrNull()?.message?.content
                    ?: error("No completion returned")
                val verdict = json.decodeFromString(VerdictJson.serializer(), content)
                Classification(
                    keep = verdict.keep,
                    reason = verdict.reason.ifBlank { "No reason given" },
                )
            }
        }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = ChatRequest(
                model = model,
                temperature = 0.0,
                responseFormat = ResponseFormat("json_object"),
                messages = listOf(
                    ChatMessage(
                        "user",
                        """Reply with exactly {"keep": true, "reason": "ok"}""",
                    ),
                ),
            )
            post(json.encodeToString(ChatRequest.serializer(), body))
            "Connected to $model"
        }
    }

    private fun post(payload: String): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${text.take(300)}")
            }
            return text
        }
    }

    private fun systemPrompt(request: ClassifyRequest): String = buildString {
        appendLine("You triage Android notifications for one app and decide whether the user sees them.")
        appendLine()
        appendLine("App: ${request.appLabel}")
        appendLine("The user's instruction for this app, which is the sole authority on what matters:")
        appendLine("\"\"\"")
        appendLine(request.prose.trim())
        appendLine("\"\"\"")
        appendLine()
        if (request.examples.isNotEmpty()) {
            appendLine("The user has previously corrected these decisions. Follow the pattern they show,")
            appendLine("including for wordings you have not seen before:")
            request.examples.forEach { example ->
                val label = if (example.keep) "KEEP" else "HIDE"
                appendLine("- $label | ${example.title.trim()} | ${example.text.trim().take(160)}")
            }
            appendLine()
        }
        appendLine("Reply with JSON only: {\"keep\": <true|false>, \"reason\": \"<under 12 words>\"}")
        appendLine("keep=true means show the user. keep=false means hide it.")
        appendLine("When genuinely torn, prefer keep=true: a hidden important message costs the user far more than one extra notification.")
    }

    private fun userPrompt(request: ClassifyRequest): String = buildString {
        appendLine("Title: ${request.title}")
        appendLine("Body: ${request.text}")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"

        /**
         * Pinned here rather than hardcoded at call sites, and user-editable in
         * Settings: Groq's free model lineup churns, and a retired id would
         * otherwise mean a new build.
         */
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("response_format") val responseFormat: ResponseFormat,
)

@Serializable
private data class ResponseFormat(val type: String)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
private data class Choice(val message: ChatMessage? = null)

@Serializable
private data class VerdictJson(val keep: Boolean, val reason: String = "")
