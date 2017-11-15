package io.jitrapon.glom.base

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.jitrapon.glom.R

/**
 * Helper class to wrap around navigation logic for Instant App in different modules
 *
 * @author Jitrapon Tiachunpun
 */
object InstantAppRouter {

    /**
     * Launch a specfic activity by specifying the module under which the activity belongs to
     */
    fun navigateTo(context: Context?, moduleName: String) {
        context?.let {
            it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://${it.getString(R.string.instant_app_host)}/${moduleName.toLowerCase()}")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
        }
    }
}