package me.aireader.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The reading page registers Activity Result launchers during its first composition. */
@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {
    @Test fun readingPageComposesWithoutLosingActivityResultOwner() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }
}
