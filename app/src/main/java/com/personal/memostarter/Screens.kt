@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.personal.memostarter
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun date(time: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(time))
@Composable private fun Action(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) } }
@Composable fun MemoScreen(vm: MainViewModel) {
 val snackbars = remember { SnackbarHostState() }
 LaunchedEffect(vm) { vm.notices.collect { message -> snackbars.currentSnackbarData?.dismiss(); snackbars.showSnackbar(message, duration = SnackbarDuration.Short) } }
 val notes by vm.notes.collectAsState(); val checks by vm.checks.collectAsState(); val editor by vm.editor.collectAsState(); val busy by vm.busy.collectAsState(); val error by vm.error.collectAsState()
 val unlockRoot by vm.unlockRoot.collectAsState();val covered by vm.privacyCovered.collectAsState()
 var appPinSettings by remember { mutableStateOf(false) };val appPinEnabled by vm.appLock.enabled.collectAsState()
 var settings by rememberSaveable { mutableStateOf(false) }
 val activity=LocalContext.current as? Activity
 val sensitive=appPinEnabled || settings || appPinSettings || unlockRoot!=null || covered || notes.any { it.id==editor?.id && it.lockRootId!=null }
 DisposableEffect(sensitive) { if(sensitive)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);onDispose { if(!vm.appLock.enabled.value)activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
 val editing by vm.editing.collectAsState()
 var trashScreen by rememberSaveable { mutableStateOf(false) }
 var deleteTarget by remember { mutableStateOf<Note?>(null) }; var moveTarget by remember { mutableStateOf<Note?>(null) }; var about by remember { mutableStateOf(false) }
 LaunchedEffect(covered) { if(covered) { deleteTarget=null;moveTarget=null;appPinSettings=false;about=false } }
 BackHandler(enabled = editor != null || trashScreen || settings) { if (!busy && !covered) { if (editor != null) vm.back() else if(trashScreen)trashScreen=false else { settings=false;vm.repo.forgetKeys() } } }
 Box(Modifier.fillMaxSize()) {
  if(covered) Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Outlined.Lock,null,Modifier.size(40.dp));Text("메모 보호 중",style=MaterialTheme.typography.titleLarge);TextButton(onClick=vm::onForeground,enabled=!busy){Text("계속하기 / 잠금 해제")} }
  else if (editor != null) key(editor!!.id) {
   if(!editing)ReadingScreen(editor!!,notes,busy,vm::back,vm::startEditing,vm::open)
   else EditorScreen(editor!!, notes, busy, vm::back, vm::save, vm::open,
    onChild = { vm.create(editor!!.id) }, onPin = { vm.action { vm.repo.pin(editor!!.id) } },
    onMove = { moveTarget = notes.find { it.id == editor!!.id } }, onTrash = { deleteTarget = notes.find { it.id == editor!!.id } }, onAppLock={appPinSettings=true},onRead=vm::finishEditing)
  } else if (trashScreen) TrashScreen(notes,busy,{trashScreen=false},vm::deleteTrash)
  else if(settings) SettingsScreen(vm,notes,{settings=false;vm.repo.forgetKeys()},{trashScreen=true},{about=true})
  else ListScreen(notes, checks, vm::open, { vm.create() }, { vm.create(it.id) }, { deleteTarget = it }, { moveTarget = it },
   { n -> vm.action { vm.repo.pin(n.id) } }, { n, delta -> vm.action { vm.repo.reorder(n.id, delta) } }, { trashScreen = true }, { about = true }, {settings=true}, {appPinSettings=true}, vm::reorderTo, busy, vm::trashMany)
  SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 64.dp))
  if (busy) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .25f)).clickable { }, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
 }
 if(appPinSettings)AlertDialog(onDismissRequest={if(!busy)appPinSettings=false},title={Text("앱 잠금 설정")},text={AppPinSettings(vm)},confirmButton={TextButton(enabled=!busy,onClick={appPinSettings=false}){Text("닫기")}})
 if(unlockRoot!=null)PinDialog(false,busy,vm::cancelUnlock,vm::submitPin,title="이전 잠금 메모 확인",description="기존 메모의 이전 PIN을 한 번 확인합니다. 이후 앱 공통 PIN으로 열 수 있습니다.")
 deleteTarget?.let { n -> Confirm("휴지통으로 이동할까요?", "‘${n.displayTitle}’과 하위 메모가 함께 이동합니다. 휴지통에서는 복원할 수 없습니다.", "이동", { deleteTarget = null }) { vm.trash(n.id); deleteTarget = null } }
 moveTarget?.let { n -> DestinationDialog("메모 이동", n, notes, { moveTarget = null }) { parent -> vm.move(n.id, parent); moveTarget = null } }
 if (about) AlertDialog(onDismissRequest={about=false},title={Text("어플 정보")},text={Column {
  val context=LocalContext.current
  Text(context.applicationInfo.loadLabel(context.packageManager).toString(),style=MaterialTheme.typography.titleMedium)
  Text("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
  Text("제작자: 아카라이브 모르윈")
  Spacer(Modifier.height(12.dp));Text("광고·로그인 없는 오프라인 메모장\n내용은 자동 저장되며 수동 저장도 가능합니다. 휴지통은 자동으로 비우지 않으며 복원을 지원하지 않습니다. 앱 공통 PIN을 잊으면 복구할 수 없습니다. 앱 삭제·데이터 초기화 시 메모가 지워집니다.\n홈 화면의 위젯 메뉴에서 My Memo를 추가하세요.")
 }},confirmButton={TextButton(onClick={about=false}){Text("확인")}})
 error?.let { msg -> AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("작업 안내") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("확인") } }) }
}
@Composable private fun Confirm(title: String, text: String, confirm: String, dismiss: () -> Unit, action: () -> Unit) { AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = action) { Text(confirm) } }, dismissButton = { TextButton(onClick = dismiss) { Text("취소") } }) }
@Composable private fun DestinationDialog(title: String, note: Note, all: List<Note>, dismiss: () -> Unit, select: (Long?) -> Unit) {
 val candidates = TreeRules.ordered(all.filter { it.trashedAt == null && runCatching { TreeRules.validateMove(note.id, it.id, all) }.isSuccess })
 AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { LazyColumn(Modifier.heightIn(max = 400.dp)) {
  item { Text("최상위 메모로", Modifier.fillMaxWidth().clickable { select(null) }.padding(16.dp)) }
  items(candidates, key = { it.id }) { n -> Text(TreeRules.path(n.id, all), Modifier.fillMaxWidth().clickable { select(n.id) }.padding(16.dp)) }
 } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}
private data class TreeRow(val note: Note, val indent: Int)
private fun rows(all: List<Note>, roots: List<Note>, collapsed: Set<Long>): List<TreeRow> {
 val result = mutableListOf<TreeRow>()
 fun add(n: Note, indent: Int) { result += TreeRow(n, indent); if (n.id !in collapsed && indent < 2) TreeRules.ordered(all.filter { it.parentId == n.id }).forEach { add(it, indent + 1) } }
 roots.forEach { add(it, 0) }; return result
}
@Composable private fun ListScreen(notes: List<Note>, checks: List<CheckItem>, onOpen: (Long) -> Unit, onCreate: () -> Unit, onChild: (Note) -> Unit, onTrash: (Note) -> Unit, onMove: (Note) -> Unit, onPin: (Note) -> Unit, onOrder: (Note, Int) -> Unit, onTrashScreen: () -> Unit, onAbout: () -> Unit, onSettings: () -> Unit, onAppLock: () -> Unit, onReorder: (Long, Long, () -> Unit) -> Unit, busy: Boolean, onTrashMany: (Set<Long>) -> Unit) {
 var query by rememberSaveable { mutableStateOf("") }; var search by rememberSaveable { mutableStateOf(false) }; var tab by rememberSaveable { mutableIntStateOf(0) }; var menu by remember { mutableStateOf(false) }; var expanded by rememberSaveable { mutableStateOf(longArrayOf()) }
 var selected by rememberSaveable { mutableStateOf(longArrayOf()) }
 var confirmDelete by remember { mutableStateOf(false) }
 var selectionMode by rememberSaveable { mutableStateOf(false) }
 fun toggleSelection(id: Long) { selectionMode=true; selected = if (id in selected) selected.filter { it != id }.toLongArray() else selected + id }
 BackHandler(selectionMode && !busy) { selected = longArrayOf(); selectionMode=false }
 LaunchedEffect(query, tab) { selected = longArrayOf(); selectionMode=false; confirmDelete = false }
 LaunchedEffect(notes) { selected = selected.filter { id -> notes.any { it.id == id && it.trashedAt == null } }.toLongArray() }
 if (confirmDelete && selectionMode) Confirm("${selected.size}개 메모를 삭제할까요?", "선택한 메모와 하위 메모를 휴지통으로 이동합니다. 휴지통에서는 복원할 수 없습니다.", "삭제", { confirmDelete = false }) {
  onTrashMany(selected.toSet()); confirmDelete = false; selected = longArrayOf(); selectionMode=false
 }
 val active = notes.filter { it.trashedAt == null }
 val listState=rememberLazyListState()
 val canDrag=!busy && !selectionMode
 val drag=rememberMemoDragOrder(active,listState,canDrag,onReorder)
 val displayed = drag.projected(active)
 val roots = TreeRules.ordered(displayed.filter { it.parentId == null })
 val matches = if (query.isNotBlank()) TreeRules.ordered(displayed.filter { n -> n.title.contains(query, true) || n.body.contains(query, true) || TemplateDetails.searchable(n.details).contains(query, true) || checks.any { it.noteId == n.id && it.text.contains(query, true) } }) else emptyList()
 Scaffold(topBar = { InkTopAppBar(title = { Text(if(selectionMode) "${selected.size}개 선택" else "My Memo", fontWeight = FontWeight.Bold) }, navigationIcon = { if(selectionMode) Action(Icons.Outlined.Close, "선택 취소", !busy) { selected = longArrayOf(); selectionMode=false } }, actions = {
  if(selectionMode) Action(Icons.Outlined.Delete, "선택 메모 삭제", !busy && selected.isNotEmpty()) { confirmDelete = true } else {
  Action(Icons.Outlined.Lock, "앱 잠금 설정", !busy, onAppLock)
  Action(Icons.Outlined.Checklist, "메모 선택", !busy) { selectionMode=true }
  Action(Icons.Outlined.Search, "메모 검색") { search = !search; if (!search) query = "" }
  Box { Action(Icons.Outlined.Settings, "설정") { menu = true }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text={Text("설정 · 비밀번호")},onClick={menu=false;onSettings()}); DropdownMenuItem(text = { Text("휴지통") }, onClick = { menu = false; onTrashScreen() }); DropdownMenuItem(text = { Text("앱 정보 및 사용 안내") }, onClick = { menu = false; onAbout() }) } }
 } }) }, floatingActionButton = { if(!selectionMode) FloatingActionButton(onClick = onCreate, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Outlined.Add, "새 메모") } }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
   if (search) OutlinedTextField(query, { query = it }, placeholder = { Text("제목·본문·체크리스트 검색") }, singleLine = true, shape = MaterialTheme.shapes.medium, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedContainerColor=MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
   TabRow(selectedTabIndex = tab) { listOf("전체 메모", "고정 메모", "설정").forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { if(i==2) { selected=longArrayOf(); selectionMode=false; onSettings() } else tab = i }, text = { Text(label) }) } }
   if(tab==0 && query.isBlank() && roots.isNotEmpty()) Text("제목 영역을 길게 누른 채 끌어 순서를 변경하세요",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=24.dp,top=8.dp))
   Box(Modifier.fillMaxSize().clipToBounds()) {
   LazyColumn(Modifier.fillMaxSize().memoDragList(drag,canDrag).testTag("memo-list"), state=listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)) {
    if (query.isNotBlank()) {
     item { Text("검색 결과 ${matches.size}개", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp)) }; if (matches.isEmpty()) item { Empty("검색 결과가 없어요") }
     items(matches, key = { it.id }) { n -> NoteRow(n, 0, active, checks, false, {}, onOpen, onChild, onTrash, onMove, onPin, onOrder, true, drag=drag, selectionMode=selectionMode, selected=n.id in selected, onSelect=::toggleSelection, modifier=Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=tween(160))) }
    } else if (tab == 0) {
     if (roots.isEmpty()) item { Empty("아직 메모가 없어요\n+ 버튼으로 첫 메모를 작성해 보세요") }
     val pinned = roots.filter { it.pinned }; val normal = roots.filter { !it.pinned }
     if (pinned.isNotEmpty()) item { Section("고정된 메모") }
     items(rows(displayed, pinned, active.filter { it.id !in expanded }.map { it.id }.toSet()), key = { it.note.id }) { r -> NoteRow(r.note, r.indent, active, checks, r.note.id in expanded, { expanded = if (r.note.id in expanded) expanded.filter { it != r.note.id }.toLongArray() else expanded + r.note.id }, onOpen, onChild, onTrash, onMove, onPin, onOrder, drag=drag, selectionMode=selectionMode, selected=r.note.id in selected, onSelect=::toggleSelection, modifier=Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=tween(160))) }
     if (normal.isNotEmpty()) item { Section("전체 메모") }
     items(rows(displayed, normal, active.filter { it.id !in expanded }.map { it.id }.toSet()), key = { it.note.id }) { r -> NoteRow(r.note, r.indent, active, checks, r.note.id in expanded, { expanded = if (r.note.id in expanded) expanded.filter { it != r.note.id }.toLongArray() else expanded + r.note.id }, onOpen, onChild, onTrash, onMove, onPin, onOrder, drag=drag, selectionMode=selectionMode, selected=r.note.id in selected, onSelect=::toggleSelection, modifier=Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=tween(160))) }
    } else {
     val list = TreeRules.ordered(displayed.filter { it.pinned })
     if (list.isEmpty()) item { Empty("표시할 메모가 없어요") }
     items(list, key = { it.id }) { n -> NoteRow(n, 0, active, checks, false, {}, onOpen, onChild, onTrash, onMove, onPin, onOrder, true, drag=drag, selectionMode=selectionMode, selected=n.id in selected, onSelect=::toggleSelection, modifier=Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=tween(160))) }
    }
   }
   val floating = drag.source
   if(floating != null) {
    val lift=remember(floating.id) { Animatable(0f) }
    LaunchedEffect(drag.dragging) { lift.animateTo(if(drag.dragging)1f else 0f,tween(140)) }
    val density=LocalDensity.current
    val indent=if(tab==0)TreeRules.depth(floating.parentId,active).coerceAtMost(2) else 0
    Box(Modifier.offset { IntOffset((drag.startBounds.left-drag.viewport.left).roundToInt(),(drag.top-drag.viewport.top).roundToInt()) }
     .width(with(density) { drag.startBounds.width.toDp() }).zIndex(10f)
     .graphicsLayer {
      scaleX=1f+.03f*lift.value;scaleY=scaleX
      translationY=-with(density) { 3.dp.toPx() }*lift.value
      shadowElevation=with(density) { 8.dp.toPx() }*lift.value
      shape=RoundedCornerShape(10.dp);clip=false
     }.background(lerp(MaterialTheme.colorScheme.surface,MaterialTheme.colorScheme.primaryContainer,.55f*lift.value),RoundedCornerShape(10.dp))
     .testTag("floating-note").semantics { stateDescription=if(drag.dragging) "드래그 중" else "배치 중" }) {
      NoteRow(floating,indent,active,checks,floating.id in expanded,{},onOpen,onChild,onTrash,onMove,onPin,onOrder,showPath=tab==1,interactive=false)
    }
   }
   }
  }
 }
}
@Composable private fun Section(text: String) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp)) }
@Composable private fun Empty(text: String) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
@Composable private fun NoteRow(n: Note, indent: Int, active: List<Note>, checks: List<CheckItem>, expanded: Boolean, toggle: () -> Unit, onOpen: (Long) -> Unit, onChild: (Note) -> Unit, onTrash: (Note) -> Unit, onMove: (Note) -> Unit, onPin: (Note) -> Unit, onOrder: (Note, Int) -> Unit, showPath: Boolean = false, drag: MemoDragOrder? = null, selectionMode: Boolean = false, selected: Boolean = false, onSelect: (Long) -> Unit = {}, modifier: Modifier = Modifier, interactive: Boolean = true) {
 var menu by remember { mutableStateOf(false) }; val children = active.count { it.parentId == n.id }; val ownChecks = checks.filter { it.noteId == n.id }
 val placeholder = drag?.source?.id==n.id
 Column(modifier.fillMaxWidth().memoDragRow(drag,n.id).testTag("note-row-${n.id}")
  .then(if(placeholder) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)
  .padding(start = (indent * 18).dp, top = 4.dp, bottom = 4.dp)
  .clip(RoundedCornerShape(12.dp))
  .border(1.dp, if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
  .background(if(selected || !interactive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
  .clickable(enabled=interactive) { if(selectionMode) onSelect(n.id) else onOpen(n.id) }) {
  Row(verticalAlignment = Alignment.CenterVertically) {
   if(selectionMode) Checkbox(checked=selected, onCheckedChange={ onSelect(n.id) })
   if (children > 0 && !showPath && !selectionMode) Action(if (expanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight, "하위 메모 접기·펼치기", onClick = toggle) else Icon(if(n.lockRootId!=null) Icons.Outlined.Lock else Icons.Outlined.Description, null, Modifier.padding(12.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
   Column(Modifier.weight(1f).memoDragTitle(drag,n.id).testTag("note-title-${n.id}").padding(vertical = 12.dp)) {
    Text(n.displayTitle, fontWeight = if (children > 0) FontWeight.SemiBold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (showPath && n.parentId != null) Text(TreeRules.path(n.parentId, active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    val preview = if (ownChecks.isNotEmpty()) ownChecks.take(2).joinToString("  ") { (if (it.done) "☑ " else "☐ ") + it.text } else n.body
    if (preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (children > 0) "하위 ${children}개 · ${date(n.updatedAt)}" else date(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
   if (n.pinned) Icon(Icons.Outlined.PushPin, "고정된 메모", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
   if(!selectionMode) Box { Action(Icons.Outlined.MoreVert, "메모 메뉴") { menu = true }; DropdownMenu(menu, { menu = false }) {
    fun close(action: () -> Unit) { menu = false; action() }
    DropdownMenuItem(text = { Text("메모 선택") }, onClick = { close { onSelect(n.id) } })
    DropdownMenuItem(text = { Text(if (n.pinned) "고정 해제" else "상단 고정") }, onClick = { close { onPin(n) } })
    DropdownMenuItem(text = { Text("하위 메모 추가") }, onClick = { close { onChild(n) } })
    DropdownMenuItem(text = { Text("위로 이동") }, onClick = { close { onOrder(n, -1) } })
    DropdownMenuItem(text = { Text("아래로 이동") }, onClick = { close { onOrder(n, 1) } })
    DropdownMenuItem(text = { Text("다른 위치로 이동") }, onClick = { close { onMove(n) } })
    DropdownMenuItem(text = { Text("휴지통으로") }, onClick = { close { onTrash(n) } })
   } }
  }
 }
}
@Composable private fun EditorScreen(session: EditorSession, notes: List<Note>, busy: Boolean, onBack: () -> Unit, onSave: () -> Unit, onOpen: (Long) -> Unit, onChild: () -> Unit, onPin: () -> Unit, onMove: () -> Unit, onTrash: () -> Unit,onAppLock: () -> Unit,onRead: () -> Unit) {
 val state by session.state.collectAsState(); val draft = state.draft; val note = notes.find { it.id == session.id }; val children = TreeRules.ordered(notes.filter { it.parentId == session.id && it.trashedAt == null })
 val template = MemoTemplates.get(draft.template)
 val focusManager = LocalFocusManager.current
 var menu by remember { mutableStateOf(false) }; var showChildren by rememberSaveable { mutableStateOf(false) }; var hideDone by rememberSaveable { mutableStateOf(false) }
 var showChecks by rememberSaveable(draft.template) { mutableStateOf(draft.template=="checklist") }; var showDetails by rememberSaveable(draft.template) { mutableStateOf(draft.template!="quick") }
 var templatePicker by rememberSaveable { mutableStateOf(false) }; var metadata by remember { mutableStateOf(false) }
 Scaffold(modifier = Modifier.imePadding(), topBar = { InkTopAppBar(title = {
  Column { Text("메모", style = MaterialTheme.typography.titleSmall)
   Text(state.status, style = MaterialTheme.typography.labelSmall, color = if (state.status.startsWith("저장 실패")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
 }, navigationIcon = { Action(Icons.AutoMirrored.Outlined.ArrowBack, "저장하고 목록으로", !busy, onBack) }, actions = {
  TextButton(onClick=onRead,enabled=!busy){Text("읽기")}
  Action(Icons.AutoMirrored.Outlined.Undo, "실행 취소", state.canUndo && !busy, session::undo); Action(Icons.AutoMirrored.Outlined.Redo, "다시 실행", state.canRedo && !busy, session::redo)
  Box { Action(Icons.Outlined.MoreHoriz, "편집 메뉴") { menu = true }; DropdownMenu(menu, { menu = false }) {
   DropdownMenuItem(text={Text("앱 잠금 설정")},onClick={menu=false;onAppLock()})
   DropdownMenuItem(text = { Text("메모 정보") }, onClick = { menu = false; metadata = true })
   DropdownMenuItem(text = { Text(if (note?.pinned == true) "고정 해제" else "상단 고정") }, onClick = { menu = false; onPin() })
   DropdownMenuItem(text = { Text("다른 위치로 이동") }, onClick = { menu = false; onMove() }); DropdownMenuItem(text = { Text("휴지통으로") }, onClick = { menu = false; onTrash() })
  } }
 }) }, bottomBar = {
  Surface(tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
   TextButton(onClick = { templatePicker = true }, enabled = !busy) { Icon(Icons.Outlined.DashboardCustomize, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("템플릿 변경") }
   Spacer(Modifier.weight(1f)); Button(onClick = { focusManager.clearFocus(); onSave() }, enabled = !busy) { Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("저장") }
  } }
 }) { padding ->
  LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)) {
   item {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     TextButton(onClick = { templatePicker = true }, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(template.name, style = MaterialTheme.typography.labelMedium); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) }
     Spacer(Modifier.weight(1f))
     Text("수정 ${date(state.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { metadata = true }.padding(vertical = 12.dp))
    }
    TextField(draft.title, { session.edit(draft.copy(title = it), "title") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(bottom=8.dp).border(1.dp,MaterialTheme.colorScheme.outlineVariant,MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium), label={Text(if(template.id=="quick") "제목" else template.titleHint)}, placeholder = { Text(template.titleHint) }, textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), singleLine = true, colors = plainFieldColors())
    if (note?.parentId != null) Text(TreeRules.path(note.parentId, notes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
    TextField(draft.body, { session.edit(draft.copy(body = it), "body") }, enabled = !busy, modifier = Modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outlineVariant,MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium), placeholder = { Text(template.bodyHint, style = MaterialTheme.typography.bodyMedium) }, textStyle = MaterialTheme.typography.bodyLarge, minLines = if(template.id=="quick") 8 else 4, colors = plainFieldColors())
    if(template.id!="quick")Text("${template.name} · 입력 양식",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
    TextButton(onClick = { showDetails = !showDetails }) { Icon(if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)); Text(if (showDetails) "상세 항목 접기" else "상세 항목 · 선택 입력", style = MaterialTheme.typography.labelMedium) }
   }
   if (showDetails) {
    item { Text("모든 항목은 선택 사항입니다. 템플릿을 바꿔도 입력 내용은 유지됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
    items(template.fields, key = { "field:$it" }) { label ->
     OutlinedTextField(draft.details[label].orEmpty(), { value -> session.edit(draft.copy(details = draft.details + (label to value)), "detail:$label") }, label = { Text("$label (선택)") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), textStyle = MaterialTheme.typography.bodyMedium)
    }
    val retained = draft.details.filter { it.key !in template.fields && it.value.isNotBlank() }
    if (retained.isNotEmpty()) item { Text("이전 템플릿에서 작성한 항목", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
    items(retained.keys.toList(), key = { "retained:$it" }) { label ->
     OutlinedTextField(draft.details[label].orEmpty(), { value -> session.edit(draft.copy(details = draft.details + (label to value)), "detail:$label") }, label = { Text(label) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), textStyle = MaterialTheme.typography.bodyMedium)
    }
   }
   item {
    Spacer(Modifier.height(24.dp))
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
     Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.Checklist, null, Modifier.padding(start = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
      TextButton(onClick = { showChecks = !showChecks }) { Text("체크리스트 ${draft.checks.count { it.done }}/${draft.checks.size}"); Icon(if (showChecks) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) }
      Spacer(Modifier.weight(1f)); Action(Icons.Outlined.Add, "체크 항목 추가", !busy) { showChecks = true; session.addCheck() }
     }
    }
   }
   if (showChecks) {
    if (draft.checks.isEmpty()) item { Text(if (draft.template == "checklist") "+를 눌러 첫 체크 항목을 추가하세요" else "필요할 때 체크 항목을 추가하세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
    items(draft.checks.filter { !hideDone || !it.done }, key = { it.id }) { item ->
     Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = item.done, enabled = !busy, onCheckedChange = { value -> session.edit(draft.copy(checks = draft.checks.map { if (it.id == item.id) it.copy(done = value) else it })) })
      TextField(item.text, { value -> session.edit(draft.copy(checks = draft.checks.map { if (it.id == item.id) it.copy(text = value) else it }), "check:${item.id}") }, enabled = !busy, modifier = Modifier.weight(1f), placeholder = { Text("체크할 내용") }, textStyle = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None), colors = plainFieldColors())
      Action(Icons.Outlined.Close, "체크 항목 삭제", !busy) { session.edit(draft.copy(checks = draft.checks.filter { it.id != item.id })) }
     }
    }
    if (draft.checks.isNotEmpty()) item { TextButton(onClick = { hideDone = !hideDone }) { Text(if (hideDone) "완료 항목 펼치기" else "완료 항목 접기", style = MaterialTheme.typography.labelMedium) } }
   }
   item {
    Spacer(Modifier.height(32.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(start = 16.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
     TextButton(onClick = { showChildren = !showChildren }) { Text("하위 메모 ${children.size}"); Icon(if (showChildren) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) }
     Spacer(Modifier.weight(1f)); if(showChildren)Action(Icons.Outlined.Add, "하위 메모 추가", !busy, onChild)
    }
   }
   if (showChildren) {
    if (children.isEmpty()) item { Text("주제를 나누고 싶을 때 하위 메모를 추가하세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
    items(children, key = { "child:${it.id}" }) { child ->
     OutlinedCard(onClick = { onOpen(child.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
      Column(Modifier.fillMaxWidth().heightIn(min=144.dp).padding(20.dp)) {
       Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Description,null,Modifier.size(24.dp));Spacer(Modifier.width(12.dp));Text(child.displayTitle,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null) }
       Spacer(Modifier.height(12.dp));Text(child.body.ifBlank { "내용을 작성해 보세요" },style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=3,overflow=TextOverflow.Ellipsis)
       Spacer(Modifier.height(12.dp));Text("눌러서 이 메모 열기",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
      }
     }
    }
   }
  }
 }
 if (metadata) AlertDialog(onDismissRequest = { metadata = false }, title = { Text("메모 정보") }, text = { Text("작성 ${date(note?.createdAt ?: state.updatedAt)}\n수정 ${date(state.updatedAt)}\n${state.status}") }, confirmButton = { TextButton(onClick = { metadata = false }) { Text("확인") } })
 if (templatePicker) AlertDialog(onDismissRequest = { templatePicker = false }, title = { Text("메모 템플릿") }, text = {
  LazyColumn(Modifier.heightIn(max = 420.dp)) {
   item { Text("제목·본문은 자유롭게 작성하세요. 템플릿을 바꿔도 기존 내용은 유지됩니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp)) }
   items(MemoTemplates.all, key = { it.id }) { option ->
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { session.selectTemplate(option.id); templatePicker = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
     RadioButton(selected = option.id == draft.template, onClick = null)
     Column(Modifier.padding(start = 8.dp)) { Text(option.name, style = MaterialTheme.typography.titleSmall); Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
   }
  }
 }, confirmButton = { TextButton(onClick = { templatePicker = false }) { Text("닫기") } })
}
@Composable private fun plainFieldColors() = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
