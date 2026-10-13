package com.personal.memostarter

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MemoApp::class)
class TemplateTests {
 private val context get() = ApplicationProvider.getApplicationContext<Context>()
 @Test fun switchingAllTemplatesPreservesFreeTextDetailsChecklistAndUndo() = runTest {
  val db = Room.inMemoryDatabaseBuilder(context, MemoDatabase::class.java).build()
  try {
   val repo = MemoRepository(db); val id = repo.create(null)
   val original = Draft("제목", "자유 본문\n둘째 줄", listOf(CheckItem("c", id, "할 일", true)), "idea", mapOf("기대 효과" to "내용 유지", "출처 URL" to "https://example.org/?q=한글", "태그" to "#개인"))
   repo.save(id, original); val (note, initial) = repo.read(id)
   val session = EditorSession(id, note, initial, repo, backgroundScope)
   for (template in MemoTemplates.all) {
    session.selectTemplate(template.id); assertTrue(session.saveNow())
    assertEquals(original.copy(template = template.id), repo.read(id).second)
   }
   session.undo(); assertEquals("plan", session.state.value.draft.template)
   session.redo(); assertEquals("information", session.state.value.draft.template)
   repo.trash(id); repo.restore(id, null)
   assertEquals(original.copy(template = "information"), repo.read(id).second)
   assertTrue(TemplateDetails.searchable(repo.read(id).first.details).contains("내용 유지"))
   session.dispose()
  } finally { db.close() }
 }
 @Test fun v2UpgradePreservesTreeTrashOrderChecksAndNewTemplateOnReopen() = runBlocking {
  val name = "v2-${UUID.randomUUID()}.db"; val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
  val schema = JSONObject(javaClass.getResourceAsStream("/schema-v2.json")!!.bufferedReader().readText()).getJSONObject("database")
  SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
   val entities = schema.getJSONArray("entities")
   for (i in 0 until entities.length()) {
    val e = entities.getJSONObject(i); val table = e.getString("tableName")
    raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
    val indices = e.getJSONArray("indices"); for (j in 0 until indices.length()) raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
   }
   raw.execSQL("INSERT INTO notes VALUES (10,NULL,'부모','본문',100,200,1,7,NULL,NULL)")
   raw.execSQL("INSERT INTO notes VALUES (11,10,'하위','보존',101,201,0,4,NULL,NULL)")
   raw.execSQL("INSERT INTO notes VALUES (12,10,'휴지통','복원 가능',102,202,0,5,300,'batch')")
   raw.execSQL("INSERT INTO checks VALUES ('check',11,'완료 내용',1,0)")
   raw.version = 2
  }
  val upgraded = MemoDatabase.open(context, name); val repo = MemoRepository(upgraded)
  val (parent, p) = repo.read(10); assertEquals("본문", p.body); assertTrue(parent.pinned); assertEquals(7L, parent.sortOrder); assertEquals(200L, parent.updatedAt); assertEquals("quick", p.template); assertTrue(p.details.isEmpty())
  val (child, draft) = repo.read(11); assertEquals(10L, child.parentId); assertTrue(draft.checks.single().done)
  assertEquals("batch", repo.snapshot().first.find { it.id == 12L }!!.trashBatch)
  repo.save(11, draft.copy(template = "study", details = mapOf("질문" to "복습할 내용"))); upgraded.close()
  val reopened = MemoDatabase.open(context, name)
  try { val restored = MemoRepository(reopened).read(11).second; assertEquals("study", restored.template); assertEquals("복습할 내용", restored.details["질문"]); assertTrue(restored.checks.single().done) } finally { reopened.close(); context.deleteDatabase(name) }
 }
}
