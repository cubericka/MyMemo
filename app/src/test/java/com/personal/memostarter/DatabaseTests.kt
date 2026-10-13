package com.personal.memostarter
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MemoApp::class)
class DatabaseTests {
 private lateinit var db: MemoDatabase; private lateinit var repo: MemoRepository
 private val context get() = ApplicationProvider.getApplicationContext<Context>()
 @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, MemoDatabase::class.java).build(); repo = MemoRepository(db) }
 @After fun finish() { db.close() }
 @Test fun dragOrderPersistsAfterReopen() = runBlocking {
  val name="drag-${UUID.randomUUID()}.db"
  var disk=MemoDatabase.open(context,name)
  try {
   val r=MemoRepository(disk)
   val a=r.create(null);val b=r.create(null);val c=r.create(null);val child=r.create(a)
   r.save(child,Draft("child","preserved",listOf(CheckItem("drag-check",child,"item",true))))
   val before=r.snapshot()
   assertTrue(runCatching { r.reorderTo(a,child) }.isFailure)
   assertEquals(before,r.snapshot())
   r.reorderTo(a,c)
   disk.close();disk=MemoDatabase.open(context,name)
   val reopened=MemoRepository(disk)
   assertEquals(listOf(b,c,a),TreeRules.ordered(reopened.snapshot().first.filter { it.parentId==null }).map { it.id })
   assertEquals(a,reopened.read(child).first.parentId)
   assertEquals("preserved",reopened.read(child).second.body)
   assertTrue(reopened.read(child).second.checks.single().done)
  } finally { disk.close();context.deleteDatabase(name) }
  Unit
 }
 @Test fun hierarchyChecklistTrashRestore() = runBlocking {
  val a = repo.create(null); val child = repo.create(a); val grand = repo.create(child)
  repo.save(child, Draft("준비물", "passport", listOf(CheckItem("c", child, "여권", true)))); repo.trash(a); assertTrue(repo.snapshot().first.all { it.trashedAt != null }); repo.restore(a, null)
  val all = repo.snapshot(); assertTrue(all.first.all { it.trashedAt == null }); assertEquals(a, all.first.find { it.id == child }!!.parentId); assertEquals(child, all.first.find { it.id == grand }!!.parentId); assertTrue(repo.read(child).second.checks.single().done)
 }
 @Test fun restoringOneChildToRootPreservesOthersAndOldTrash() = runBlocking {
  val a = repo.create(null); val b = repo.create(a); val c = repo.create(a); repo.trash(c); repo.trash(a); repo.restore(b, null); repo.restore(a, null)
  val all = repo.snapshot().first; assertNull(all.find { it.id == b }!!.parentId); assertNotNull(all.find { it.id == c }!!.trashedAt); assertNull(all.find { it.id == a }!!.trashedAt)
 }
 @Test fun invalidMoveAndContentFailureRollback() = runBlocking {
  val a = repo.create(null); val b = repo.create(a); val c = repo.create(b); assertTrue(runCatching { repo.move(a, c) }.isFailure); assertNull(repo.read(a).first.parentId)
  repo.save(a, Draft(body = "original", checks = listOf(CheckItem("same", a, "keep")))); val other = repo.create(null)
  assertTrue(runCatching { repo.save(other, Draft(body = "bad", checks = listOf(CheckItem("same", other, "collision")))) }.isFailure); assertEquals("", repo.read(other).second.body); assertEquals("keep", repo.read(a).second.checks.single().text)
 }
 @Test fun reorderAndMoveNeverOverwriteContent() = runBlocking {
  val a = repo.create(null); val b = repo.create(null); val c = repo.create(null); repo.save(b, Draft("title", "body")); repo.reorder(b, -1); assertEquals(b, TreeRules.ordered(repo.snapshot().first).first().id)
  repo.pin(c); assertEquals(c, TreeRules.ordered(repo.snapshot().first).first().id); repo.move(b, a); assertEquals("body", repo.read(b).second.body); assertEquals(a, repo.read(b).first.parentId)
 }
 @Test fun permanentDeleteCascadesOnlySelectedSubtree() = runBlocking {
  val a = repo.create(null); val b = repo.create(a); val keep = repo.create(null); repo.save(b, Draft(checks = listOf(CheckItem("c", b, "gone")))); repo.trash(a); repo.permanentlyDelete(a)
  assertEquals(listOf(keep), repo.snapshot().first.map { it.id }); assertTrue(repo.snapshot().second.isEmpty())
 }
 @Test fun legacyMigrationPreservesIdsTextTimestampAndPersistsOnReopen() = runBlocking {
  val name = "migration-${UUID.randomUUID()}.db"; val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
  SQLiteDatabase.openOrCreateDatabase(path, null).use { it.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, updated_at INTEGER NOT NULL)"); it.execSQL("INSERT INTO notes VALUES (7,'기존 제목','줄1' || char(10) || '줄2',12345)"); it.version = 1 }
  val migrated = MemoDatabase.open(context, name); val r = MemoRepository(migrated); val (note, draft) = r.read(7)
  assertEquals("기존 제목", draft.title); assertEquals("줄1\n줄2", draft.body); assertEquals(12345L, note.updatedAt); assertEquals(12345L, note.createdAt)
  val child = r.create(7); r.save(child, Draft(checks = listOf(CheckItem("persist", child, "saved", true)))); migrated.close()
  val reopened = MemoDatabase.open(context, name); assertTrue(MemoRepository(reopened).read(child).second.checks.single().done); reopened.close(); context.deleteDatabase(name); Unit
 }
}
