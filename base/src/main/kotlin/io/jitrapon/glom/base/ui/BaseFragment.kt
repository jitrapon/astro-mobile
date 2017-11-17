package io.jitrapon.glom.base.ui

import android.support.v4.app.Fragment

/**
 * All fragments should extend from this base class for convenience functions (including analytics).
 * This fragment automatically derives from LifeCycleOwner. Convenience wrapper function
 * checks for null Activity instance for when this Fragment instance is no longer attached to the
 * host activity. This avoids NullPointerExceptions occurring.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseFragment : Fragment() {

}
