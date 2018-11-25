package io.jitrapon.glom.base.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.Size
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.finish

/**
 * Helper class to wrap around all navigation logic
 *
 * @author Jitrapon Tiachunpun
 */
object Router {

    const val MODULE_BOARD = "board"
    const val MODULE_MAP = "map"
    const val MODULE_EXPLORE = "explore"
    const val MODULE_PROFILE = "profile"
    const val MODULE_AUTH = "auth"

    /**
     * Launch a specfic activity by specifying the module under which the activity belongs to
     */
    fun navigate(from: Activity?, isInstantApp: Boolean, toModule: String?,
                 shouldFinish: Boolean = false, @Size(2) transitionAnimations: Array<Int>? = null, requestCode: Int? = null) {
        toModule ?: return
        from?.let {
            if (isInstantApp) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://${it.getString(R.string.instant_app_host)}/${toModule.toLowerCase()}")).apply {
                    `package` = from.packageName
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                if (requestCode == null) it.startActivity(intent)
                else it.startActivityForResult(intent, requestCode)
            }
            else {
                val module = toModule.toLowerCase()
                val className = "io.jitrapon.glom.$module.${module.capitalize()}Activity"
                try {
                    val intent = Intent(it, Class.forName(className))
                    if (requestCode == null) it.startActivity(intent)
                    else it.startActivityForResult(intent, requestCode)
                }
                catch (ex: Exception) {
                    AppLogger.w("Failed to launch module '$module'. Could not find class $className")
                }
            }
            if (shouldFinish) {
                from.finish()
            }
            transitionAnimations?.let {
                from.overridePendingTransition(it[0], it[1])
            }
        }
    }
}
