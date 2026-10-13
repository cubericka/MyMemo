package com.personal.memostarter

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MemoApp::class)
class IntegrityTests {
 @Test fun failureBlocksAllWritesKeepsDbAndDraftAndAllowsRecovery() = runTest {
  val context = ApplicationProvider.getApplicationContext<Context>()
  val db = Room.inMemoryDatabaseBuilder(context, MemoDatabase::class.java).build()
  var verdict = "OK"; val guard = IntegrityGuard({ verdict }); val repo = MemoRepository(db, authorize = guard::requireTrusted)
  try {
   val id = repo.create(null); repo.save(id, Draft("보존", "기존 내용")); val child = repo.create(id)
   val (note, draft) = repo.read(id); val session = EditorSession(id, note, draft, repo, backgroundScope)
   session.edit(draft.copy(body = "작성 중인 내용")); verdict = "CONTENT_HASH"
   assertFalse(session.saveNow()); assertEquals("작성 중인 내용", session.state.value.draft.body)
   assertEquals("기존 내용", repo.read(id).second.body)
   assertTrue(runCatching { repo.create(null) }.isFailure)
   assertTrue(runCatching { repo.trash(id) }.isFailure)
   assertTrue(runCatching { repo.pin(id) }.isFailure)
   assertTrue(runCatching { repo.move(child, null) }.isFailure)
   assertTrue(runCatching { repo.reorder(id, 1) }.isFailure)
   assertTrue(runCatching { repo.permanentlyDelete(id) }.isFailure)
   assertTrue(runCatching { repo.restore(id, null) }.isFailure)
   assertNull(repo.read(id).first.trashedAt); assertEquals(id, repo.read(child).first.parentId)
   verdict = "OK"; assertTrue(session.saveNow()); assertEquals("작성 중인 내용", repo.read(id).second.body); session.dispose()
  } finally { db.close() }
 }
 @Test fun verifierExceptionFailsClosedWithoutThrowingIntoUi() = runTest {
  val guard=IntegrityGuard({ throw IllegalStateException("IO") })
  assertFalse(guard.refresh()); assertEquals("UNREADABLE",guard.status.value)
 }
}
