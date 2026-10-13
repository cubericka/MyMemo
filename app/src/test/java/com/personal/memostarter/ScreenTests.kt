package com.personal.memostarter
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MemoApp::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenTests {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>()
 @Test fun createEditSaveSearchAndReopen() {
  compose.onNodeWithContentDescription("새 메모").performClick(); compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
  compose.onAllNodes(hasSetTextAction())[0].performTextInput("검수 제목"); compose.onAllNodes(hasSetTextAction())[1].performTextInput("줄1\n검색용 고유단어"); compose.onNodeWithText("저장", useUnmergedTree = true).performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("저장됨").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithContentDescription("저장하고 목록으로").performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("메모 검색").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithContentDescription("메모 검색").performClick(); compose.onNode(hasSetTextAction()).performTextInput("고유단어")
  compose.waitUntil(15_000) { compose.onAllNodesWithText("검수 제목").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithText("검수 제목").performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("읽기 모드").fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasSetTextAction()).assertCountEquals(0);compose.onNodeWithText("수정").performClick()
  compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }; compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("줄1\n검색용 고유단어")
 }
 @Test fun templateSwitchKeepsTextAndOptionalFields() {
  compose.onNodeWithContentDescription("새 메모").performClick()
  compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
  compose.onAllNodes(hasSetTextAction())[0].performTextInput("자유로운 제목")
  compose.onAllNodes(hasSetTextAction())[1].performTextInput("바뀌어도 남는 본문")
  compose.onNodeWithText("템플릿 변경").performClick(); compose.onNodeWithText("아이디어").performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("기대 효과 (선택)"))
  compose.onNodeWithText("기대 효과 (선택)").performTextInput("보존할 생각")
  compose.onNodeWithText("템플릿 변경").performClick(); compose.onNodeWithText("빠른 메모").performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("상세 항목 · 선택 입력"))
  compose.onNodeWithText("상세 항목 · 선택 입력").performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("보존할 생각"))
  compose.onNodeWithText("보존할 생각").assertExists()
  compose.onNodeWithText("저장", useUnmergedTree = true).performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("저장됨").fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithContentDescription("저장하고 목록으로").performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("자유로운 제목").fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithText("자유로운 제목").assertExists()
  compose.onNodeWithText("A. 자유로운 제목").assertDoesNotExist()
  compose.onNodeWithContentDescription("메모 검색").performClick(); compose.onNode(hasSetTextAction()).performTextInput("보존할 생각")
  compose.onNodeWithText("자유로운 제목").performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("읽기 모드").fetchSemanticsNodes().isNotEmpty() };compose.onNodeWithText("수정").performClick()
  compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
  compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("바뀌어도 남는 본문")
 }
 @Test fun manualSaveAfterAutosaveShowsConfirmation() {
  compose.onNodeWithContentDescription("새 메모").performClick()
  compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
  compose.onAllNodes(hasSetTextAction())[1].performTextInput("자동 저장 이후 수동 저장")
  compose.waitUntil(15_000) { compose.onAllNodesWithText("저장됨").fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithText("저장",useUnmergedTree=true).performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("저장했습니다").fetchSemanticsNodes().isNotEmpty() }
  compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("자동 저장 이후 수동 저장")
 }
 @Test fun manualSaveIsQueuedDuringAnotherAction() {
  compose.onNodeWithContentDescription("새 메모").performClick()
  compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
  val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
  compose.runOnIdle {
   val session=requireNotNull(vm.editor.value)
   session.edit(session.state.value.draft.copy(body="대기 후 저장"))
   vm.busy.value=true;vm.save()
  }
  compose.onNodeWithText("저장했습니다").assertDoesNotExist()
  compose.runOnIdle { vm.busy.value=false }
  compose.waitUntil(15_000) { compose.onAllNodesWithText("저장했습니다").fetchSemanticsNodes().isNotEmpty() }
  val saved=kotlinx.coroutines.runBlocking { vm.repo.read(requireNotNull(vm.editor.value).id).second }
  org.junit.Assert.assertEquals("대기 후 저장",saved.body)
 }
}
