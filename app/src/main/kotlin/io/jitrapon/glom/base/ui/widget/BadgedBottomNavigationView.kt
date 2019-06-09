package io.jitrapon.glom.base.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.jitrapon.glom.R

/**
 * A (rough and ready) extension to [BottomNavigationView] which shows a badge over a menu
 * item to indicate new content therein.
 *
 * Credit: https://github.com/google/iosched/blob/master/lib/src/main/java/com/google/samples/apps/iosched/ui/widget/BadgedBottomNavigationView.java
 *
 * @author Jitrapon Tiachunpun
 */
class BadgedBottomNavigationView(context: Context, attrs: AttributeSet) : BottomNavigationView(context, attrs) {

    companion object {

        private const val NO_BADGE_POSITION = -1
    }

    private var badgePosition = NO_BADGE_POSITION
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radius: Float
    private var listener: BottomNavigationView.OnNavigationItemSelectedListener? = null
    private val drawListener = ViewTreeObserver.OnDrawListener {
        if (badgePosition > NO_BADGE_POSITION) {
            postInvalidateOnAnimation()
        }
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.BadgedBottomNavigationView)
        badgePaint.color = a.getColor(R.styleable.BadgedBottomNavigationView_badgeColor, Color.TRANSPARENT)
        radius = a.getDimension(R.styleable.BadgedBottomNavigationView_badgeRadius, 0f)
        a.recycle()

        // we need to listen to tab selection to re-position the badge so set our own listener
        // wrapping any provided listener.
        super.setOnNavigationItemSelectedListener { item ->
            if (badgePosition > NO_BADGE_POSITION) {
                // use a pre-draw listener to invalidate ourselves when the badged item moves
                viewTreeObserver.addOnDrawListener(drawListener)
            }
            listener?.onNavigationItemSelected(item) ?: false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (badgePosition > NO_BADGE_POSITION) {
            val menuView = getChildAt(0) as ViewGroup? ?: return
            val menuItem = menuView.getChildAt(badgePosition) as ViewGroup? ?: return
            val icon = menuItem.getChildAt(0) ?: return

            val cx = (menuView.left + menuItem.right - icon.left).toFloat()
            canvas.drawCircle(cx, icon.top.toFloat(), radius, badgePaint)
        }
    }

    override fun setOnNavigationItemSelectedListener(
            listener: BottomNavigationView.OnNavigationItemSelectedListener?) {
        this.listener = listener
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnDrawListener(drawListener)
        super.onDetachedFromWindow()
    }

    //region exposed functions

    fun clearBadge() {
        badgePosition = NO_BADGE_POSITION
        invalidate()
    }

    //endregion
}
