package com.personal.memostarter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class EditorTests {
 @Test fun historySupportsTitleBodyChecklistUndoRedoAfter100Edits() {
  val h = EditHistory(Draft()); repeat(40) { h.edit(Draft(title = "title$it", body = "body$it"), "", it.toLong()) }
  repeat(30) { h.undo() }; assertEquals("title9", h.current.title); repeat(30) { h.redo() }; assertEquals("body39", h.current.body)
  val before = h.current; h.edit(before.copy(checks = listOf(CheckItem("x", 1, "check", true))), ""); h.undo(); assertEquals(before, h.current); h.redo(); assertTrue(h.current.checks.single().done)
  h.undo(); h.edit(Draft(body = "new branch"), ""); assertFalse(h.canRedo)
 }
 @Test fun autosaveDebouncesAndPreservesUndo() = runTest {
  val writes = mutableListOf<Draft>(); val store = object : ContentStore { override suspend fun save(id: Long, draft: Draft): Long { writes += draft; return 10 } }
  val s = EditorSession(1, Note(id = 1), Draft(), store, this)
  s.edit(Draft(body = "first")); advanceTimeBy(400); s.edit(Draft(body = "latest")); advanceTimeBy(599); runCurrent(); assertEquals(0, writes.size)
  advanceTimeBy(1); runCurrent(); assertEquals("latest", writes.single().body); assertEquals("저장됨", s.state.value.status)
  s.undo(); assertEquals("first", s.state.value.draft.body); s.saveNow(); assertEquals("first", writes.last().body); s.dispose()
 }
 @Test fun saveQueueNeverRevertsNewerContent() = runTest {
  val writes = mutableListOf<String>(); val store = object : ContentStore { override suspend fun save(id: Long, draft: Draft): Long { delay(300); writes += draft.body; return 1 } }
  val s = EditorSession(1, Note(id = 1), Draft(), store, this); s.edit(Draft(body = "old")); launch { s.saveNow() }; runCurrent(); s.edit(Draft(body = "new")); launch { s.saveNow() }
  advanceUntilIdle(); assertEquals(listOf("old", "new"), writes); assertEquals("new", s.state.value.draft.body); assertEquals("저장됨", s.state.value.status); s.dispose()
 }
 @Test fun failedSaveRetainsDraftAndRetries() = runTest {
  var fail = true; val store = object : ContentStore { override suspend fun save(id: Long, draft: Draft): Long { if (fail) error("full"); return 1 } }
  val s = EditorSession(1, Note(id = 1), Draft(), store, this); s.edit(Draft(body = "keep")); assertFalse(s.saveNow()); assertEquals("keep", s.state.value.draft.body)
  fail = false; assertTrue(s.saveNow()); assertEquals("저장됨", s.state.value.status); s.dispose()
 }
 @Test fun treeRejectsCyclesAndFourthLevelIncludingWholeSubtree() {
  val all = listOf(Note(id = 1), Note(id = 2, parentId = 1), Note(id = 3, parentId = 2), Note(id = 4))
  assertTrue(runCatching { TreeRules.validateMove(1, 3, all) }.isFailure); assertTrue(runCatching { TreeRules.validateMove(4, 3, all) }.isFailure); assertTrue(runCatching { TreeRules.validateMove(1, 4, all) }.isFailure)
  TreeRules.validateMove(2, 4, all); assertEquals(setOf(1L, 2L, 3L), TreeRules.descendants(1, all))
 }
}
