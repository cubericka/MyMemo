package com.personal.memostarter
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
class MainViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
 private val app = application as MemoApp; val repo = app.repository; val appLock = app.appLock
 val notes = repo.notes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
 val checks = repo.checks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
 val unlockRoot = MutableStateFlow<Long?>(null)
 val privacyCovered = MutableStateFlow(false)
 private var pendingAction: (suspend () -> Unit)? = null
 private var backgroundJob: Job? = null
 private var foregroundJob: Job? = null
 private var manualSaveJob: Job? = null
 private val noticeChannel = Channel<String>(Channel.BUFFERED)
 val notices = noticeChannel.receiveAsFlow()
 fun changePin(id: Long,old: String,new: String)=action {
  repo.changePin(id,old,new);error.value="비밀번호를 변경했습니다."
 }
 val editing = MutableStateFlow(false)
 val editor = MutableStateFlow<EditorSession?>(null); val busy = MutableStateFlow(false); val error = MutableStateFlow<String?>(null)
 init { viewModelScope.launch { appLock.unlocked.first { it };busy.first { !it };saved.get<Long>("open_id")?.let { open(it) } } }
 fun action(block: suspend () -> Unit) {
  if (busy.value) return; busy.value = true
  viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: AppUnlockRequired) { privacyCovered.value=true } catch (e: UnlockRequired) { unlockRoot.value=e.root; pendingAction=block } catch (e: Exception) { error.value = e.message ?: "작업을 완료하지 못했습니다. 다시 시도해 주세요." } finally { busy.value = false } }
 }
 private suspend fun flush() { check(editor.value?.saveNow() != false) { "저장에 실패했습니다. 내용을 유지하고 있으니 저장 버튼으로 재시도하세요." } }
 private suspend fun load(id: Long) { val (note, draft) = repo.read(id); editor.value?.dispose(); editor.value = EditorSession(id, note, draft, repo, app.scope); saved["open_id"] = id }
 fun submitPin(pin: String) {
  val root=unlockRoot.value ?: return
  action { repo.unlock(root,pin); unlockRoot.value=null;val retry=pendingAction;pendingAction=null;retry?.invoke();privacyCovered.value=false }
 }
 fun cancelUnlock() { unlockRoot.value=null;pendingAction=null;if(editor.value==null)repo.forgetKeys() }
 fun setLock(pin: String) = action { flush();val id=requireNotNull(editor.value).id;repo.enableLock(id,pin);load(id) }
 fun removeLock() = action { flush();val id=requireNotNull(editor.value).id;repo.disableLock(id);load(id) }
 fun lockNow() = action { flush();editor.value?.dispose();editor.value=null;saved.remove<Long>("open_id");repo.forgetKeys();repo.recoverKnownKeys() }
 fun onBackground() {
  privacyCovered.value=true
  appLock.coverWhileSaving()
  val current=editor.value
  backgroundJob=app.scope.launch { try { current?.saveNow() } finally { repo.forgetKeys();appLock.lock() } }
 }
 fun onForeground() {
  if(foregroundJob?.isActive==true)return
  foregroundJob=viewModelScope.launch {
   backgroundJob?.join();appLock.unlocked.first { it };busy.first { !it }
   action { appLock.requireOpen();repo.recoverKnownKeys();editor.value?.let { repo.requireAccess(it.id) };privacyCovered.value=false }
  }
 }
 fun open(id: Long) = action { flush(); if (editor.value?.id != id) load(id); editing.value=false }
 fun create(parent: Long? = null) = action { flush(); load(repo.create(parent)); editing.value=true }
 fun back() = action { flush(); editor.value?.dispose(); editor.value = null; saved.remove<Long>("open_id"); repo.forgetKeys();repo.recoverKnownKeys() }
 fun startEditing() { editing.value=true }
 fun finishEditing() = action { flush();editing.value=false }
 fun reorderTo(id: Long,targetId: Long,onFinished: ()->Unit)=action {
  try { flush();repo.reorderTo(id,targetId);noticeChannel.send("메모 순서를 변경했습니다") } finally { onFinished() }
 }
 fun save() {
  val requested = editor.value ?: return
  if (manualSaveJob?.isActive == true) return
  manualSaveJob = viewModelScope.launch {
   // A lifecycle/navigation action must not silently discard an explicit Save.
   busy.first { !it }
   if (editor.value !== requested) return@launch
   busy.value = true
   try {
    check(requested.saveNow()) { "저장에 실패했습니다. 내용을 유지하고 있으니 저장 버튼으로 재시도하세요." }
    noticeChannel.send("저장했습니다")
   } catch (e: CancellationException) { throw e }
   catch (e: Exception) { error.value = e.message ?: "저장하지 못했습니다. 다시 시도해 주세요." }
   finally { busy.value = false }
  }
 }
 fun trashMany(ids: Set<Long>) = action { flush(); repo.trashMany(ids); repo.forgetKeys();repo.recoverKnownKeys() }
 fun trash(id: Long) = action { flush(); repo.trash(id); editor.value?.dispose(); editor.value = null; saved.remove<Long>("open_id"); repo.forgetKeys();repo.recoverKnownKeys() }
 fun move(id: Long, parent: Long?) = action { flush(); repo.move(id, parent) }
 fun deleteTrash(ids: Set<Long>) = action { repo.permanentlyDeleteMany(ids) }
 fun enableAppPin(pin: String)=action { flush();withContext(Dispatchers.IO) { appLock.enable(pin) };MemoWidget.requestUpdate(app) }
 fun changeAppPin(old: String,new: String)=action { withContext(Dispatchers.IO) { appLock.change(old,new) } }
 fun disableAppPin(pin: String)=action { withContext(Dispatchers.IO) { appLock.disable(pin) };MemoWidget.requestUpdate(app) }
 fun lockAppNow()=action { flush();repo.forgetKeys();appLock.lock() }
 override fun onCleared() { editor.value?.backgroundSave(); super.onCleared() }
}
