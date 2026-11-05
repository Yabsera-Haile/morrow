package app.morrow.diary

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class MorrowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Auto-lock: note when the whole app leaves the screen, and lock on return if it's been too long.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.lock.onAppForegrounded()
                override fun onStop(owner: LifecycleOwner) = container.lock.onAppBackgrounded()
            },
        )
    }
}
