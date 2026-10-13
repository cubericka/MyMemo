package com.personal.memostarter
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
private val Context.widgetData by preferencesDataStore("widget_settings")
data class WidgetChoice(val noteId: Long? = null, val size: Int = 1, val hide: Boolean = false)
object WidgetSettings {
 private fun note(id: Int) = longPreferencesKey("widget_${id}_note")
 private fun size(id: Int) = intPreferencesKey("widget_${id}_size")
 private fun hide(id: Int) = booleanPreferencesKey("widget_${id}_hide")
 suspend fun get(context: Context, id: Int): WidgetChoice { val p = context.applicationContext.widgetData.data.first(); return WidgetChoice(p[note(id)], p[size(id)] ?: 1, p[hide(id)] ?: false) }
 suspend fun put(context: Context, id: Int, choice: WidgetChoice) { context.applicationContext.widgetData.edit { p -> if (choice.noteId == null) p.remove(note(id)) else p[note(id)] = choice.noteId; p[size(id)] = choice.size.coerceIn(0, 2); p[hide(id)] = choice.hide } }
 suspend fun remove(context: Context, id: Int) { context.applicationContext.widgetData.edit { it.remove(note(id)); it.remove(size(id)); it.remove(hide(id)) } }
 suspend fun remap(context: Context, old: IntArray, new: IntArray) { val choices = old.map { get(context, it) }; old.forEach { remove(context, it) }; new.forEachIndexed { i, id -> choices.getOrNull(i)?.let { put(context, id, it) } } }
}
