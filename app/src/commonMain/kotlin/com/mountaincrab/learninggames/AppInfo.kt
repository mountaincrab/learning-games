package com.mountaincrab.learninggames

/** App version metadata, supplied per-platform (Android reads BuildConfig). */
expect object AppInfo {
    val versionName: String
    val versionCode: Int
}
