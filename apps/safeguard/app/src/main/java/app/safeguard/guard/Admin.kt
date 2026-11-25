package app.safeguard.guard

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.safeguard.R

/**
 * Makes the app a device admin app, for one reason: Android won't uninstall one until
 * that is switched off again, and a locked Safeguard keeps the page for switching it off
 * closed. None of a device admin's powers are asked for or used.
 */
class Admin : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_warning)

    companion object {
        fun component(context: Context) = ComponentName(context, Admin::class.java)

        fun active(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        /** Android's screen that asks the user to make this a device admin app. */
        fun ask(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_about))

        fun stop(context: Context) =
            context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
    }
}
