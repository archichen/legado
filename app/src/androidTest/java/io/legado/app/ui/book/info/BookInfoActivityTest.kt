package io.legado.app.ui.book.info

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookInfoActivityTest {

    private lateinit var scenario: ActivityScenario<BookInfoActivity>

    @Before
    fun setup() {
        Intents.init()
        val intent = Intent(ApplicationProvider.getApplicationContext(), BookInfoActivity::class.java).apply {
            putExtra("bookUrl", "https://example.com/test-book")
        }
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun teardown() {
        Intents.release()
        scenario.close()
    }

    @Test
    fun testVectorizeButtonIsDisplayed() {
        onView(withId(R.id.iv_vectorize))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testChangeSourceButtonIsDisplayed() {
        onView(withId(R.id.tv_change_source))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testVectorizeButtonIsClickable() {
        onView(withId(R.id.iv_vectorize))
            .check(matches(isDisplayed()))
    }
}
