package io.jitrapon.glom.base.util

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout

/**
 * Extension functions to reduce ViewPager boilerplate code
 *
 * @author Jitrapon Tiachunpun
 */

fun androidx.viewpager.widget.ViewPager.createWithFragments(activity: AppCompatActivity, fragments: Array<androidx.fragment.app.Fragment>, titles: Array<String>? = null,
                                                            tabLayout: TabLayout? = null) {
    adapter = object : androidx.fragment.app.FragmentPagerAdapter(activity.supportFragmentManager) {

        override fun getItem(position: Int): androidx.fragment.app.Fragment = fragments[position]

        override fun getCount(): Int = fragments.size

        override fun getItemPosition(`object`: Any): Int = fragments.indexOf(`object`).let {
            when (it) {
                -1 -> POSITION_NONE
                else -> it
            }
        }

        override fun getPageTitle(position: Int): CharSequence? = titles?.get(position)

        override fun finishUpdate(container: ViewGroup) {
            try {
                super.finishUpdate(container)
            }
            catch (ex: Exception) {
                AppLogger.e(ex)
            }
        }
    }
    tabLayout?.setupWithViewPager(this)
}

inline fun <reified T> androidx.viewpager.widget.ViewPager.getFragmentAtPosition(fragmentManager: androidx.fragment.app.FragmentManager, position: Int): T? {
    return fragmentManager.findFragmentByTag("android:switcher:${this.id}:$position") as? T
}

inline fun androidx.viewpager.widget.ViewPager.doOnPageSelected(crossinline action: (position: Int) -> Unit) {
    this.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            action(position)
        }
    })
}

inline fun <reified T> androidx.viewpager.widget.ViewPager.doOnFragmentSelected(fm: androidx.fragment.app.FragmentManager, crossinline action: (fragment: T) -> Unit) {
    this.doOnPageSelected { page ->
        this@doOnFragmentSelected.getFragmentAtPosition<T>(fm, page)?.let {
            action(it)
        }
    }
}
