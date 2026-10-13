@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.personal.memostarter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable fun SettingsScreen(vm: MainViewModel,notes: List<Note>,onBack: ()->Unit,onTrash: ()->Unit,onAbout: ()->Unit) {
 val busy by vm.busy.collectAsState()
 var change by remember { mutableStateOf<Note?>(null) }
 Scaffold(topBar={TopAppBar(title={Text("설정")},navigationIcon={TextButton(onClick=onBack,enabled=!busy){Text("뒤로")}})}) { padding ->
  LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item { HorizontalDivider();Text("메모 비밀번호 변경",style=MaterialTheme.typography.titleMedium)
    Text("변경할 최상위 메모를 선택하세요. 잠긴 제목은 인증 전까지 숨깁니다.",style=MaterialTheme.typography.bodySmall) }
   val locked=notes.filter { it.id==it.lockRootId }.sortedBy { it.id }
   if(locked.isEmpty())item { Text("비밀번호를 설정한 메모가 없습니다.") }
   items(locked,key={it.id}) { note -> OutlinedButton(onClick={change=note},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("${note.displayTitle} · #${note.id}"+if(note.trashedAt!=null) " (휴지통)" else "") } }
   item { HorizontalDivider();TextButton(onClick=onTrash,enabled=!busy){Text("휴지통")};TextButton(onClick=onAbout,enabled=!busy){Text("앱 정보 및 사용 안내")} }
  }
 }
 change?.let { note -> ChangePinDialog(busy,{change=null}) { old,new -> change=null;vm.changePin(note.id,old,new) } }
}

@Composable private fun ChangePinDialog(busy: Boolean,dismiss: ()->Unit,submit: (String,String)->Unit) {
 var old by remember { mutableStateOf("") };var fresh by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") }
 AlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={if(!busy)dismiss()},title={Text("비밀번호 변경")},text={Column {
  Text("기존 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.")
  @Composable fun field(label: String,value: String,set: (String)->Unit) { OutlinedTextField(value,{if(it.length<=4 && it.all { c->c in '0'..'9' })set(it)},label={Text(label)},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth().padding(top=8.dp)) }
  field("기존 비밀번호",old){old=it};field("새 비밀번호 4자리",fresh){fresh=it};field("새 비밀번호 확인",confirm){confirm=it}
 }},confirmButton={TextButton(enabled=!busy && old.length==4 && fresh.length==4 && fresh==confirm,onClick={submit(old,fresh);old="";fresh="";confirm=""}){Text("변경")}},dismissButton={TextButton(enabled=!busy,onClick=dismiss){Text("취소")}})
}
