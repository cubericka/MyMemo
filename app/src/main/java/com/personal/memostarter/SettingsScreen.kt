package com.personal.memostarter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun SettingsScreen(vm: MainViewModel,notes: List<Note>,onBack: ()->Unit,onTrash: ()->Unit,onAbout: ()->Unit) {
 val busy by vm.busy.collectAsState()
 Scaffold(topBar={InkTopAppBar(title={Text("설정")},navigationIcon={TextButton(onClick=onBack,enabled=!busy){Text("뒤로")}})}) { padding ->
  LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item { AppPinSettings(vm) }
   item { InkCard { TextButton(onClick=onTrash,enabled=!busy){Text("휴지통")};TextButton(onClick=onAbout,enabled=!busy){Text("어플 정보")} } }
  }
 }
}
