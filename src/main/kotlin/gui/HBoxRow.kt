package gui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.layout.HBox

class HBoxRow: HBox {

    constructor() : super() {
        setItemSpacing(10.0)
    }

    constructor(spacing: Double) : super(spacing){
        setItemSpacing(spacing)
    }
    constructor(vararg children: Node?) : super(*children){
        setItemSpacing(10.0)
    }
    constructor(spacing: Double, vararg children: Node?) : super(spacing, *children){
        setItemSpacing(spacing)
    }

    /**
     * Defines the spacing and padding for items in rows
     */
    private fun setItemSpacing(spacing: Double) {
        this.spacing = spacing
        this.padding = Insets(10.0, 10.0, 10.0, 10.0)
        this.alignment = Pos.CENTER_LEFT
    }
}
