package app.fajralarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.fajralarm.alarm.AlarmScheduler

class FajrAlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        val ringing = NotificationChannel(
            CHANNEL_ALARM,
            "Ringing alarm",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shown while the Fajr alarm is ringing"
            setSound(null, null) // the service plays the sound itself on the alarm stream
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val info = NotificationChannel(
            CHANNEL_INFO,
            "Alarm status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Snooze and missed-alarm notices" }
        nm.createNotificationChannels(listOf(ringing, info))

        // Make sure an alarm is always queued while the app process is alive.
        AlarmScheduler.reschedule(this)
    }

    companion object {
        const val CHANNEL_ALARM = "fajr_alarm_ringing"
        const val CHANNEL_INFO = "fajr_alarm_info"
    }
}
