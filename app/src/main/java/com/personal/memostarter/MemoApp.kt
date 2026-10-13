package com.personal.memostarter
import android.app.Application
import kotlinx.coroutines.*
class MemoApp : Application() {
 val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
 val integrity by lazy { IntegrityGuard.forApp(this) }
 val database by lazy { MemoDatabase.open(this) }
 val repository by lazy { MemoRepository(database, authorize = integrity::requireTrusted) { MemoWidget.requestUpdate(this) } }
}
