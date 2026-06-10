package com.mountaincrab.learninggames.game.shapegame

import kotlin.js.JsExport

/** The shapes a child matches in the Magic Hat game. There are twelve so a full
 * game shows every shape once, in a randomised arrangement.
 *
 * Enums can't be `@JsExport`ed, so the web side sees shapes as their [name] strings
 * (via [shapeTypeNames]/[shapeFillColorCss]); Kotlin callers use the enum directly. */
enum class ShapeType(val displayName: String, val fillColorArgb: Int) {
    CIRCLE("circle", 0xFFFFD23F.toInt()),
    SQUARE("square", 0xFF4ECDC4.toInt()),
    TRIANGLE("triangle", 0xFF6FBF3B.toInt()),
    PENTAGON("pentagon", 0xFF9B5DE5.toInt()),
    DIAMOND("diamond", 0xFFFF6B6B.toInt()),
    RECTANGLE("rectangle", 0xFF577590.toInt()),
    DOME("arch", 0xFFF4A261.toInt()),
    STAR("star", 0xFFFFB703.toInt()),
    HEXAGON("hexagon", 0xFF00BBF9.toInt()),
    HEART("heart", 0xFFF15BB5.toInt()),
    OVAL("oval", 0xFF80ED99.toInt()),
    CROSS("cross", 0xFFE07A5F.toInt()),
}

/** All shape names, in declaration order. */
@JsExport
fun shapeTypeNames(): Array<String> = ShapeType.entries.map { it.name }.toTypedArray()

/** Fill colour for a shape as a CSS hex string, e.g. `#FFD23F`. */
@JsExport
fun shapeFillColorCss(typeName: String): String {
    val rgb = ShapeType.valueOf(typeName).fillColorArgb and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0').uppercase()
}
