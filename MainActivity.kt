package br.com.mediacar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var controller: PlayerController
    private val hasStorage = mutableStateOf(false)
    private var remoteHandler: ((Int) -> Boolean)? = null
    private val keyHandler = Handler(Looper.getMainLooper())
    private var pendingMinimize: Runnable? = null
    private var lastMinimizeAt = 0L
    private var lastMinimizeKey = -1

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasStorage.value = granted || storageGranted()
        }

    private fun storageGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // tela sempre ligada
        prefs = Prefs(this)
        controller = PlayerController(applicationContext, prefs)

        hasStorage.value = storageGranted()
        if (!hasStorage.value) permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(prefs, controller, hasStorage.value, onRemoteHandler = { remoteHandler = it }) {
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }


    /**
     * Integração com o comando satélite: os códigos DPAD são usados pelo scroll/rodinha.
     * HOME, APP_SWITCH e MENU são tratados como o botão físico de minimizar; um clique
     * isolado mantém o comportamento padrão do Android e dois cliques abrem as pastas.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        val minimizeKey = key == KeyEvent.KEYCODE_HOME ||
            key == KeyEvent.KEYCODE_APP_SWITCH || key == KeyEvent.KEYCODE_MENU

        if (minimizeKey) {
            if (event.action == KeyEvent.ACTION_UP) return true
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true

            val now = SystemClock.elapsedRealtime()
            val isDouble = key == lastMinimizeKey && now - lastMinimizeAt <= 380L
            lastMinimizeKey = key
            lastMinimizeAt = now
            pendingMinimize?.let { keyHandler.removeCallbacks(it) }
            pendingMinimize = null

            if (isDouble) {
                lastMinimizeAt = 0L
                remoteHandler?.invoke(4)
            } else {
                // Aguarda a janela de duplo clique. Se não houver segundo clique,
                // repassa o evento original ao Android para preservar a função padrão.
                val originalDown = KeyEvent(event)
                val originalUp = KeyEvent.changeAction(event, KeyEvent.ACTION_UP)
                val task = Runnable { dispatchOriginalMinimize(originalDown, originalUp) }
                pendingMinimize = task
                keyHandler.postDelayed(task, 390L)
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val command = when (key) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 1
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> 2
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_VOLUME_UP -> 3
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_VOLUME_DOWN -> 5
                else -> 0
            }
            if (command != 0 && remoteHandler?.invoke(command) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun dispatchOriginalMinimize(down: KeyEvent, up: KeyEvent) {
        super.dispatchKeyEvent(down)
        super.dispatchKeyEvent(up)
    }

    override fun onStart() {
        super.onStart()
        controller.foreground = true
    }

    override fun onStop() {
        // A reprodução pertence ao PlaybackService; sair da Activity não pausa o vídeo.
        controller.save()
        controller.foreground = false
        super.onStop()
    }

    override fun onDestroy() {
        // O PlaybackService libera o ExoPlayer quando a reprodução em segundo plano termina.
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }
}
