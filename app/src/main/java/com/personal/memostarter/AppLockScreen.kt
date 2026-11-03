package com.personal.memostarter
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun AppLockBoundary(app: MemoApp,content: @Composable ()->Unit) {
 val open by app.appLock.unlocked.collectAsState();val enabled by app.appLock.enabled.collectAsState()
 val activity=LocalContext.current as? Activity
 SideEffect { if(enabled)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
 if(open)content() else {
  var pin by remember { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) };var busy by remember { mutableStateOf(false) };val scope=rememberCoroutineScope()
  Scaffold(topBar={InkTopAppBar(title={Text("앱 잠금")})}) { padding ->
   Column(Modifier.padding(padding).fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
    Text("My Memo 드래그",style=MaterialTheme.typography.headlineSmall)
    Text("앱 공통 PIN을 입력하세요. 기존 메모는 그대로 보관됩니다.")
    OutlinedTextField(pin,{if(it.length<=4 && it.all { c->c in '0'..'9' })pin=it},label={Text("앱 PIN 4자리")},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth().padding(vertical=16.dp))
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    Button(enabled=!busy && pin.length==4,onClick={val entered=pin;pin="";busy=true;scope.launch { try { withContext(Dispatchers.IO) { app.appLock.unlock(entered) } } catch(e: Exception) { error=e.message ?: "잠금을 해제하지 못했습니다" } finally { busy=false } }}) { Text("앱 잠금 해제") }
   }
  }
 }
}
