package io.jitrapon.glom.base.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.annotation.Size
import android.support.v4.app.Fragment
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.finish

/**
 * Helper class to wrap around navigation logic for Instant App in different modules
 *
 * @author Jitrapon Tiachunpun
 */
object Router {

    /**
     * Launch a specfic activity by specifying the module under which the activity belongs to
     */
    fun navigate(from: Context?, toModule: String?, shouldFinish: Boolean = false, @Size(2) transitionAnimations: Array<Int>? = null) {
        from?.let {
            it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://${it.getString(R.string.instant_app_host)}/${toModule?.toLowerCase()}")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
            if (shouldFinish) {
                if (from is Activity) {
                    from.finish()
                    transitionAnimations?.let {
                        from.overridePendingTransition(it[0], it[1])
                    }
                }
                else if (from is Fragment) from.finish()
            }
        }
    }
}