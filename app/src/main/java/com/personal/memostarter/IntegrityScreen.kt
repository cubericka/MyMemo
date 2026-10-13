package com.personal.memostarter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable fun IntegrityBoundary(guard: IntegrityGuard, content: @Composable () -> Unit) {
 val status by guard.status.collectAsState()
 var entered by rememberSaveable { mutableStateOf(false) }
 val scope = rememberCoroutineScope()
 LaunchedEffect(Unit) { guard.refresh() }
 LaunchedEffect(status) { if (status == "OK") entered = true }
 Column(Modifier.fillMaxSize()) {
  if (status != "OK") {
   Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.statusBarsPadding().padding(16.dp)) {
     Text(if (status == "CHECKING") "앱 확인 중…" else "앱 보호 확인 실패", style = MaterialTheme.typography.titleMedium)
     if (status != "CHECKING") {
      Text("저장·삭제 등 변경 작업을 제한합니다. 기존 메모는 삭제하지 않았습니다. 작성 중인 내용은 복사해 보관하세요. 앱 삭제 없이 공식 APK로 복구해 주세요. ($status)", style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = { scope.launch { guard.refresh() } }) { Text("다시 확인") }
     }
    }
   }
  }
  // Keep the live editor mounted on a mid-session failure: unsaved text remains copyable.
  if (entered || status == "OK") Box(Modifier.weight(1f)) { content() }
 }
}
