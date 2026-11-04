package com.personal.memostarter

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=MemoApp::class)
class SelectionTests {
 private lateinit var db: MemoDatabase
 private lateinit var repo: MemoRepository
 @Before fun setup() {
  db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),MemoDatabase::class.java).build()
  repo=MemoRepository(db,vault=NoteVault(TestDeviceWrap()))
 }
 @After fun close() { db.close() }
 @Test fun overlappingSelectionPreservesOldTrashAndOtherNotes() = runBlocking {
  val parent=repo.create(null); val child=repo.create(parent); val old=repo.create(parent); val other=repo.create(null); val keep=repo.create(null)
  repo.trash(old); val batch=db.dao().get(old)!!.trashBatch
  repo.trashMany(setOf(parent,child,other))
  val all=repo.snapshot().first
  assertEquals(batch,all.single { it.id==old }.trashBatch)
  assertNull(all.single { it.id==keep }.trashedAt)
  assertEquals(1,all.filter { it.id in setOf(parent,child,other) }.map { it.trashBatch }.distinct().size)
  assertTrue(all.filter { it.id!=keep }.all { it.trashedAt!=null })
  repo.restore(parent,null)
  assertNull(db.dao().get(child)!!.trashedAt); assertNotNull(db.dao().get(old)!!.trashedAt)
 }
 @Test fun lockedOrMissingSelectionRollsBackWholeBatch() = runBlocking {
  val plain=repo.create(null); val locked=repo.create(null)
  repo.enableLock(locked,"1234");repo.forgetKeys()
  val before=repo.snapshot()
  assertTrue(runCatching { repo.trashMany(setOf(plain,locked)) }.exceptionOrNull() is UnlockRequired)
  assertEquals(before,repo.snapshot())
  assertTrue(runCatching { repo.trashMany(setOf(plain,999999)) }.isFailure)
  assertEquals(before,repo.snapshot())
 }
 @Test fun multiWidgetPreservesOrderAndNeverExposesProtectedContent() = runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val choice=WidgetChoice().withNotes(listOf(3,1,2,3)).copy(hide=false)
  WidgetSettings.put(context,901,choice)
  WidgetSettings.remap(context,intArrayOf(901),intArrayOf(902))
  assertEquals(listOf(3L,1L,2L),WidgetSettings.get(context,902).noteIds)
  val notes=listOf(Note(id=1,title="secret",body="hidden",lockRootId=1), Note(id=2,title="deleted",trashedAt=1),Note(id=3,title="normal",body="visible"))
  val entries=widgetEntries(choice,notes,emptyList())
  assertEquals(listOf(3L,1L),entries.map { it.id })
  assertEquals("잠긴 메모",entries.last().title)
  assertFalse(entries.toString().contains("secret"));assertFalse(entries.toString().contains("hidden"))
  assertTrue(widgetEntries(choice.copy(hide=true),notes,emptyList()).none { it.body.contains("visible") })
  WidgetSettings.remove(context,902)
 }
}
