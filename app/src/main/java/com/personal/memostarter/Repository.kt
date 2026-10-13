package com.personal.memostarter
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class Draft(val title: String = "", val body: String = "", val checks: List<CheckItem> = emptyList(), val template: String = "quick", val details: Map<String, String> = emptyMap())
interface ContentStore { suspend fun save(id: Long, draft: Draft): Long }
class MemoRepository(val db: MemoDatabase, private val authorize: suspend () -> Unit = {}, val vault: NoteVault = NoteVault(), private val changed: () -> Unit = {}) : ContentStore {
 private val dao = db.dao(); private val writes = Mutex()
 val notes = combine(dao.observeNotes(),vault.revision) { all,_ -> all.map(vault::project) }.flowOn(Dispatchers.IO)
 val checks = combine(dao.observeChecks(),dao.observeNotes(),vault.revision) { plain,all,_ -> plain + all.filter { it.lockRootId!=null && vault.has(it.lockRootId) }.flatMap { runCatching { vault.read(it).checks }.getOrDefault(emptyList()) } }.flowOn(Dispatchers.IO)
 private suspend fun <T> write(action: suspend () -> T): T {
  val result = withContext(Dispatchers.IO) { writes.withLock { authorize(); db.withTransaction { action() } } }
  runCatching { changed() }; return result
 }
 private fun access(note: Note) { note.lockRootId?.let { if(!vault.has(it))throw UnlockRequired(it) } }
 suspend fun requireAccess(id: Long) = withContext(Dispatchers.IO) { access(requireNotNull(dao.get(id))) }
 private suspend fun draft(note: Note): Draft { access(note);return if(note.lockRootId!=null)vault.read(note) else Draft(note.title,note.body,dao.checks(note.id),note.template,TemplateDetails.decode(note.details)) }
 suspend fun read(id: Long): Pair<Note, Draft> = withContext(Dispatchers.IO) { db.withTransaction {
  val note = requireNotNull(dao.get(id)) { "메모를 찾을 수 없습니다" }; require(note.trashedAt == null) { "휴지통에 있는 메모입니다" }
  val value=draft(note); vault.project(note) to value
 } }
 // Widgets always use raw records: even an unlocked editing session must not reveal protected data.
 suspend fun snapshot(): Pair<List<Note>, List<CheckItem>> = withContext(Dispatchers.IO) { db.withTransaction { dao.all() to dao.allChecks() } }
 private fun nextOrder(parent: Long?, pinned: Boolean, all: List<Note>) = (all.filter { it.parentId == parent && it.pinned == pinned && it.trashedAt == null }.maxOfOrNull { it.sortOrder } ?: -1) + 1
 private suspend fun store(note: Note,draft: Draft,time: Long) {
  access(note);require(draft.checks.map { it.id }.distinct().size==draft.checks.size)
  val value=draft.copy(checks=draft.checks.mapIndexed { i,c -> c.copy(noteId=note.id,sortOrder=i) })
  if(note.lockRootId!=null) {
   dao.update(note.copy(title="잠긴 메모",body="",template="quick",details="{}",sealedContent=vault.seal(note.id,note.lockRootId,value),updatedAt=time));dao.clearChecks(note.id)
  } else {
   dao.update(note.copy(title=value.title,body=value.body,template=value.template,details=TemplateDetails.encode(value.details),updatedAt=time,sealedContent=null))
   dao.clearChecks(note.id);dao.insertChecks(value.checks)
  }
 }
 suspend fun create(parent: Long?): Long = write {
  val all = dao.all();val p=all.find { it.id==parent }
  if (parent != null) { require(p!=null && p.trashedAt==null) { "상위 메모가 없습니다" };access(p) }
  require(TreeRules.depth(parent, all) < 3) { "최대 3단계까지 지원합니다" }
  val id=dao.insert(Note(parentId = parent, sortOrder = nextOrder(parent, false, all),lockRootId=p?.lockRootId))
  if(p?.lockRootId!=null) { val n=requireNotNull(dao.get(id));store(n,Draft(),n.updatedAt) };id
 }
 override suspend fun save(id: Long, draft: Draft): Long = write {
  val note=requireNotNull(dao.get(id));check(note.trashedAt==null) { "메모가 휴지통에 있습니다" };val time=System.currentTimeMillis();store(note,draft,time);time
 }
 private fun validateBoundary(note: Note,parent: Long?,all: List<Note>) {
  access(note)
  val target=all.find { it.id==parent };target?.let(::access)
  // Restoring/moving the locked root at the root level keeps its existing envelope.
  val expected=if(note.id==note.lockRootId && parent==null)note.lockRootId else target?.lockRootId
  require(note.lockRootId==expected) { "잠긴 메모의 보호 범위를 바꾸려면 먼저 상위 메모의 잠금 설정을 해제하세요" }
 }
 suspend fun move(id: Long, parent: Long?) = write {
  val all = dao.all(); val note = requireNotNull(dao.get(id)); require(note.trashedAt == null);validateBoundary(note,parent,all)
  TreeRules.validateMove(id, parent, all); dao.update(note.copy(parentId = parent, sortOrder = nextOrder(parent, note.pinned, all)))
 }
 suspend fun pin(id: Long) = write { val n = requireNotNull(dao.get(id));access(n); require(n.trashedAt == null); dao.update(n.copy(pinned = !n.pinned, sortOrder = nextOrder(n.parentId, !n.pinned, dao.all()))) }
 suspend fun reorderTo(id: Long, targetId: Long) = write {
  val all=dao.all();access(requireNotNull(all.find { it.id==id }))
  ReorderRules.moveTo(id,targetId,all).forEach { dao.update(it) }
 }
 suspend fun reorder(id: Long, delta: Int) = write {
  require(delta == -1 || delta == 1); val all = dao.all(); val n = requireNotNull(dao.get(id));access(n)
  val peers = TreeRules.ordered(all.filter { it.parentId == n.parentId && it.pinned == n.pinned && it.trashedAt == null }).toMutableList()
  val index = peers.indexOfFirst { it.id == id }; val target = index + delta
  if (index >= 0 && target in peers.indices) { java.util.Collections.swap(peers, index, target); peers.forEachIndexed { i, item -> dao.update(item.copy(sortOrder = i.toLong())) } }
 }
 suspend fun trash(id: Long) = write {
  val all = dao.all(); require(all.any { it.id == id && it.trashedAt == null }); val ids = TreeRules.descendants(id, all);all.filter { it.id in ids }.forEach(::access)
  val batch = UUID.randomUUID().toString(); val time = System.currentTimeMillis()
  all.filter { it.id in ids && it.trashedAt == null }.forEach { dao.update(it.copy(trashedAt = time, trashBatch = batch)) }
 }
 suspend fun restore(id: Long, parent: Long?) = write {
  val all = dao.all(); val root = requireNotNull(dao.get(id)); require(root.trashedAt != null);validateBoundary(root,parent,all); TreeRules.validateMove(id, parent, all)
  val ids = TreeRules.descendants(id, all);all.filter { it.id in ids }.forEach(::access)
  all.filter { it.id in ids && it.trashBatch == root.trashBatch }.forEach { dao.update(it.copy(trashedAt = null, trashBatch = null, parentId = if (it.id == id) parent else it.parentId, sortOrder = if (it.id == id) nextOrder(parent, it.pinned, all) else it.sortOrder)) }
 }
 suspend fun permanentlyDelete(id: Long) = write {
  val all = dao.all(); val ids = TreeRules.descendants(id, all);all.filter { it.id in ids }.forEach(::access)
  require(all.filter { it.id in ids }.all { it.trashedAt != null }) { "사용 중인 메모는 영구 삭제할 수 없습니다" }
  ids.forEach { dao.erase(it);dao.eraseLock(it) }
 }
 suspend fun enableLock(id: Long,pin: String) {
  val key=VaultCrypto.random(32)
  try {
   write {
    val all=dao.all();val root=requireNotNull(dao.get(id));require(root.parentId==null && root.trashedAt==null) { "최상위 메모에서 잠금을 설정하세요" }
    val affected=all.filter { it.id in TreeRules.descendants(id,all) };require(affected.all { it.lockRootId==null }) { "이미 잠긴 메모가 있습니다" }
    val envelope=vault.envelope(id,pin,key);dao.putLock(envelope)
    for(note in affected) { val value=draft(note);dao.update(note.copy(title="잠긴 메모",body="",template="quick",details="{}",lockRootId=id,sealedContent=vault.seal(note.id,id,value,key)));dao.clearChecks(note.id) }
   }
   vault.cache(id,key)
  } finally { key.fill(0) }
 }
 suspend fun unlock(id: Long,pin: String,now: Long=System.currentTimeMillis()) {
  val result=write {
   val group=requireNotNull(dao.lockGroup(id)) { "잠금 정보를 찾을 수 없습니다" }
   require(now>=group.blockedUntil) { "입력 횟수를 초과했습니다. ${(group.blockedUntil-now)/1000+1}초 후 다시 시도하세요" }
   try {
    val key=vault.unlock(group,pin);dao.putLock(group.copy(failures=0,blockedUntil=0));Result.success(key)
   } catch(e: WrongPin) {
    val failures=group.failures+1;val wait=if(failures>=5)(30_000L shl (failures-5).coerceAtMost(5)).coerceAtMost(900_000L) else 0L
    dao.putLock(group.copy(failures=failures,blockedUntil=now+wait));Result.failure(e)
   }
  }
  val key=result.getOrThrow();try { vault.cache(id,key) } finally { key.fill(0) }
 }
 suspend fun disableLock(id: Long) {
  write {
   val root=requireNotNull(dao.get(id));require(root.id==root.lockRootId) { "상위 메모에서 잠금 설정을 해제하세요" };access(root)
   for(note in dao.all().filter { it.lockRootId==id }) { val value=vault.read(note);store(note.copy(lockRootId=null,sealedContent=null),value,note.updatedAt) };dao.eraseLock(id)
  };vault.forget(id)
 }
 suspend fun changePin(id: Long,oldPin: String,newPin: String) {
  require(newPin.matches(Regex("[0-9]{4}"))) { "숫자 4자리를 입력하세요" }
  unlock(id,oldPin)
  try { write { val group=requireNotNull(dao.lockGroup(id));dao.putLock(vault.rewrap(group,newPin)) } } finally { vault.forget(id) }
 }
 fun forgetKeys() = vault.clear()
}
