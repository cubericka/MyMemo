package com.personal.memostarter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
class EditHistory(initial: Draft) {
 var current = initial; private set
 private val undo = ArrayDeque<Draft>(); private val redo = ArrayDeque<Draft>()
 private var lastGroup = ""; private var lastTime = 0L
 val canUndo get() = undo.isNotEmpty(); val canRedo get() = redo.isNotEmpty()
 fun edit(next: Draft, group: String, time: Long = System.currentTimeMillis()) {
  if (next == current) return
  if (group.isEmpty() || group != lastGroup || time - lastTime > 500) { undo.addLast(current); if (undo.size > 100) undo.removeFirst() }
  redo.clear(); current = next; lastGroup = group; lastTime = time
 }
 fun undo(): Draft { if (canUndo) { redo.addLast(current); current = undo.removeLast() }; lastGroup = ""; return current }
 fun redo(): Draft { if (canRedo) { undo.addLast(current); current = redo.removeLast() }; lastGroup = ""; return current }
}
data class EditorState(val draft: Draft, val status: String = "저장됨", val updatedAt: Long, val canUndo: Boolean = false, val canRedo: Boolean = false)
class EditorSession(val id: Long, note: Note, initial: Draft, private val repo: ContentStore, private val scope: CoroutineScope) {
 private val history = EditHistory(initial)
 private val _state = MutableStateFlow(EditorState(initial, updatedAt = note.updatedAt)); val state: StateFlow<EditorState> = _state
 private var revision = 0L; private var savedRevision = 0L; private var debounce: Job? = null; private val saveMutex = Mutex()
 fun edit(draft: Draft, group: String = "") { if (draft == history.current) return; history.edit(draft, group); changed() }
 fun undo() { if (history.canUndo) { history.undo(); changed() } }
 fun redo() { if (history.canRedo) { history.redo(); changed() } }
 fun selectTemplate(id: String) { require(MemoTemplates.all.any { it.id == id }); edit(history.current.copy(template = id)) }
 fun addCheck() { edit(history.current.copy(checks = history.current.checks + CheckItem(UUID.randomUUID().toString(), id, ""))) }
 private fun changed() {
  revision++; _state.value = _state.value.copy(draft = history.current, status = "저장 대기…", canUndo = history.canUndo, canRedo = history.canRedo)
  debounce?.cancel(); debounce = scope.launch { delay(600); scope.launch { saveNow() } }
 }
 suspend fun saveNow(): Boolean = saveMutex.withLock {
  // Latest snapshot is read under the lock. Drain edits arriving during IO before navigation.
  while (savedRevision < revision) {
   val snapshot = history.current; val version = revision; _state.value = _state.value.copy(status = "저장 중…")
   try { val time = repo.save(id, snapshot); savedRevision = version; _state.value = _state.value.copy(updatedAt = time, status = if (version == revision) "저장됨" else "저장 대기…") }
   catch (e: CancellationException) { throw e }
   catch (e: Exception) { _state.value = _state.value.copy(status = "저장 실패 · 저장 버튼으로 재시도"); return@withLock false }
  }; true
 }
 fun backgroundSave() { scope.launch { saveNow() } }
 fun dispose() { debounce?.cancel() }
}
