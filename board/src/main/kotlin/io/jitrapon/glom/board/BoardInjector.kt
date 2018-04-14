package io.jitrapon.glom.board

import io.jitrapon.glom.base.di.ObjectGraph

/**
 * Caller must explicitly clear compoonent
 *
* Created by Jitrapon
*/
object BoardInjector {

    private var boardComponent: BoardComponent? = null

    fun getComponent(): BoardComponent {
        if (boardComponent == null) {
            boardComponent = DaggerBoardComponent.builder()
                    .baseComponent(ObjectGraph.component)
                    .build()
        }
        return boardComponent!!
    }

    fun clear() {
        boardComponent = null
    }
}