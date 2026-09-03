package com.example.microbiologicaldetection.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * Desde targetSdk 35 la app siempre dibuja bajo las barras del sistema, así que cada
 * pantalla tiene que reservar el espacio de los insets por su cuenta. Estos helpers
 * guardan el padding/margen original y le suman el inset correspondiente.
 */
fun View.applyInsetsPadding(
    top: Boolean = false,
    bottom: Boolean = false,
    horizontal: Boolean = true,
    ime: Boolean = false
) {
    val startLeft = paddingLeft
    val startTop = paddingTop
    val startRight = paddingRight
    val startBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val imeBottom = if (ime) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
        view.setPadding(
            startLeft + if (horizontal) bars.left else 0,
            startTop + if (top) bars.top else 0,
            startRight + if (horizontal) bars.right else 0,
            startBottom + if (bottom) maxOf(bars.bottom, imeBottom) else imeBottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** Igual que [applyInsetsPadding] pero sobre los márgenes (útil para FABs y overlays). */
fun View.applyInsetsMargin(
    top: Boolean = false,
    bottom: Boolean = false,
    horizontal: Boolean = true
) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val startLeft = lp.leftMargin
    val startTop = lp.topMargin
    val startRight = lp.rightMargin
    val startBottom = lp.bottomMargin

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = startLeft + if (horizontal) bars.left else 0
            topMargin = startTop + if (top) bars.top else 0
            rightMargin = startRight + if (horizontal) bars.right else 0
            bottomMargin = startBottom + if (bottom) bars.bottom else 0
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
