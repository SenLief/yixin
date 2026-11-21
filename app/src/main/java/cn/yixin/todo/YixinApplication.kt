package cn.yixin.todo

import android.app.Application
import cn.yixin.data.RoomDeckRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class YixinApplication : Application() {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val repository by lazy { RoomDeckRepository(this) { UUID.randomUUID().toString() } }
}
