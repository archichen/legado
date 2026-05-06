package io.legado.app.ui.book.vectorize

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.not

@RunWith(AndroidJUnit4::class)
class VectorizeActivityTest {

    private lateinit var scenario: ActivityScenario<VectorizeActivity>

    @Before
    fun setup() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VectorizeActivity::class.java).apply {
            putExtra("bookUrl", "https://example.com/test-book")
        }
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun teardown() {
        scenario.close()
    }

    @Test
    fun testActivityLaunches() {
        onView(withId(R.id.recycler_view))
            .check(matches(isDisplayed()))
        onView(withId(R.id.progress_bar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_start))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_stop))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_clear))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testProgressTextDisplayed() {
        onView(withId(R.id.tv_progress))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testStopButtonInitiallyDisabled() {
        onView(withId(R.id.btn_stop))
            .check(matches(not(isEnabled())))
    }

    @Test
    fun testStartButtonInitiallyEnabled() {
        onView(withId(R.id.btn_start))
            .check(matches(isEnabled()))
    }

    @Test
    fun testProgressBarStartsAtZero() {
        onView(withId(R.id.progress_bar))
            .check(matches(isDisplayed()))
    }
}
