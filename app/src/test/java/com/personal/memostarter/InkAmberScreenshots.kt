package com.personal.memostarter

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Native Robolectric renders, not screenshots from a physical device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=MemoApp::class,qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class InkAmberScreenshots {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun capture(name: String) {
  compose.waitForIdle()
  compose.runOnIdle {
   val view=compose.activity.window.decorView
   val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
   view.draw(Canvas(bitmap))
   val file=File("build/reports/ink-amber/$name.png");file.parentFile.mkdirs()
   file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
  }
 }
 @Test fun renderExistingScreensAndSelection() {
  val vm=ViewModelProvider(compose.activity)[MainViewModel::class.java]
  runBlocking {
   for ((title,body) in listOf("읽고 싶은 책" to "이번 달 읽을 책 목록", "주간 할 일" to "회의 자료 준비 · 디자인 피드백", "프로젝트 회의 정리" to "다음 주 일정과 역할을 정리합니다.")) {
    val id=vm.repo.create(null);vm.repo.save(id,Draft(title,body))
   }
  }
  compose.waitUntil(15_000) { compose.onAllNodesWithText("프로젝트 회의 정리").fetchSemanticsNodes().isNotEmpty() }
  capture("01-list")
  compose.onNodeWithContentDescription("메모 검색").performClick();capture("06-search")
  compose.onNodeWithContentDescription("메모 검색").performClick()
  compose.onNodeWithContentDescription("메모 선택").performClick()
  compose.onNodeWithText("프로젝트 회의 정리").performClick()
  compose.onNodeWithText("1개 선택").assertExists();capture("02-selection")
  compose.onNodeWithContentDescription("선택 취소").performClick()
  compose.onNodeWithText("프로젝트 회의 정리").performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithText("읽기 모드").fetchSemanticsNodes().isNotEmpty() }
  capture("03-reading")
  compose.onNodeWithText("수정").performClick()
  compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size>=2 }
  capture("04-editor")
  compose.onNodeWithContentDescription("저장하고 목록으로").performClick()
  compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("메모 검색").fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithText("설정").performClick()
  compose.onNodeWithText("앱 공통 PIN").assertExists();capture("05-settings")
 }
}
