package io.jitrapon.glom.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.instantapps.InstantApps
import io.jitrapon.glom.base.navigation.Router
import io.jitrapon.glom.base.navigation.Router.MODULE_BOARD

/**
 * Launcher activity that handles any deep links or any other operations
 * before going into the main content
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // redirect to one of the BaseMainActivity classes
        Router.navigate(this, InstantApps.isInstantApp(this), MODULE_BOARD, true)
    }
}
