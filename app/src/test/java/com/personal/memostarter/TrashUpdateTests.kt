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
class TrashUpdateTests {
 private lateinit var db: MemoDatabase;private lateinit var repo: MemoRepository
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),MemoDatabase::class.java).build();repo=MemoRepository(db,vault=NoteVault(TestDeviceWrap()))}
 @After fun close(){db.close()}
 @Test fun selectedTreesAreDeletedOnceAndOtherNotesRemain()=runBlocking {
  val parent=repo.create(null);val child=repo.create(parent);val other=repo.create(null);val live=repo.create(null)
  repo.trashMany(setOf(parent,other));repo.permanentlyDeleteMany(setOf(parent,child))
  assertEquals(setOf(other,live),repo.snapshot().first.map { it.id }.toSet())
  repo.permanentlyDeleteMany(setOf(other));assertEquals(listOf(live),repo.snapshot().first.map { it.id })
 }
 @Test fun activeOrStaleSelectionRejectsWholeTransaction()=runBlocking {
  val a=repo.create(null);val b=repo.create(null);repo.trash(a)
  assertTrue(runCatching{repo.permanentlyDeleteMany(setOf(a,b))}.isFailure)
  assertTrue(runCatching{repo.permanentlyDeleteMany(setOf(a,99999))}.isFailure)
  assertEquals(setOf(a,b),repo.snapshot().first.map { it.id }.toSet())
 }
 @Test fun encryptedTrashCanBeDeletedWithoutIndividualPin()=runBlocking {
  val parent=repo.create(null);val child=repo.create(parent);repo.enableLock(parent,"9876");repo.trash(parent);repo.forgetKeys()
  repo.permanentlyDeleteMany(setOf(parent));assertTrue(repo.snapshot().first.isEmpty());assertNull(db.dao().lockGroup(parent));assertNull(db.dao().get(child))
 }
}
