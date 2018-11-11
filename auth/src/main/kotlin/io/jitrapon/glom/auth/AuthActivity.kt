package io.jitrapon.glom.auth

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.lifecycle.Observer
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.util.Transformation
import io.jitrapon.glom.base.util.color
import io.jitrapon.glom.base.util.loadFromUrl
import io.jitrapon.glom.base.util.obtainViewModel
import kotlinx.android.synthetic.main.auth_activity.*

/**
 * This activity is the main entry to the auth screen. Supports login and sign up flow.
 *
 * Created by Jitrapon
 */
class AuthActivity : BaseActivity() {

    private lateinit var viewModel: AuthViewModel

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.auth_activity)
    }

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(AuthViewModel::class.java)
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.observableViewAction)

        viewModel.getObservableBackground().observe(this@AuthActivity, Observer {
                auth_scrolling_background.loadFromUrl(this@AuthActivity, it, null, null,
                        ColorDrawable(color(R.color.white)),
                        Transformation.CENTER_CROP,
                        800)
        })
    }

    //endregion
}