package com.personal.memostarter

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class IntegrityGuard(private val verify: () -> String, initiallyTrusted: Boolean = false) {
 val status = MutableStateFlow(if (initiallyTrusted) "OK" else "CHECKING")
 private val lock = Mutex()
 suspend fun refresh(): Boolean = lock.withLock {
  val result = withContext(Dispatchers.IO) { runCatching { verify() }.getOrDefault("UNREADABLE") }
  status.value = result; result == "OK"
 }
 suspend fun requireTrusted() {
  check(refresh()) { "앱 무결성을 확인할 수 없어 작업을 중단했습니다. 기존 메모는 삭제하지 않았습니다. 작성 중인 내용은 복사해 보관하고 공식 APK로 복구하세요. 앱을 삭제하지 마세요. (${status.value})" }
 }
 companion object {
  fun forApp(context: Context): IntegrityGuard = IntegrityGuard({
   if (BuildConfig.DEBUG) "OK" else verifyInstalled(context)
  }, BuildConfig.DEBUG)
  @Suppress("DEPRECATION")
  internal fun verifyInstalled(context: Context): String {
   val app = context.applicationInfo
   if ((app.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 || Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return "DEBUGGER"
   if (!app.splitSourceDirs.isNullOrEmpty()) return "UNSUPPORTED_SPLIT"
   val certificates = if (Build.VERSION.SDK_INT >= 28) {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return "SIGNER"
    info.apkContentsSigners.map { it.toByteArray() }
   } else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.map { it.toByteArray() } ?: return "SIGNER"
   if (context.packageName != BuildConfig.APPLICATION_ID) return "PACKAGE"
   return PackageIntegrity.verify(File(app.sourceDir), certificates, BuildConfig.RELEASE_CERT_PINS.split(',').filter { it.isNotBlank() }.toSet(), BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong())
  }
 }
}
