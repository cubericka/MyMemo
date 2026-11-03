package com.personal.memostarter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
 private val vm: MainViewModel by viewModels()
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)); setContent { MemoTheme { AppLockBoundary(application as MemoApp) { IntegrityBoundary((application as MemoApp).integrity) { MemoScreen(vm) } } } }; lifecycleScope.launch { (application as MemoApp).appLock.unlocked.collectLatest { if(it)vm.onForeground() } }; if (savedInstanceState == null) route(intent) }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); route(intent) }
 override fun onStart() { super.onStart(); lifecycleScope.launch { (application as MemoApp).integrity.refresh(); if((application as MemoApp).appLock.unlocked.value)vm.onForeground() } }
 private fun route(intent: Intent) { lifecycleScope.launch { (application as MemoApp).appLock.unlocked.first { it };vm.busy.first { !it }; when { intent.getBooleanExtra("new_note", false) -> vm.create(); intent.hasExtra("note_id") -> vm.open(intent.getLongExtra("note_id", -1)) }; intent.removeExtra("new_note"); intent.removeExtra("note_id") } }
 override fun onStop() { vm.onBackground(); super.onStop() }
}
