package com.personal.memostarter
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=MemoApp::class)
@LooperMode(LooperMode.Mode.PAUSED)
class UpdateScreenTests {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val vm get()=ViewModelProvider(compose.activity)[MainViewModel::class.java]
 @Test fun aboutShowsCreatorAndVersionAndMainHasCommonPinEntry() {
  compose.onNodeWithContentDescription("앱 잠금 설정").assertExists()
  compose.onNodeWithText("설정").performClick();compose.onNodeWithText("앱 공통 PIN").assertExists()
  compose.onNodeWithText("어플 정보").performClick()
  compose.onNodeWithText("제작자: 아카라이브 모르윈").assertExists()
  compose.onNodeWithText("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertExists()
 }
 @Test fun trashRequiresConfirmationAndHasNoRestoreAction() {
  runBlocking { val a=vm.repo.create(null);vm.repo.save(a,Draft("삭제 대상"));vm.repo.trash(a) }
  compose.onNodeWithText("설정").performClick();compose.onNodeWithText("휴지통").performClick()
  compose.onNodeWithText("복원").assertDoesNotExist()
  compose.onNodeWithText("전체 비우기").performClick();compose.onNodeWithText("취소").performClick()
  assertEquals(1,runBlocking{vm.repo.snapshot().first.size})
  compose.onNodeWithText("전체 비우기").performClick();compose.onNodeWithText("영구 삭제 확인").performClick()
  compose.waitUntil(15000) { compose.onAllNodesWithText("휴지통이 비어 있어요").fetchSemanticsNodes().isNotEmpty() }
 }
 @Test fun lockedAppHidesMemoListAndNewMemoAction() {
  compose.runOnIdle { vm.appLock.enabled.value=true;vm.appLock.unlocked.value=false }
  compose.onNodeWithText("앱 PIN 4자리").assertExists();compose.onNodeWithContentDescription("새 메모").assertDoesNotExist()
 }
 @Test fun selectedTrashDeletionLeavesUnselectedMemo() {
  runBlocking { listOf("선택한 메모","남길 메모").forEach { title->
   val id=vm.repo.create(null);vm.repo.save(id,Draft(title));vm.repo.trash(id)
  } }
  compose.onNodeWithText("설정").performClick();compose.onNodeWithText("휴지통").performClick()
  compose.onNodeWithText("선택한 메모").performClick();compose.onNodeWithText("선택 삭제 (1)").performClick()
  assertEquals(2,runBlocking { vm.repo.snapshot().first.size })
  compose.onNodeWithText("영구 삭제 확인").performClick()
  compose.waitUntil(15000) { runBlocking { vm.repo.snapshot().first.size==1 } }
  compose.onNodeWithText("남길 메모").assertExists();compose.onNodeWithText("선택한 메모").assertDoesNotExist()
 }
}
