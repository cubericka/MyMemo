package com.personal.memostarter
import android.app.Application
import kotlinx.coroutines.*
class MemoApp : Application() {
 val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
 val integrity by lazy { IntegrityGuard.forApp(this) }
 val database by lazy { MemoDatabase.open(this) }
 val appLock by lazy { AppPinLock(getSharedPreferences("app-pin-v1", MODE_PRIVATE)) }
 val repository by lazy { MemoRepository(database, authorize = { integrity.requireTrusted();appLock.requireOpen() }, readAllowed=appLock::requireOpen, recoverKey=appLock::recover, rememberKey=appLock::remember) { MemoWidget.requestUpdate(this) } }
}
