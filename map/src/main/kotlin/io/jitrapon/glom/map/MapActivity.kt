package io.jitrapon.glom.map

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem

class MapActivity : BaseMainActivity() {

    override fun onCreateViewModel() {
        //TODO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_activity)

        tag = "map"
    }

    override fun getSelfNavItem() = NavigationItem.MAP
}
