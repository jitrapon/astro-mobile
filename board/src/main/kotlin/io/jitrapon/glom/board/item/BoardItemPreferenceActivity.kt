package io.jitrapon.glom.board.item

import android.app.Activity
import android.os.Bundle
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.util.addFragment
import io.jitrapon.glom.base.util.setupActionBar
import io.jitrapon.glom.board.Const
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.event.EventItemPreferenceFragment
import kotlinx.android.synthetic.main.board_item_preference_activity.*

/**
 * Fragment showing customization options for event items in a board
 *
 * Created by Jitrapon
 */
class BoardItemPreferenceActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.board_item_preference_activity)

        setupActionBar(board_item_preference_toolbar) {
            title = getString(R.string.board_item_preference_title)
            setDisplayHomeAsUpEnabled(true)
        }

        tag = "board_preference"
    }

    /**
     * Callback triggered when child fragment is initialized
     */
    override fun onCreateFragment(savedInstanceState: Bundle?) {
        val boardItemType = intent?.getIntExtra(Const.EXTRA_BOARD_ITEM_TYPE, -1)
        if (boardItemType == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        val fragmentValues = when (boardItemType) {
            BoardItem.TYPE_EVENT -> EventItemPreferenceFragment() to "event_item_preference"
            else -> throw IllegalArgumentException()
        }
        addFragment(R.id.board_item_preference_fragment_container, fragmentValues.first, fragmentValues.second)
    }
}
