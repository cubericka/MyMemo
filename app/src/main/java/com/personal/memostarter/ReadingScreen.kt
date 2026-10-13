@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.personal.memostarter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable fun ReadingScreen(session: EditorSession,notes: List<Note>,busy: Boolean,onBack: ()->Unit,onEdit: ()->Unit,onOpen: (Long)->Unit) {
 val state by session.state.collectAsState();val d=state.draft
 var checks by rememberSaveable { mutableStateOf(false) };var children by rememberSaveable { mutableStateOf(false) }
 val subs=TreeRules.ordered(notes.filter { it.parentId==session.id && it.trashedAt==null })
 Scaffold(topBar={TopAppBar(title={Text("읽기 모드",style=MaterialTheme.typography.titleSmall)},navigationIcon={TextButton(onClick=onBack,enabled=!busy){Text("목록")}},actions={TextButton(onClick=onEdit,enabled=!busy){Text("수정")}})}) { padding ->
  LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   item { SelectionContainer { Column {
    Text(d.title.ifBlank { "제목 없음" },style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(20.dp));Text(d.body.ifBlank { "내용이 없습니다" },style=MaterialTheme.typography.bodyLarge)
   } } }
   items(d.details.filterValues { it.isNotBlank() }.toList(),key={"detail:${it.first}"}) { (label,value) -> SelectionContainer { Column { Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);Text(value,style=MaterialTheme.typography.bodyLarge) } } }
   item { HorizontalDivider();TextButton(onClick={checks=!checks}){Text("체크리스트 ${d.checks.count { it.done }}/${d.checks.size} · ${if(checks) "접기" else "펼치기"}")} }
   if(checks)items(d.checks,key={"check:${it.id}"}) { check -> Row { Checkbox(checked=check.done,onCheckedChange=null);SelectionContainer { Text(check.text,modifier=Modifier.padding(start=12.dp),style=MaterialTheme.typography.bodyLarge.copy(textDecoration=if(check.done)TextDecoration.LineThrough else TextDecoration.None)) } } }
   item { Spacer(Modifier.height(16.dp));HorizontalDivider();TextButton(onClick={children=!children}){Text("하위 메모 ${subs.size} · ${if(children) "접기" else "펼치기"}")} }
   if(children)items(subs,key={"child:${it.id}"}) { n -> OutlinedCard(onClick={onOpen(n.id)},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().heightIn(min=144.dp).padding(20.dp)) { Text(n.displayTitle,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(12.dp));Text(n.body,maxLines=3);Spacer(Modifier.height(12.dp));Text("읽기 모드로 열기",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary) } } }
  }
 }
}
