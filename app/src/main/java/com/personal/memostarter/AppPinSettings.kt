package com.personal.memostarter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable fun AppPinSettings(vm: MainViewModel) {
 val enabled by vm.appLock.enabled.collectAsState();val busy by vm.busy.collectAsState()
 var mode by remember { mutableStateOf("") }
 InkCard {
  Text("앱 공통 PIN",style=MaterialTheme.typography.titleMedium)
  Text(if(enabled) "앱 전체에 숫자 4자리 PIN이 적용되어 있습니다." else "메모별 PIN 대신 앱 전체에 같은 PIN을 적용합니다.")
  if(enabled) { TextButton(onClick=vm::lockAppNow,enabled=!busy){Text("지금 잠그기")};TextButton(onClick={mode="change"},enabled=!busy){Text("앱 PIN 변경")};TextButton(onClick={mode="disable"},enabled=!busy){Text("앱 잠금 사용 해제")} }
  else Button(onClick={mode="enable"},enabled=!busy){Text("앱 PIN 설정")}
  Text("잠금 사용 중에는 위젯 내용이 숨겨집니다. 기존 개별 잠금 메모는 이전 PIN을 한 번 확인하면 이후 공통 PIN으로 열립니다.",style=MaterialTheme.typography.bodySmall)
 }
 if(mode=="enable")PinDialog(true,busy,{mode=""},{pin->mode="";vm.enableAppPin(pin)},title="앱 PIN 설정",description="앱 전체를 보호할 숫자 4자리를 입력하세요. PIN을 잊으면 복구할 수 없습니다.")
 if(mode=="disable")PinDialog(false,busy,{mode=""},{pin->mode="";vm.disableAppPin(pin)},title="앱 잠금 사용 해제",description="현재 앱 PIN을 확인합니다. 해제 후에는 앱을 PIN 없이 열 수 있습니다.")
 if(mode=="change") {
  var old by remember { mutableStateOf("") };var new by remember { mutableStateOf("") };var again by remember { mutableStateOf("") }
  AlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={if(!busy)mode=""},title={Text("앱 PIN 변경")},text={Column {
   @Composable fun field(label: String,value: String,set: (String)->Unit) { OutlinedTextField(value,{if(it.length<=4 && it.all { c->c in '0'..'9' })set(it)},label={Text(label)},enabled=!busy,singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth().padding(top=8.dp)) }
   field("현재 PIN",old){old=it};field("새 PIN",new){new=it};field("새 PIN 확인",again){again=it}
  }},confirmButton={TextButton(enabled=!busy && old.length==4 && new.length==4 && new==again,onClick={vm.changeAppPin(old,new);mode=""}){Text("변경")}},dismissButton={TextButton(onClick={mode=""},enabled=!busy){Text("취소")}})
 }
}
