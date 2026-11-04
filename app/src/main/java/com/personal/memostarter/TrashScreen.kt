package com.personal.memostarter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun TrashScreen(notes: List<Note>,busy: Boolean,onBack: ()->Unit,onDelete: (Set<Long>)->Unit) {
 val deleted=notes.filter { it.trashedAt!=null }.sortedByDescending { it.trashedAt }
 var selected by rememberSaveable { mutableStateOf(longArrayOf()) };var pending by remember { mutableStateOf<Set<Long>?>(null) }
 LaunchedEffect(notes) { selected=selected.filter { id->deleted.any { it.id==id } }.toLongArray() }
 fun toggle(id: Long) { selected=if(id in selected)selected.filter { it!=id }.toLongArray() else selected+id }
 Scaffold(topBar={InkTopAppBar(title={Text("휴지통")},navigationIcon={TextButton(onClick=onBack,enabled=!busy){Text("목록")}})}) { padding ->
  LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   item {
    Text("휴지통에서는 복원할 수 없습니다. 영구 삭제 전까지 메모를 보관하며 자동 삭제하지 않습니다.")
    Row { TextButton(enabled=!busy && selected.isNotEmpty(),onClick={pending=selected.toSet()}) { Text("선택 삭제 (${selected.size})") }
     Spacer(Modifier.weight(1f));TextButton(enabled=!busy && deleted.isNotEmpty(),onClick={pending=deleted.map { it.id }.toSet()}){Text("전체 비우기")} }
   }
   if(deleted.isEmpty())item { Text("휴지통이 비어 있어요") }
   items(deleted,key={it.id}) { n -> OutlinedCard(Modifier.fillMaxWidth().clickable(enabled=!busy){toggle(n.id)}) {
    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Checkbox(checked=n.id in selected,onCheckedChange={toggle(n.id)},enabled=!busy);Text(n.displayTitle,modifier=Modifier.weight(1f));TextButton(enabled=!busy,onClick={pending=setOf(n.id)}) { Text("영구 삭제") } }
   } }
  }
 }
 pending?.let { ids -> AlertDialog(onDismissRequest={if(!busy)pending=null},title={Text("영구 삭제할까요?")},text={Text("선택한 ${ids.size}개 항목과 해당 하위 메모를 영구 삭제합니다. 삭제 후에는 되돌릴 수 없습니다.")},confirmButton={TextButton(enabled=!busy,onClick={onDelete(ids);selected=longArrayOf();pending=null}){Text("영구 삭제 확인")}},dismissButton={TextButton(enabled=!busy,onClick={pending=null}){Text("취소")}}) }
}
