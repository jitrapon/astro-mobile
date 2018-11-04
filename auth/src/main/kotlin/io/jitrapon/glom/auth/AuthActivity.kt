package io.jitrapon.glom.auth

import android.os.Bundle
import io.jitrapon.glom.base.ui.BaseActivity

/**
 * Created by Jitrapon
 */
class AuthActivity : BaseActivity() {

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.auth_activity)
    }

    //endregion
}