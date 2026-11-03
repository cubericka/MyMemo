package com.personal.memostarter
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=MemoApp::class)
class AppPinWidgetTests {
 @Test fun commonPinHidesEvenPreviouslyCachedWidgetRows()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<MemoApp>()
  val id=app.repository.create(null);app.repository.save(id,Draft("민감한 제목","민감한 본문"))
  WidgetSettings.put(app,101,WidgetChoice().withNotes(listOf(id)))
  val controller=Robolectric.buildService(MemoWidgetService::class.java).create()
  val factory=controller.get().onGetViewFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,101))
  try {
   factory.onDataSetChanged();assertEquals(1,factory.count)
   app.appLock.enabled.value=true
   val rendered=factory.getViewAt(0)!!.apply(app,null)
   assertEquals("앱 잠금 사용 중",rendered.findViewById<TextView>(R.id.item_title).text.toString())
   assertEquals("앱에서 PIN을 입력하세요",rendered.findViewById<TextView>(R.id.item_body).text.toString())
   factory.onDataSetChanged();assertEquals(0,factory.count)
  } finally { factory.onDestroy();controller.destroy();app.appLock.enabled.value=false;app.database.close() }
 }
}
