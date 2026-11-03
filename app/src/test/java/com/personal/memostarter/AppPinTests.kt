package com.personal.memostarter
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=MemoApp::class)
class AppPinTests {
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private val prefs get()=context.getSharedPreferences("app-pin-test",Context.MODE_PRIVATE)
 private lateinit var device: TestDeviceWrap
 @Before fun setup(){prefs.edit().clear().commit();device=TestDeviceWrap()}
 @Test fun restartRequiresPinAndRecoversEnrolledKeysWithoutPlaintextPin() {
  val lock=AppPinLock(prefs,device);val key=VaultCrypto.random(32)
  lock.enable("2468");lock.remember(42,key);lock.lock()
  assertFalse(lock.unlocked.value);assertTrue(runCatching { lock.recover(42) }.exceptionOrNull() is AppUnlockRequired)
  val restart=AppPinLock(prefs,device);assertTrue(restart.enabled.value);assertFalse(restart.unlocked.value)
  assertTrue(runCatching { restart.unlock("0000") }.exceptionOrNull() is WrongPin)
  restart.unlock("2468");assertArrayEquals(key,restart.recover(42))
  val record=org.json.JSONObject(prefs.getString("record",null)!!)
  assertFalse(record.has("pin"));assertFalse(record.has("password"));assertNotEquals("2468",record.getString("box"))
 }
 @Test fun failedAttemptsSurviveRestartAndBlockEvenCorrectPinUntilCooldown() {
  val lock=AppPinLock(prefs,device);lock.enable("1234");lock.lock()
  repeat(5){assertTrue(runCatching{lock.unlock("0000",1_000_000)}.exceptionOrNull() is WrongPin)}
  val restart=AppPinLock(prefs,device)
  assertTrue(runCatching{restart.unlock("1234",1_000_001)}.isFailure);assertFalse(restart.unlocked.value)
  restart.unlock("1234",1_030_001);assertTrue(restart.unlocked.value)
 }
 @Test fun changingAndDisablingPinPreservesLegacyKeys() {
  val lock=AppPinLock(prefs,device);val key=VaultCrypto.random(32)
  lock.enable("1234");lock.remember(7,key);lock.change("1234","4321");lock.lock()
  assertTrue(runCatching{lock.unlock("1234")}.isFailure);lock.unlock("4321");assertArrayEquals(key,lock.recover(7))
  assertTrue(runCatching{lock.disable("0000")}.isFailure);assertTrue(lock.enabled.value)
  lock.disable("4321");val restart=AppPinLock(prefs,device)
  assertFalse(restart.enabled.value);assertTrue(restart.unlocked.value);assertArrayEquals(key,restart.recover(7))
 }
 @Test fun corruptConfigurationFailsClosedWithoutResettingData() {
  prefs.edit().putString("record","broken-record").commit();val lock=AppPinLock(prefs,device)
  assertTrue(lock.enabled.value);assertFalse(lock.unlocked.value);assertTrue(runCatching{lock.unlock("1234")}.isFailure)
  assertEquals("broken-record",prefs.getString("record",null))
 }
 @Test fun oldNoteUnlockWithoutAppPinDoesNotPersistAnAuthenticationBypass()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,MemoDatabase::class.java).build()
  try {
   val lock=AppPinLock(prefs,device)
   fun repo()=MemoRepository(db,vault=NoteVault(device),readAllowed=lock::requireOpen,recoverKey=lock::recover,rememberKey=lock::remember)
   val first=repo();val id=first.create(null);first.save(id,Draft("보호된 메모","기존 내용"))
   first.enableLock(id,"9876");first.forgetKeys();first.unlock(id,"9876")
   assertEquals("기존 내용",first.read(id).second.body)
   assertTrue(runCatching { repo().read(id) }.exceptionOrNull() is UnlockRequired)
   assertNull(prefs.getString("record",null))
  } finally { db.close() }
 }
 @Test fun legacyEncryptedNoteEnrollsOnceAndIsReadableAfterCommonPinChange()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,MemoDatabase::class.java).build()
  try {
   val lock=AppPinLock(prefs,device)
   fun repo()=MemoRepository(db,authorize={lock.requireOpen()},vault=NoteVault(device),readAllowed=lock::requireOpen,recoverKey=lock::recover,rememberKey=lock::remember)
   val first=repo();val id=first.create(null);first.save(id,Draft("기존 제목","보존할 내용"));first.enableLock(id,"9876");first.forgetKeys()
   val encrypted=db.dao().get(id)!!.sealedContent
   lock.enable("1234");first.unlock(id,"9876");first.forgetKeys();lock.change("1234","4321");lock.lock()
   assertTrue(runCatching { first.read(id) }.exceptionOrNull() is AppUnlockRequired)
   assertTrue(runCatching { first.create(null) }.exceptionOrNull() is AppUnlockRequired)
   lock.unlock("4321");val next=repo();assertEquals("보존할 내용",next.read(id).second.body)
   assertEquals(encrypted,db.dao().get(id)!!.sealedContent)
  } finally { db.close() }
 }
}
