package com.mountaincrab.learninggames

import androidx.compose.runtime.Composable

/**
 * Hooks the platform's system "back" gesture/button. On Android this delegates to
 * Compose's BackHandler; other platforms can no-op or provide their own behaviour.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
