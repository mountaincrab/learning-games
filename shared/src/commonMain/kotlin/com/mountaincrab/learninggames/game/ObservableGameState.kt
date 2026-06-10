package com.mountaincrab.learninggames.game

import kotlin.js.JsExport

/**
 * Base for the shared (Compose-free, React-free) game state holders. Platforms that
 * need to react to mutations hook [onChange]: the Android screens bump a Compose
 * state to trigger recomposition; the webapp redraws every frame anyway and can
 * ignore it.
 */
@JsExport
abstract class ObservableGameState {
    var onChange: (() -> Unit)? = null

    protected fun notifyChanged() {
        onChange?.invoke()
    }
}
