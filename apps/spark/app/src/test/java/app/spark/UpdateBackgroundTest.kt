package app.spark

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.update.AppUpdate
import app.spark.update.UpdateNotice
import app.spark.update.UpdateReceiver
import app.spark.update.UpdateService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * The update away from the screen, on the JVM: the service that keeps a download alive
 * with the screen off, and what the app does when Android asks for the user's yes while
 * nobody is looking at it.
 */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [Sight::class])
class UpdateBackgroundTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before
    fun outOfSight() {
        Sight.onScreen = false
    }

    private fun answer(status: Int, sessionId: Int) = UpdateReceiver().onReceive(
        app,
        Intent()
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            .putExtra(Intent.EXTRA_INTENT, Intent("confirm")),
    )

    @Test
    fun `the service shows the download and stops once there is nothing to keep alive`() {
        val service = Robolectric.buildService(UpdateService::class.java).create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateNotice.PROGRESS, shadowOf(service).lastForegroundNotificationId)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `an install prompt that comes up out of sight waits behind a notification`() {
        answer(PackageInstaller.STATUS_PENDING_USER_ACTION, sessionId = 7)
        assertNull(shadowOf(app).nextStartedActivity)
        assertNotNull(notifications.getNotification(UpdateNotice.RESULT))
        assertTrue(AppUpdate.prompt.value)

        // Giving the prompt up comes back as a cancelled install; that isn't shown as a failure.
        answer(PackageInstaller.STATUS_FAILURE_ABORTED, sessionId = 7)
        assertEquals(AppUpdate.State.Idle, AppUpdate.state.value)
    }

    @Test
    fun `with the app on screen the install prompt opens straight away`() {
        Sight.onScreen = true
        answer(PackageInstaller.STATUS_PENDING_USER_ACTION, sessionId = 9)
        assertEquals("confirm", shadowOf(app).nextStartedActivity.action)
        assertNull(notifications.getNotification(UpdateNotice.RESULT))
    }

    @Test
    fun `a cancelled install the app did not cause is still reported`() {
        answer(PackageInstaller.STATUS_FAILURE_ABORTED, sessionId = 8)
        assertTrue(AppUpdate.state.value is AppUpdate.State.Failed)
    }
}

/** Stands in for Android's answer to "is this app the one on screen?". */
@Implements(ActivityManager::class)
class Sight {
    companion object {
        @JvmField
        var onScreen = false

        @JvmStatic
        @Implementation
        fun getMyMemoryState(state: ActivityManager.RunningAppProcessInfo) {
            state.importance = if (onScreen) {
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } else {
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
            }
        }
    }
}
