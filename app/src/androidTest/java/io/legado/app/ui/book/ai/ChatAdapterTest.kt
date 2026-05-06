package io.legado.app.ui.book.ai

import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.ChatMessage
import io.legado.app.databinding.ItemChatMessageBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatAdapterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun testAdapterCreation() {
        val adapter = ChatAdapter()
        assertNotNull(adapter)
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun testAdapterSubmitList() {
        val adapter = ChatAdapter()
        val messages = listOf(
            ChatMessage(
                id = 1,
                bookUrl = "test-url",
                role = ChatMessage.ROLE_USER,
                content = "Hello"
            ),
            ChatMessage(
                id = 2,
                bookUrl = "test-url",
                role = ChatMessage.ROLE_ASSISTANT,
                content = "Hi there!"
            )
        )
        adapter.submitList(messages)
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun testAdapterHandlesAllRoles() {
        val adapter = ChatAdapter()
        val messages = listOf(
            ChatMessage(id = 1, bookUrl = "test", role = ChatMessage.ROLE_USER, content = "User msg"),
            ChatMessage(id = 2, bookUrl = "test", role = ChatMessage.ROLE_ASSISTANT, content = "Assistant msg"),
            ChatMessage(id = 3, bookUrl = "test", role = ChatMessage.ROLE_THINKING, content = "Thinking..."),
            ChatMessage(id = 4, bookUrl = "test", role = ChatMessage.ROLE_TOOL_CALL, content = "searchContent()"),
            ChatMessage(id = 5, bookUrl = "test", role = ChatMessage.ROLE_TOOL_RESULT, content = "Found 3 results")
        )
        adapter.submitList(messages)
        assertEquals(5, adapter.itemCount)
    }

    @Test
    fun testAdapterEmptyList() {
        val adapter = ChatAdapter()
        adapter.submitList(emptyList())
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun testChatMessageRoles() {
        assertEquals("user", ChatMessage.ROLE_USER)
        assertEquals("assistant", ChatMessage.ROLE_ASSISTANT)
        assertEquals("system", ChatMessage.ROLE_SYSTEM)
        assertEquals("thinking", ChatMessage.ROLE_THINKING)
        assertEquals("tool_call", ChatMessage.ROLE_TOOL_CALL)
        assertEquals("tool_result", ChatMessage.ROLE_TOOL_RESULT)
    }

    @Test
    fun testChatMessageCreation() {
        val msg = ChatMessage(
            bookUrl = "https://example.com/book",
            role = ChatMessage.ROLE_USER,
            content = "Test message"
        )
        assertEquals("https://example.com/book", msg.bookUrl)
        assertEquals(ChatMessage.ROLE_USER, msg.role)
        assertEquals("Test message", msg.content)
        assertNotNull(msg.timestamp)
    }
}
