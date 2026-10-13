package com.personal.memostarter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable fun PinDialog(setting: Boolean,busy: Boolean,onDismiss: () -> Unit,onSubmit: (String) -> Unit,title: String? = null,description: String? = null) {
 var pin by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") }
 AlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={ if(!busy)onDismiss() },title={Text(title ?: if(setting) "메모 비밀번호 설정" else "잠긴 메모 열기")},text={Column {
  Text(description ?: if(setting) "이 메모와 하위 메모를 함께 보호합니다. 비밀번호를 잊으면 복구할 수 없습니다." else "상위 메모에 설정한 숫자 4자리를 입력하세요.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(pin,{ if(it.length<=4 && it.all { c -> c in '0'..'9' })pin=it },label={Text("비밀번호 4자리")},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth().padding(top=12.dp))
  if(setting)OutlinedTextField(confirm,{ if(it.length<=4 && it.all { c -> c in '0'..'9' })confirm=it },label={Text("비밀번호 확인")},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth().padding(top=8.dp))
 }},confirmButton={TextButton(enabled=!busy && pin.length==4 && (!setting || pin==confirm),onClick={val value=pin;pin="";confirm="";onSubmit(value)}){Text(if(setting) "잠금 설정" else "열기")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("취소")}})
}
