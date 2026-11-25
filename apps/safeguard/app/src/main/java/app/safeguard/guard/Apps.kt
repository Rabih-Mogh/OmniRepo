package app.safeguard.guard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import app.safeguard.core.Seen

/** What kind of app a package is, and what the phone calls it. */
internal class Apps(private val context: Context) {

    private val labels = HashMap<String, String>()
    private var browsers: Set<String> = emptySet()
    private var asked = -1_000_000L

    /** The app's name as the phone shows it; its package name when it can't be found. */
    fun label(app: String): String = labels.getOrPut(app) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString()
        } catch (_: Exception) {
            app
        }
    }

    /** Any app that offers to open web links, whichever browser it is. Asked again now and then, for ones installed since. */
    fun browser(app: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (app !in browsers && now - asked > 60_000) {
            asked = now
            val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
            browsers = try {
                context.packageManager.queryIntentActivities(link, PackageManager.MATCH_ALL)
                    .mapTo(HashSet()) { it.activityInfo.packageName } - context.packageName
            } catch (_: Exception) {
                browsers
            }
        }
        return app in browsers
    }

    fun store(app: String): Boolean = app in STORES || installer(app)

    /** Android's prompt for installing and uninstalling, under the names phone makers give it. */
    fun installer(app: String): Boolean = "packageinstaller" in app

    /** Anywhere an app can be stopped, switched off or removed from. */
    fun settings(app: String): Boolean = "settings" in app || installer(app) || app in SETTINGS

    /** Whether the text of this app's windows is read at all. */
    fun read(app: String): Boolean = store(app) || settings(app) || browser(app)

    private companion object {
        val STORES = setOf(
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.huawei.appmarket",
            "com.xiaomi.mipicks",
            "com.heytap.market",
            "org.fdroid.fdroid",
            "com.aurora.store",
        )

        // Settings screens that live outside the phone's Settings app.
        val SETTINGS = setOf(
            "com.samsung.accessibility",
            "com.samsung.android.lool",
            "com.miui.securitycenter",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }
}

/** Reads the text of a window. */
internal object Reading {

    /** Every piece of text in the window under [root], nearest the top first, up to [most] places looked at. */
    fun texts(root: AccessibilityNodeInfo, most: Int = 600): List<Seen> {
        val out = ArrayList<Seen>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        var looked = 0
        while (queue.isNotEmpty() && looked++ < most) {
            val node = queue.removeFirst()
            val text = node.text?.toString()
            val described = node.contentDescription?.toString()
            if (!text.isNullOrBlank()) out += Seen(text, node.viewIdResourceName, node.isEditable)
            if (!described.isNullOrBlank() && described != text) out += Seen(described, node.viewIdResourceName)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return out
    }
}
