package com.michaeltroger.gruenerpass

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.michaeltroger.gruenerpass.coroutines.dispatcher.di.CoroutineDispatchersModule
import com.michaeltroger.gruenerpass.db.AppDatabase
import com.michaeltroger.gruenerpass.db.di.DatabaseModule
import com.michaeltroger.gruenerpass.lock.AppLockedRepo
import com.michaeltroger.gruenerpass.pdfimporter.PdfImporter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@UninstallModules(DatabaseModule::class, CoroutineDispatchersModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    application = HiltTestApplication::class,
    sdk = [30],
)
class MainActivityRobolectricTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var appLockedRepo: AppLockedRepo

    @Inject
    lateinit var pdfImporter: PdfImporter

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()

        // Ensure clean state between tests.
        appDatabase.clearAllTables()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }

        // Force list layout so we can assert the title in the list item (`@id/name`).
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean(context.getString(R.string.key_preference_show_list_layout), true)
            .putBoolean(context.getString(R.string.key_preference_biometric), false)
            .commit()

        // Ensure we start unlocked (start destination = certificates).
        runBlocking {
            appLockedRepo.unlockApp()
        }
    }

    @Test
    fun openApp_showsEmptyState() {
        val activity = launchMainActivity()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.certificates)
        val addButton = activity.findViewById<View>(R.id.add_button)

        waitUntil("Empty state") {
            recyclerView.adapter != null && addButton.visibility == View.VISIBLE
        }

        assertEquals(0, recyclerView.adapter!!.itemCount)
        assertEquals(View.VISIBLE, addButton.visibility)
    }

    private fun launchMainActivity(): MainActivity {
        // Important: pass `savedInstanceState = null` so `MainActivity` behaves like a cold start.
        return Robolectric.buildActivity(MainActivity::class.java)
            .create(null)
            .start()
            .resume()
            .visible()
            .get()
    }

    private fun waitUntil(
        reason: String,
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            shadowOf(Looper.getMainLooper()).idle()
            // Give background coroutines/threads a chance to progress.
            Thread.sleep(10)
        }
        assertTrue("$reason (timeout ${timeoutMs}ms)", condition())
    }
}
