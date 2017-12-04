package io.jitrapon.glom.profile

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.ui.widget.bottomnav.BadgedBottomNavigationView
import kotlinx.android.synthetic.main.profile_activity.*

class ProfileActivity : BaseMainActivity() {

    override fun onCreateViewModel() {
        //TODO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_activity)

        tag = "profile"
    }

    override fun getBottomNavBar() = profile_bottom_navigation as BadgedBottomNavigationView

    override fun getSelfNavItem() = NavigationItem.PROFILE
}
