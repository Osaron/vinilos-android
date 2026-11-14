package co.edu.uniandes.vinilos

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.ui.tracks.TrackFormFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackFormTest {
    @Test fun validationAndDraftAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var submitted: String? = null
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResultListener(TrackFormFragment.VALID_TRACK_RESULT, activity) { _, data ->
                    submitted = data.getString(TrackFormFragment.TRACK_NAME) + "/" + data.getString(TrackFormFragment.TRACK_DURATION)
                }
                activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, TrackFormFragment()).commitNow()
            }
            onView(withId(R.id.track_save_button)).perform(scrollTo(), click())
            onView(withId(R.id.track_name_error)).check(matches(isDisplayed()))
            onView(withId(R.id.track_duration_error)).check(matches(isDisplayed()))
            assertNull(submitted)
            onView(withId(R.id.track_name_input)).perform(replaceText("  Canción  "), closeSoftKeyboard())
            onView(withId(R.id.track_duration_input)).perform(replaceText("3:60"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.track_name_input)).check(matches(withText("  Canción  ")))
            onView(withId(R.id.track_duration_input)).check(matches(withText("3:60")))
            onView(withId(R.id.track_duration_error)).check(matches(isDisplayed()))
            onView(withId(R.id.track_save_button)).perform(scrollTo(), click())
            assertNull(submitted)
            onView(withId(R.id.track_duration_input)).perform(replaceText("3:45"), closeSoftKeyboard())
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResultListener(TrackFormFragment.VALID_TRACK_RESULT, activity) { _, data ->
                    submitted = data.getString(TrackFormFragment.TRACK_NAME) + "/" + data.getString(TrackFormFragment.TRACK_DURATION)
                }
            }
            onView(withId(R.id.track_save_button)).perform(scrollTo(), click())
            assertEquals("Canción/3:45", submitted)
        }
    }
}
