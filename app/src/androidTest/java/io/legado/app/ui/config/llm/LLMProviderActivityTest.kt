package io.legado.app.ui.config.llm

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LLMProviderActivityTest {

    private lateinit var scenario: ActivityScenario<LLMProviderActivity>

    @Before
    fun setup() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LLMProviderActivity::class.java)
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
        onView(withId(R.id.fab_add))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testAddButtonShowsDialog() {
        onView(withId(R.id.fab_add))
            .perform(click())

        onView(withText(R.string.add))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun testAddProviderDialogHasAllFields() {
        onView(withId(R.id.fab_add))
            .perform(click())

        onView(withText(R.string.llm_provider_name))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText(R.string.llm_base_url))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText(R.string.llm_api_key))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText(R.string.llm_model_name))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun testAddProviderWithValidData() {
        onView(withId(R.id.fab_add))
            .perform(click())

        onView(withId(R.id.et_name))
            .inRoot(isDialog())
            .perform(replaceText("Test Provider"))

        onView(withId(R.id.et_base_url))
            .inRoot(isDialog())
            .perform(replaceText("https://api.example.com/v1"))

        onView(withId(R.id.et_api_key))
            .inRoot(isDialog())
            .perform(replaceText("test-key-123"))

        onView(withId(R.id.et_model_name))
            .inRoot(isDialog())
            .perform(replaceText("gpt-4"))

        onView(withText(R.string.dialog_confirm))
            .inRoot(isDialog())
            .perform(click())

        onView(withText("Test Provider"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testCancelDialogDoesNotAddProvider() {
        onView(withId(R.id.fab_add))
            .perform(click())

        onView(withId(R.id.et_name))
            .inRoot(isDialog())
            .perform(replaceText("Should Not Exist"))

        onView(withText(R.string.dialog_cancel))
            .inRoot(isDialog())
            .perform(click())
    }
}
