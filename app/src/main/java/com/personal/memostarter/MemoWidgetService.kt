package com.personal.memostarter

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking

internal data class WidgetEntry(val id: Long, val title: String, val body: String)
internal fun widgetEntries(choice: WidgetChoice, notes: List<Note>, checks: List<CheckItem>): List<WidgetEntry> =
 choice.noteIds.mapNotNull { id ->
  notes.find { it.id == id && it.trashedAt == null }?.let { n ->
   when {
    n.lockRootId != null -> WidgetEntry(id, "잠긴 메모", "앱에서 비밀번호를 입력해 확인하세요")
    choice.hide -> WidgetEntry(id, "비공개 메모", "내용을 보려면 누르세요")
    else -> WidgetEntry(id, n.displayTitle, (listOf(n.body).filter { it.isNotBlank() } + checks.filter { it.noteId==id }.map { (if(it.done) "☑ " else "☐ ") + it.text }).joinToString("\n").ifBlank { "내용 없음" })
   }
  }
 }
class MemoWidgetService : RemoteViewsService() {
 override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
  val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
  var entries = emptyList<WidgetEntry>()
  var choice = WidgetChoice()
  override fun onCreate() {}
  override fun onDataSetChanged() {
   entries = emptyList()
   runCatching { runBlocking {
    val app = applicationContext as MemoApp
    if(!app.appLock.enabled.value && app.integrity.refresh()) {
     choice = WidgetSettings.get(app,id)
     val (notes,checks) = app.repository.snapshot()
     entries = widgetEntries(choice,notes,checks)
    }
   } }
  }
  override fun onDestroy() { entries = emptyList() }
  override fun getCount() = entries.size
  override fun getViewAt(position: Int): RemoteViews? {
   val entry = entries.getOrNull(position) ?: return null
   if((applicationContext as MemoApp).appLock.enabled.value)return RemoteViews(packageName,R.layout.memo_widget_item).apply { setTextViewText(R.id.item_title,"앱 잠금 사용 중");setTextViewText(R.id.item_body,"앱에서 PIN을 입력하세요") }
   return RemoteViews(packageName, R.layout.memo_widget_item).apply {
    setTextViewText(R.id.item_title,entry.title); setTextViewText(R.id.item_body,entry.body)
    val font = listOf(11f,13f,16f)[choice.size.coerceIn(0,2)]
    setTextViewTextSize(R.id.item_title,TypedValue.COMPLEX_UNIT_SP,font+2)
    setTextViewTextSize(R.id.item_body,TypedValue.COMPLEX_UNIT_SP,font)
    setOnClickFillInIntent(R.id.item_root, Intent().putExtra("note_id",entry.id))
   }
  }
  override fun getLoadingView(): RemoteViews? = null
  override fun getViewTypeCount() = 1
  override fun getItemId(position: Int) = entries.getOrNull(position)?.id ?: -1L
  override fun hasStableIds() = true
 }
}
