package app.fajralarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import app.fajralarm.alarm.AlarmScheduler

class FajrAlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Sink reference photos from older versions are no longer used; don't keep them around.
        java.io.File(filesDir, "sink").takeIf { it.exists() }?.deleteRecursively()

        // Make sure an alarm is always queued while the app process is alive.
        AlarmScheduler.reschedule(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The phone's language may have changed; creating a channel again renames it.
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val ringing = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.channel_ringing_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_ringing_description)
            setSound(null, null) // the service plays the sound itself on the alarm stream
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val info = NotificationChannel(
            CHANNEL_INFO,
            getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_status_description) }
        nm.createNotificationChannels(listOf(ringing, info))
    }

    companion object {
        const val CHANNEL_ALARM = "fajr_alarm_ringing"
        const val CHANNEL_INFO = "fajr_alarm_info"
    }
}
