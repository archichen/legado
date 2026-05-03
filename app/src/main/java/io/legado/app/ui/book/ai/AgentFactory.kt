package io.legado.app.ui.book.ai

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.LLMProvider
import java.time.Duration

object AgentFactory {

    fun createBookAgent(
        provider: LLMProvider,
        book: Book,
        chatMemoryMaxMessages: Int = 20
    ): BookAssistant {
        val chatModel = OpenAiChatModel.builder()
            .baseUrl(provider.baseUrl)
            .apiKey(provider.apiKey)
            .modelName(provider.modelName)
            .temperature(0.3)
            .timeout(Duration.ofSeconds(120))
            .build()

        val bookSearchTool = BookSearchTool(book)

        return AiServices.builder(BookAssistant::class.java)
            .chatModel(chatModel)
            .tools(bookSearchTool)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(chatMemoryMaxMessages))
            .build()
    }
}
