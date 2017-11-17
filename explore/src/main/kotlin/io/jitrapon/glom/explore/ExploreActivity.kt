package io.jitrapon.glom.explore

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem

class ExploreActivity : BaseMainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.explore_activity)
    }

    override fun getSelfNavItem() = NavigationItem.EXPLORE
}
