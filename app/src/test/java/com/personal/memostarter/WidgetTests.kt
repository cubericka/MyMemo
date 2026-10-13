package com.personal.memostarter
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MemoApp::class)
class WidgetTests {
 @Test fun widgetChoicesAreIndependentAndRemappable() = runBlocking {
  val context = ApplicationProvider.getApplicationContext<Context>(); WidgetSettings.put(context, 101, WidgetChoice(7, 0, true)); WidgetSettings.put(context, 102, WidgetChoice(8, 2, false))
  assertEquals(WidgetChoice(7, 0, true), WidgetSettings.get(context, 101)); assertEquals(WidgetChoice(8, 2, false), WidgetSettings.get(context, 102)); WidgetSettings.remove(context, 101)
  assertEquals(WidgetChoice(), WidgetSettings.get(context, 101)); assertEquals(8L, WidgetSettings.get(context, 102).noteId); WidgetSettings.remap(context, intArrayOf(102), intArrayOf(103)); assertEquals(WidgetChoice(8, 2, false), WidgetSettings.get(context, 103)); WidgetSettings.remove(context, 103)
 }
}
