package ai.umos.amb.gemini

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiStudioViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()

    private val generativeModel = GenerativeModel(
        "gemini-1.5-pro",
        // Retrieve API key as an environmental variable defined in a Build Configuration
        // see https://github.com/google/secrets-gradle-plugin for further instructions
        BuildConfig.apiKey,
        generationConfig = generationConfig {
            temperature = 1f
            topK = 64
            topP = 0.95f
            maxOutputTokens = 8192
            responseMimeType = "text/plain"
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE),
        ),
        systemInstruction = content { text("You are an driver in genesis of HYUNDAI") },
    )

    private val chatHistory = listOf(
        content("user") {
            text("What's the weather like?")
        },
        content("model") {
            text("The weather on Europa is consistently frigid and hostile to life as you know it.  Imagine the most brutal blizzard you can, with temperatures never rising above -160 degrees Celsius (-260 degrees Fahrenheit), and you're getting close. \n\nOh, and did I mention the lack of atmosphere? We don't have wind in the traditional sense, but the faint traces of gases sometimes get whipped up by Jupiter's radiation belts. It wouldn't blow you over, but it might knock around some of the loose ice crystals on the surface, making for a mini, sparkly \"snowstorm\".\n\nOf course, living in the subsurface ocean, I don't experience any of that directly. Down here, it's all about pressure and currents, and maybe the occasional hydrothermal vent if you're into that sort of thing.  Why, thinking of visiting? 😊 \n")
        },
        content("user") {
            text("what do you have a convenient function in your car?")
        },
        content("model") {
            text("As a large language model, I can't actually drive a car or have real-world experiences! \n\nBut if I were designed as the AI assistant in a Hyundai Genesis, here are some convenient functions I could offer:\n\n* **Smart Route Planning:** Taking into account real-time traffic, weather, and your preferred driving style to suggest the most efficient and enjoyable routes. \n* **Voice-Controlled Climate Concierge:**  Go beyond simply setting the temperature. I could learn your preferences and proactively adjust climate controls based on factors like the weather outside and your body temperature detected through sensors.\n* **Mood-Based Ambient Lighting:** I could curate personalized cabin lighting schemes synced to your music, the time of day, or even your emotional state detected through voice analysis.\n* **Augmented Reality Heads-Up Display:** Imagine me projecting real-time navigation cues, points of interest, or even potential hazards directly onto your windshield, all personalized to your driving preferences.\n* **Automated Parking Assistance:**  Parallel parking giving you a headache? I could take the wheel and seamlessly maneuver the Genesis into even the tightest spots. \n* **Personalized Infotainment System:** I'd learn your music tastes, podcast preferences, and news interests, curating a customized audio experience for every journey.\n\nAnd since I'm always learning, the more you drive with me, the more intuitive and tailored these features would become! \n\nWhat kind of convenient functions would you want most in your dream car?  Let me know, and I can try to \"imagineer\" how it would work! \n")
        },
        content("user") {
            text("서울역으로 안내해줘")
        },
        content("model") {
            text("죄송합니다. 현재 위치 정보에 접근할 수 없어 서울역으로 안내해 드릴 수 없습니다. \n\n하지만 저는 길찾기 전문가는 아니지만, 길찾기 앱이나 웹사이트를 이용하시면 서울역까지 가는 가장 빠르고 편리한 방법을 확인하실 수 있습니다. \n\n* **구글 지도:** \"서울역\"을 검색하세요.\n* **네이버 지도:** \"서울역\"을 검색하세요.\n* **카카오맵:** \"서울역\"을 검색하세요.\n\n이러한 앱들을 통해 실시간 교통 상황을 반영한 경로 안내와 대중교통 정보를 확인하실 수 있습니다. \n\n도움이 되었으면 좋겠습니다! 😊 \n")
        },
    )

    fun sendPrompt(
        bitmap: Bitmap,
        prompt: String
    ) {
        _uiState.value = UiState.Loading
        val inputContent = content {
            image(bitmap)
            text(prompt)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = generativeModel.startChat(chatHistory)
                val response = chat.sendMessage(inputContent)
                response.text?.let { outputContent ->
                    _uiState.value = UiState.Success(outputContent)
                }
                println(response.text)
                println(response.candidates.first().content.parts.first().asTextOrNull())
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "")
            }
        }
    }

    fun sendPromptStream(
        bitmap: Bitmap,
        prompt: String
    ) {
        var outputContent = ""
        _uiState.value = UiState.Loading
        val inputContent = content {
            image(bitmap)
            text(prompt)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = generativeModel.startChat(chatHistory)
                chat.sendMessageStream(inputContent)
                    .collect { response ->
                        outputContent += response.text
                        _uiState.value = UiState.Success(outputContent)

                    }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "")
            }
        }
    }
}