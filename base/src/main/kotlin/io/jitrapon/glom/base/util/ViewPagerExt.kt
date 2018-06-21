package io.jitrapon.glom.base.util

import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.ViewGroup

/**
 * Extension functions to reduce ViewPager boilerplate code
 *
 * @author Jitrapon Tiachunpun
 */

fun ViewPager.createWithFragments(activity: AppCompatActivity, fragments: Array<Fragment>, titles: Array<String>? = null,
                     tabLayout: TabLayout? = null) {
    adapter = object : FragmentPagerAdapter(activity.supportFragmentManager) {

        override fun getItem(position: Int): Fragment = fragments[position]

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

inline fun <reified T> ViewPager.getFragmentAtPosition(fragmentManager: FragmentManager, position: Int): T? {
    return fragmentManager.findFragmentByTag("android:switcher:${this.id}:$position") as? T
}

inline fun ViewPager.doOnPageSelected(crossinline action: (position: Int) -> Unit) {
    this.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            action(position)
        }
    })
}

inline fun <reified T> ViewPager.doOnFragmentSelected(fm: FragmentManager, crossinline action: (fragment: T) -> Unit) {
    this.doOnPageSelected {
        this@doOnFragmentSelected.getFragmentAtPosition<T>(fm, it)?.let {
            action(it)
        }
    }
}
