package com.personal.memostarter

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec
import java.util.UUID
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

class TestDeviceWrap : DeviceWrap {
 private val key=SecretKeySpec(VaultCrypto.random(32),"AES")
 override fun seal(data: ByteArray,aad: ByteArray)=VaultCrypto.seal(key,data,aad)
 override fun open(data: String,aad: ByteArray)=VaultCrypto.open(key,data,aad)
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=MemoApp::class)
class VaultTests {
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private lateinit var db: MemoDatabase;private lateinit var repo: MemoRepository;private lateinit var device: TestDeviceWrap
 @Before fun setup(){device=TestDeviceWrap();db=Room.inMemoryDatabaseBuilder(context,MemoDatabase::class.java).build();repo=MemoRepository(db,vault=NoteVault(device))}
 @After fun close(){db.close()}
 @Test fun encryptsWholeTreeAndHidesSearchAndWidgetSource()=runBlocking {
  val root=repo.create(null);val child=repo.create(root);val grand=repo.create(child)
  val original=Draft("비밀 제목","본문 SECRET-NOTE",listOf(CheckItem("c",child,"SECRET-CHECK",true)),"idea",mapOf("기대 효과" to "SECRET-DETAIL"))
  repo.save(child,original);repo.save(grand,Draft(body="grand secret"));repo.enableLock(root,"1234")
  val raw=repo.snapshot();assertTrue(raw.first.all{it.lockRootId==root && it.body.isEmpty() && it.title=="잠긴 메모" && it.details=="{}"});assertTrue(raw.second.isEmpty())
  assertEquals(original,repo.read(child).second);repo.forgetKeys()
  assertTrue(repo.notes.first().all{it.body.isEmpty()});assertTrue(repo.checks.first().isEmpty())
  assertTrue(runCatching{repo.read(grand)}.exceptionOrNull() is UnlockRequired)
  repo.unlock(root,"1234");assertEquals(original,repo.read(child).second)
 }
 @Test fun wrongPinAndPersistentCooldownNeverModifyContent()=runBlocking {
  val id=repo.create(null);repo.save(id,Draft(body="preserve"));repo.enableLock(id,"4321");repo.forgetKeys()
  repeat(5){assertTrue(runCatching{repo.unlock(id,"0000",1_000_000L)}.exceptionOrNull() is WrongPin)}
  assertEquals(5,db.dao().lockGroup(id)!!.failures)
  val restart=MemoRepository(db,vault=NoteVault(device))
  assertTrue(runCatching{restart.unlock(id,"4321",1_000_001L)}.isFailure)
  restart.unlock(id,"4321",1_031_000L);assertEquals("preserve",restart.read(id).second.body);assertEquals(0,db.dao().lockGroup(id)!!.failures)
 }
 @Test fun lockedWritesBlockedNewChildrenInheritAndBoundaryMoveDenied()=runBlocking {
  val root=repo.create(null);val other=repo.create(null);repo.enableLock(root,"1111");val child=repo.create(root);repo.save(child,Draft(body="secret"));repo.forgetKeys()
  assertTrue(runCatching{repo.save(child,Draft(body="bad"))}.exceptionOrNull() is UnlockRequired)
  assertTrue(runCatching{repo.trash(root)}.exceptionOrNull() is UnlockRequired)
  repo.unlock(root,"1111");assertTrue(runCatching{repo.move(child,other)}.isFailure);assertTrue(runCatching{repo.move(other,root)}.isFailure)
  assertEquals(root,repo.read(child).first.parentId);assertEquals(root,repo.read(child).first.lockRootId)
 }
 @Test fun lockRemovalAndTrashRestorePreserveFieldsAndIds()=runBlocking {
  val root=repo.create(null);val child=repo.create(root);val d=Draft("child","text",listOf(CheckItem("c",child,"check",true)),"plan",mapOf("이유" to "why"))
  repo.save(child,d);repo.enableLock(root,"9876");repo.trash(root);repo.forgetKeys();repo.unlock(root,"9876");repo.restore(root,null);assertEquals(d,repo.read(child).second)
  repo.disableLock(root);repo.forgetKeys();assertEquals(d,repo.read(child).second);assertNull(repo.read(child).first.lockRootId);assertNull(db.dao().lockGroup(root))
 }
 @Test fun eachRootHasIndependentPinAndCiphertextBindsNoteId()=runBlocking {
  val a=repo.create(null);val b=repo.create(null);repo.save(a,Draft(body="a"));repo.save(b,Draft(body="b"));repo.enableLock(a,"1234");repo.enableLock(b,"5678");repo.forgetKeys();repo.unlock(a,"1234")
  assertTrue(runCatching{repo.read(b)}.exceptionOrNull() is UnlockRequired);assertTrue(runCatching{repo.unlock(b,"1234")}.exceptionOrNull() is WrongPin)
  val raw=db.dao().get(a)!!;assertTrue(runCatching{repo.vault.read(raw.copy(id=b))}.isFailure);assertEquals("a",repo.read(a).second.body)
 }
 @Test fun invalidPinAndFailedLockAreAtomic()=runBlocking {
  val root=repo.create(null);val child=repo.create(root);repo.save(child,Draft(body="keep"))
  assertTrue(runCatching{repo.enableLock(root,"123")}.isFailure);assertNull(db.dao().lockGroup(root));assertNull(repo.read(child).first.lockRootId);assertEquals("keep",repo.read(child).second.body)
 }
 @Test fun encryptedStorageReopensWithFreshVaultAndNoPlaintextRecord()=runBlocking {
  val name="vault-${UUID.randomUUID()}.db";val disk=MemoDatabase.open(context,name);val r=MemoRepository(disk,vault=NoteVault(device));val id=r.create(null);r.save(id,Draft(body="SECRET-CONTENT-UNIQUE"));r.enableLock(id,"1234");r.forgetKeys();disk.close()
  val next=MemoDatabase.open(context,name)
  try { val fresh=MemoRepository(next,vault=NoteVault(device));assertTrue(runCatching{fresh.read(id)}.exceptionOrNull() is UnlockRequired);fresh.unlock(id,"1234");assertEquals("SECRET-CONTENT-UNIQUE",fresh.read(id).second.body);assertFalse(next.dao().get(id)!!.sealedContent!!.contains("SECRET-CONTENT-UNIQUE")) } finally {next.close();context.deleteDatabase(name)}
 }
 @Test fun v3MigrationKeepsExistingContentsAndStartsUnlocked()=runBlocking {
  val name="v3-${UUID.randomUUID()}.db";val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
  val entities=JSONObject(javaClass.getResourceAsStream("/schema-v3.json")!!.bufferedReader().readText()).getJSONObject("database").getJSONArray("entities")
  SQLiteDatabase.openOrCreateDatabase(path,null).use { raw ->
   for(i in 0 until entities.length()){val e=entities.getJSONObject(i);val table=e.getString("tableName");raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
   raw.execSQL("INSERT INTO notes VALUES (1,NULL,'기존','유지',100,200,1,7,NULL,NULL,'idea','{\"기대 효과\":\"보존\"}')");raw.version=3
  }
  val upgraded=MemoDatabase.open(context,name)
  try {val(n,d)=MemoRepository(upgraded,vault=NoteVault(device)).read(1);assertEquals("유지",d.body);assertEquals("보존",d.details["기대 효과"]);assertNull(n.lockRootId);assertTrue(n.pinned)}finally{upgraded.close();context.deleteDatabase(name)}
 }
}
