package com.personal.memostarter
import org.junit.Assert.*
import org.junit.Test

class ReorderTests {
 @Test fun arbitraryMovesKeepIdentityContentAndDates() {
  val notes=(1L..4L).map { Note(id=it,title="제목$it",body="본문$it",sortOrder=it,createdAt=10,updatedAt=20) }
  val down=ReorderRules.moveTo(1,4,notes)
  assertEquals(listOf(2L,3L,4L,1L),down.map { it.id })
  val up=ReorderRules.moveTo(1,2,down)
  assertEquals(listOf(1L,2L,3L,4L),up.map { it.id })
  for(n in down)assertEquals(notes.first { it.id==n.id },n.copy(sortOrder=notes.first { it.id==n.id }.sortOrder))
 }
 @Test fun childrenReorderOnlyWithinSameParentAndPinGroup() {
  val notes=listOf(Note(id=1),Note(id=2),Note(id=3,parentId=1,sortOrder=0),Note(id=4,parentId=1,sortOrder=1),Note(id=5,parentId=2),Note(id=6,parentId=1,pinned=true))
  assertEquals(listOf(4L,3L),ReorderRules.moveTo(3,4,notes).map { it.id })
  assertTrue(runCatching { ReorderRules.moveTo(3,5,notes) }.isFailure)
  assertTrue(runCatching { ReorderRules.moveTo(3,6,notes) }.isFailure)
 }
 @Test fun movingParentNeverReparentsDescendantsOrIncludesTrash() {
  val notes=listOf(Note(id=1),Note(id=2,sortOrder=1),Note(id=3,parentId=1),Note(id=4,parentId=3),Note(id=5,trashedAt=10))
  assertEquals(listOf(2L,1L),ReorderRules.moveTo(1,2,notes).map { it.id })
  assertTrue(ReorderRules.moveTo(1,1,notes).isEmpty())
  assertTrue(runCatching { ReorderRules.moveTo(1,5,notes) }.isFailure)
 }
}
