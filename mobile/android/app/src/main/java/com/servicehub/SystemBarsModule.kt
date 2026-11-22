package com.servicehub

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Hides the phone's own navigation bar (back / home / recents) while the web app's side menu is open,
 * so the bottom of the menu is not covered. Hidden "transiently": a swipe from the bottom edge still
 * reveals it, so the user is never trapped.
 */
class SystemBarsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "ServiceHubSystemBars"

  @ReactMethod
  fun setNavigationBarHidden(hidden: Boolean) {
    val activity = reactContext.currentActivity ?: return
    activity.runOnUiThread {
      val window = activity.window ?: return@runOnUiThread
      val controller = WindowCompat.getInsetsController(window, window.decorView)
      if (hidden) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
      } else {
        controller.show(WindowInsetsCompat.Type.navigationBars())
      }
    }
  }
}
