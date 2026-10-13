package com.personal.memostarter
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.widget.RemoteViews
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
class MemoWidget : AppWidgetProvider() {
 override fun onReceive(context: Context, intent: Intent) {
  super.onReceive(context, intent); val pending = goAsync()
  (context.applicationContext as MemoApp).scope.launch {
   try {
    if (!(context.applicationContext as MemoApp).integrity.refresh()) { updateAll(context); return@launch }
    if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.forEach { WidgetSettings.remove(context, it) } ?: intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1).takeIf { it >= 0 }?.let { WidgetSettings.remove(context, it) }
    if (intent.action == AppWidgetManager.ACTION_APPWIDGET_RESTORED) WidgetSettings.remap(context, intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS) ?: intArrayOf(), intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf())
    updateAll(context)
   } catch (e: Exception) { if (BuildConfig.DEBUG) android.util.Log.w("MyMemoWidget", "Widget refresh failed; note data unchanged", e) } finally { pending.finish() }
  }
 }
 companion object {
  private val updates = Mutex()
  fun requestUpdate(context: Context) { (context.applicationContext as MemoApp).scope.launch { runCatching { updateAll(context) } } }
  suspend fun updateAll(context: Context) = updates.withLock {
   val manager = AppWidgetManager.getInstance(context); val ids = manager.getAppWidgetIds(ComponentName(context, MemoWidget::class.java)); if (ids.isEmpty()) return@withLock
   val guard = (context.applicationContext as MemoApp).integrity
   if (!guard.refresh()) {
    for (id in ids) { val blocked = RemoteViews(context.packageName, R.layout.memo_widget); blocked.setTextViewText(R.id.widget_title, "앱 보호 확인 실패"); blocked.setTextViewText(R.id.widget_body, "메모는 보존됩니다. 공식 APK로 복구하세요."); manager.updateAppWidget(id, blocked) }
    return@withLock
   }
   val (notes, checks) = (context.applicationContext as MemoApp).repository.snapshot()
   for (id in ids) {
    val choice = WidgetSettings.get(context, id); val note = notes.find { it.id == choice.noteId && it.trashedAt == null }; val view = RemoteViews(context.packageName, R.layout.memo_widget)
    val title = when { choice.noteId == null -> "My Memo"; note == null -> "메모를 사용할 수 없음"; note.lockRootId != null -> "잠긴 메모"; choice.hide -> "비공개 메모"; else -> note.displayTitle }
    val ownChecks = checks.filter { it.noteId == note?.id }
    val preview = buildList { if (!note?.body.isNullOrBlank()) add(note!!.body); addAll(ownChecks.map { (if (it.done) "☑ " else "☐ ") + it.text }) }.joinToString("\n")
    val body = when { choice.noteId == null -> "표시할 메모 선택"; note == null -> "설정에서 메모를 다시 선택하세요"; note.lockRootId != null -> "앱에서 비밀번호를 입력해 확인하세요"; choice.hide -> "내용을 보려면 누르세요"; else -> preview.ifBlank { "내용 없음" } }
    view.setTextViewText(R.id.widget_title, title); view.setTextViewText(R.id.widget_body, body)
    val font = listOf(11f, 13f, 16f)[choice.size.coerceIn(0, 2)]; view.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, font + 2); view.setTextViewTextSize(R.id.widget_body, TypedValue.COMPLEX_UNIT_SP, font)
    val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 48)
    view.setInt(R.id.widget_body, "setMaxLines", ((height - 36) / (font * 1.35f)).toInt().coerceIn(1, 12))
    fun pending(action: String, intent: Intent): PendingIntent { intent.data = Uri.parse("mymemo://widget/$id/$action"); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP); return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
    val config = Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    view.setOnClickPendingIntent(R.id.widget_settings, pending("settings", config))
    view.setOnClickPendingIntent(R.id.widget_content, if (note == null) pending("choose", Intent(config)) else pending("open", Intent(context, MainActivity::class.java).putExtra("note_id", note.id)))
    view.setOnClickPendingIntent(R.id.widget_add, pending("new", Intent(context, MainActivity::class.java).putExtra("new_note", true)))
    runCatching { manager.updateAppWidget(id, view) }
   }
  }
 }
}
