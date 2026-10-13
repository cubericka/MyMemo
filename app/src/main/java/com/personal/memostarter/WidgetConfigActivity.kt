@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.personal.memostarter
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
class WidgetConfigActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); enableEdgeToEdge(); setResult(RESULT_CANCELED)
  val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID); val manager = AppWidgetManager.getInstance(this)
  if (id == AppWidgetManager.INVALID_APPWIDGET_ID || manager.getAppWidgetInfo(id)?.provider != ComponentName(this, MemoWidget::class.java)) { finish(); return }
  val app = application as MemoApp
  setContent { MemoTheme { IntegrityBoundary(app.integrity) {
   val notes by app.repository.notes.collectAsState(initial = emptyList()); var choice by remember { mutableStateOf(WidgetChoice()) }; var loaded by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }
   LaunchedEffect(id) { try { choice = WidgetSettings.get(this@WidgetConfigActivity, id); loaded = true } catch (e: Exception) { error = "설정을 불러오지 못했습니다. 닫고 다시 시도하세요." } }
   Scaffold(topBar = { TopAppBar(title = { Text("위젯 설정") }) }, bottomBar = { Button(onClick = {
    saving = true; lifecycleScope.launch { try { app.integrity.requireTrusted(); WidgetSettings.put(this@WidgetConfigActivity, id, choice); MemoWidget.updateAll(this@WidgetConfigActivity); setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish() } catch (e: Exception) { error = "설정 저장 또는 위젯 갱신 실패. 다시 시도하세요."; saving = false } }
   }, enabled = loaded && !saving, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { Text(if (saving) "적용 중…" else "적용") } }) { padding ->
    LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
     item {
      Text("글자 크기"); Row { listOf("작게", "보통", "크게").forEachIndexed { i, label -> FilterChip(selected = choice.size == i, enabled = loaded && !saving, onClick = { choice = choice.copy(size = i) }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp)) } }
      Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = choice.hide, enabled = loaded && !saving, onCheckedChange = { choice = choice.copy(hide = it) }); Spacer(Modifier.width(12.dp)); Text("개인정보 가리기") }
      Text("가리기는 위젯 표시만 숨깁니다. 메모 잠금 기능은 아닙니다.", style = MaterialTheme.typography.bodySmall)
      HorizontalDivider(Modifier.padding(vertical = 16.dp)); Text("표시할 메모 선택")
      ListItem(headlineContent = { Text("메모 미지정") }, leadingContent = { RadioButton(selected = choice.noteId == null, enabled = loaded && !saving, onClick = { choice = choice.copy(noteId = null) }) }, modifier = Modifier.clickable(enabled = loaded && !saving) { choice = choice.copy(noteId = null) })
      if (notes.none { it.trashedAt == null }) Text("메모가 없습니다. 앱에서 메모를 만든 뒤 선택하세요."); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
     }
     items(TreeRules.ordered(notes.filter { it.trashedAt == null }), key = { it.id }) { n -> ListItem(headlineContent = { Text(n.displayTitle) }, supportingContent = { if (n.parentId != null) Text(TreeRules.path(n.parentId, notes)) }, leadingContent = { RadioButton(selected = choice.noteId == n.id, enabled = loaded && !saving, onClick = { choice = choice.copy(noteId = n.id) }) }, modifier = Modifier.clickable(enabled = loaded && !saving) { choice = choice.copy(noteId = n.id) }) }
    }
   }
  } } }
 }
}
