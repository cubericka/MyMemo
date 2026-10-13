package com.personal.memostarter

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/** Executes the real Android PackageManager adapter over actual signed Release APK fixtures.
 * Robolectric supplies the installed-package metadata; this is not a physical APK installation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], application = MemoApp::class)
class InstalledIntegrityTests {
 private lateinit var directory: File
 private val context get() = ApplicationProvider.getApplicationContext<Context>()
 @Before fun fixtures() {
  val path=System.getProperty("mymemo.securityFixtures")
  Assume.assumeTrue("Generate sealed APK fixtures using tools/test_apks.py first", !path.isNullOrBlank())
  directory=File(path!!)
 }
 @Suppress("DEPRECATION")
 private fun install(name: String, signer: String = "validation", pastApproved: Boolean = false) {
  val app=context.applicationInfo
  app.flags=app.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv(); app.sourceDir=File(directory,name).absolutePath; app.splitSourceDirs=null
  val current=Signature(File(directory,"$signer.der").readBytes())
  val info=PackageInfo(); info.packageName=context.packageName; info.applicationInfo=app; info.versionCode=342; info.signatures=arrayOf(current)
  if(Build.VERSION.SDK_INT>=28) {
   info.signingInfo=SigningInfo()
   Shadows.shadowOf(info.signingInfo).setSignatures(arrayOf(current))
   if(pastApproved) Shadows.shadowOf(info.signingInfo).setPastSigningCertificates(arrayOf(Signature(File(directory,"validation.der").readBytes()),current))
  }
  Shadows.shadowOf(context.packageManager).installPackage(info)
 }
 @Test fun approvedActualReleasePasses() { install("normal.apk"); assertEquals("OK",IntegrityGuard.verifyInstalled(context)) }
 @Test fun alteredDexSignedByApprovedKeyFails() { install("dex-modified.apk"); assertEquals("CONTENT_HASH",IntegrityGuard.verifyInstalled(context)) }
 @Test fun unknownCurrentSignerFailsEvenWithApprovedHistory() { install("resigned.apk","untrusted",true); assertEquals("SIGNER",IntegrityGuard.verifyInstalled(context)) }
 @Test fun releaseDebuggableFlagFails() { install("normal.apk");context.applicationInfo.flags=context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE;assertEquals("DEBUGGER",IntegrityGuard.verifyInstalled(context)) }
 @Test fun unexpectedSplitFails() { install("normal.apk");context.applicationInfo.splitSourceDirs=arrayOf("unexpected.apk");assertEquals("UNSUPPORTED_SPLIT",IntegrityGuard.verifyInstalled(context)) }
}
