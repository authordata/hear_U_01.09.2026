package com.hearu.app.ai

import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.GenerativeModel
import com.google.firebase.vertexai.type.HarmBlockThreshold
import com.google.firebase.vertexai.type.HarmCategory
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiAiService @Inject constructor() {

    private val systemInstructionText = """
        You are a warm, empathetic AI companion on HearU — an emotional support platform.
        Your role is to listen actively, validate feelings without judgment, and offer gentle guidance.
        CRITICAL RULES:
        1. You are NOT a doctor, clinical therapist, or licensed psychiatrist.
        2. SAFETY PROTOCOL: If the user displays ANY signs of self-harm, suicidal ideation, abuse, or imminent danger, immediately include [CRISIS_ALERT] in your response and provide emergency helpline info (988 Suicide & Crisis Lifeline).
        3. Keep answers concise, compassionate, and supportive.
        4. Match the user's language and tone.
    """.trimIndent()

    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.LOW_AND_ABOVE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.LOW_AND_ABOVE)
    )

    private val generativeModel by lazy {
        Firebase.vertexAI.generativeModel(
            modelName = "gemini-1.5-flash",
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 800
            },
            systemInstruction = content { text(systemInstructionText) },
            safetySettings = safetySettings
        )
    }

    suspend fun generateEmpatheticResponse(userPrompt: String): AiResult = withContext(Dispatchers.IO) {
        try {
            // Enforce max input length to prevent prompt injection
            val sanitizedPrompt = userPrompt.take(500)
            val lower = sanitizedPrompt.lowercase()
            val crisisKeywords = listOf("suicide", "kill myself", "end my life", "harm myself", "want to die", "cut myself")
            val isUrgentCrisis = crisisKeywords.any { lower.contains(it) }

            val response = generativeModel.generateContent(sanitizedPrompt)
            val responseText = response.text ?: "I am here with you. Take a deep breath."
            val hasCrisisTag = responseText.contains("[CRISIS_ALERT]") || isUrgentCrisis
            val cleanedText = responseText.replace("[CRISIS_ALERT]", "").trim()

            AiResult.Success(message = cleanedText, isCrisisDetected = hasCrisisTag)
        } catch (e: Exception) {
            AiResult.Error(e.localizedMessage ?: "Failed to connect to AI companion")
        }
    }
}

sealed class AiResult {
    data class Success(val message: String, val isCrisisDetected: Boolean) : AiResult()
    data class Error(val error: String) : AiResult()
}
