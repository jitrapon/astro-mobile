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
    fun navigate(from: Context?, isInstantApp: Boolean, toModule: String?,
                 shouldFinish: Boolean = false, @Size(2) transitionAnimations: Array<Int>? = null) {
        toModule ?: return
        from?.let {
            if (isInstantApp) {
                it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://${it.getString(R.string.instant_app_host)}/${toModule.toLowerCase()}")).apply {
                    `package` = from.packageName
                    addCategory(Intent.CATEGORY_BROWSABLE)
                })
            }
            else {
                val module = toModule.toLowerCase()
                val className = "io.jitrapon.glom.$module.${module.capitalize()}Activity"
                try {
                    it.startActivity(Intent(it, Class.forName(className)))
                }
                catch (ex: Exception) {
                    AppLogger.w("Failed to launch module '$module'. Could not find class $className")
                }
            }
            if (shouldFinish) {
                if (from is Activity) from.finish()
                else if (from is androidx.fragment.app.Fragment) from.finish()
            }
            if (from is Activity) {
                transitionAnimations?.let {
                    from.overridePendingTransition(it[0], it[1])
                }
            }
        }
    }
}