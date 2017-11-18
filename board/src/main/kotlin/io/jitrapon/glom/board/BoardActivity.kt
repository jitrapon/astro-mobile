package io.jitrapon.glom.board

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.util.obtainViewModel
import kotlinx.android.synthetic.main.board_activity.*

/**
 * Subclass of the MainActivity. Board activity is the default entry point to the application.
 *
 * @author Jitrapon Tiachunpun
 */
class BoardActivity : BaseMainActivity() {

    private lateinit var viewModel: BoardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.board_activity)

        tag = "board"
        setupView()
    }

    override fun getSelfNavItem() = NavigationItem.BOARD

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(BoardViewModel::class.java)
    }

    override fun subscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())
    }

    private fun setupView() {
        test_button.setOnClickListener {
            viewModel.loadBoard()
        }
    }
}
