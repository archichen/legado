package io.legado.app.ui.book.ai

import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import org.junit.Test
import java.time.Duration

class AgentIntegrationTest {

    @Test
    fun testAgentWithMimoModel() {
        val baseUrl = "https://api.xiaomimimo.com/v1"
        val apiKey = "tp-cyuojc72xwr94ekdm90uu7p4icp8s3cmewyatydl4030eqkl"
        val modelName = "mimo-v2.5-pro"

        val chatModel = OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.3)
            .timeout(Duration.ofSeconds(60))
            .build()

        val response = chatModel.chat("你好，请用中文简单介绍一下你自己。")
        println("Model response: $response")
        assert(response.isNotEmpty()) { "Response should not be empty" }
    }
}
