package com.personal.memostarter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=MemoApp::class,qualifiers="w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class TitleDragTests {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val vm get()=ViewModelProvider(compose.activity)[MainViewModel::class.java]
 private fun seed(count: Int=4): List<Long> {
  val ids=runBlocking {
   (count downTo 1).forEach { val id=vm.repo.create(null);vm.repo.save(id,Draft("메모 $it","본문 $it")) }
   TreeRules.ordered(vm.repo.snapshot().first).map { it.id }
  }
  compose.waitUntil(15_000) { compose.onAllNodesWithText("메모 1").fetchSemanticsNodes().isNotEmpty() }
  compose.waitForIdle();return ids
 }
 private fun title(id: Long)=compose.onNodeWithTag("note-title-$id",useUnmergedTree=true)
 private fun list()=compose.onNodeWithTag("memo-list")
 private fun point(id: Long, whitespace: Boolean=false): Offset {
  val bounds=title(id).fetchSemanticsNode().boundsInRoot
  return (if(whitespace)Offset(bounds.right-6,bounds.top+4) else Offset(bounds.left+24,bounds.top+20))-list().fetchSemanticsNode().boundsInRoot.topLeft
 }
 private fun hold(at: Offset) {
  compose.mainClock.autoAdvance=false
  list().performTouchInput { down(at) }
  compose.mainClock.advanceTimeBy(650);compose.waitForIdle()
 }
 private fun release() {
  list().performTouchInput { up() };compose.mainClock.autoAdvance=true
  compose.waitUntil(15_000) { compose.onAllNodesWithTag("floating-note").fetchSemanticsNodes().isEmpty() }
 }
 private fun order()=runBlocking { TreeRules.ordered(vm.repo.snapshot().first).map { it.id } }
 @Test fun shortTapOpensExistingMemoWithoutDragging() {
  val ids=seed();title(ids[0]).performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("읽기 모드").fetchSemanticsNodes().isNotEmpty() }
  assertEquals(ids[0],vm.editor.value?.id);compose.onNodeWithTag("floating-note").assertDoesNotExist()
 }
 @Test fun whitespaceLongPressLiftsWholeRowAndReflowsBeforeDropDownward() {
  val ids=seed();val from=point(ids[0],true);val to=point(ids[2])
  val secondBefore=title(ids[1]).fetchSemanticsNode().boundsInRoot.top
  val rowWidth=compose.onNodeWithTag("note-row-${ids[0]}",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.width
  hold(from)
  compose.onNodeWithTag("floating-note").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"드래그 중"))
  assertNull(vm.editor.value)
  compose.onNodeWithTag("floating-note").assert(hasAnyDescendant(hasText("메모 1")))
  compose.onNodeWithTag("floating-note").assert(hasAnyDescendant(hasText("본문 1")))
  assertTrue(compose.onNodeWithTag("floating-note").fetchSemanticsNode().boundsInRoot.width>rowWidth*1.02f)
  list().performTouchInput { moveTo(to) };compose.mainClock.advanceTimeBy(250);compose.waitForIdle()
  assertTrue(title(ids[1]).fetchSemanticsNode().boundsInRoot.top<secondBefore)
  assertEquals(ids,order())
  release();compose.waitUntil(15_000) { order()==listOf(ids[1],ids[2],ids[0],ids[3]) }
 }
 @Test fun titleTextLongPressMovesUpwardAndDoesNotOpenEditor() {
  val ids=seed();val from=point(ids[2]);val to=point(ids[0])
  hold(from);list().performTouchInput { moveTo(to) };compose.mainClock.advanceTimeBy(200);release()
  compose.waitUntil(15_000) { order()==listOf(ids[2],ids[0],ids[1],ids[3]) };assertNull(vm.editor.value)
 }
 @Test fun releaseWithoutMovementDoesNotOpenOrChangeOrder() {
  val ids=seed();hold(point(ids[1],true));release();assertEquals(ids,order());assertNull(vm.editor.value)
 }
 @Test fun cancelledDragKeepsOriginalOrder() {
  val ids=seed();val to=point(ids[2]);hold(point(ids[0]))
  list().performTouchInput { moveTo(to) };compose.mainClock.advanceTimeBy(200)
  list().performTouchInput { cancel() };compose.mainClock.autoAdvance=true;compose.waitForIdle()
  assertEquals(ids,order());compose.onNodeWithTag("floating-note").assertDoesNotExist()
 }
 @Test fun dragAtBottomAutoScrollsAndSurvivesSourceRecycling() {
  val ids=seed(24);hold(point(ids[0],true));val bounds=list().fetchSemanticsNode().boundsInRoot
  list().performTouchInput { moveTo(Offset(bounds.width/2,bounds.height-8)) }
  repeat(20) { compose.mainClock.advanceTimeBy(80);compose.waitForIdle() }
  val axis=list().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
  assertTrue("List should scroll while the finger is held at its lower edge",axis.value()>0f)
  compose.onNodeWithTag("floating-note").assertExists();release();assertTrue(order().indexOf(ids[0])>3)
 }
 @Test fun menuAndPlainScrollingStillWorkWithoutLongPress() {
  val ids=seed(24);compose.onAllNodesWithContentDescription("메모 메뉴")[0].performClick()
  compose.onNodeWithText("휴지통으로").assertExists();compose.onNodeWithText("메모 선택").performClick()
  compose.onNodeWithText("1개 선택").assertExists();compose.onNodeWithContentDescription("선택 취소").performClick()
  list().performTouchInput { swipeUp() };assertEquals(ids,order());compose.onNodeWithTag("floating-note").assertDoesNotExist()
 }
}
