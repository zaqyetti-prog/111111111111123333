package br.com.mediacar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { BROWSER, PLAYER }

/** Controla qual tela aparece e a retomada automática do último vídeo. */
@Composable
fun App(prefs: Prefs, c: PlayerController, hasStorage: Boolean, onRemoteHandler: (((Int) -> Boolean)?) -> Unit, onRequestPermission: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.BROWSER) }
    var dir by remember {
        mutableStateOf(prefs.lastPath?.let { File(it).parentFile }?.takeIf { it.isDirectory })
    }
    var rescan by remember { mutableIntStateOf(0) }
    var sizeLevel by remember { mutableIntStateOf(prefs.sizeLevel) }
    var fill by remember { mutableStateOf(prefs.fillMode) }
    var waiting by remember { mutableStateOf<File?>(null) }
    var cancelled by remember { mutableStateOf(false) }
    var remoteMove by remember { mutableIntStateOf(0) }
    var remoteSelect by remember { mutableIntStateOf(0) }
    var browserOverlay by remember { mutableStateOf(false) }

    val rootsState = androidx.compose.runtime.produceState<List<File>>(initialValue = emptyList(), rescan, hasStorage) {
        value = if (hasStorage) withContext(Dispatchers.IO) { FileUtil.roots() } else emptyList()
    }
    val roots = rootsState.value

    SideEffect {
        onRemoteHandler { command ->
            val browsing = screen == Screen.BROWSER || (screen == Screen.PLAYER && browserOverlay)
            when (command) {
                1 -> if (browsing) { remoteMove--; true } else false
                2 -> if (browsing) { remoteMove++; true } else false
                3 -> if (browsing) { remoteSelect++; true } else false
                4 -> {
                    if (screen == Screen.PLAYER) browserOverlay = true
                    true
                }
                5 -> if (browsing) {
                    val current = dir
                    val isRoot = current == null || roots.any { it.absolutePath == current.absolutePath }
                    if (isRoot && screen == Screen.PLAYER) browserOverlay = false else up()
                    true
                } else false
                else -> false
            }
        }
    }

    // Atualiza posição na tela e grava o progresso
    LaunchedEffect(Unit) {
        c.enableBackgroundPlayback()
        while (true) {
            c.tick()
            delay(500)
        }
    }

    // Ao abrir o app: se estava tocando, volta a tocar sozinho de onde parou.
    // Se o pendrive ainda não montou (carro acabou de ligar), espera até ~2 minutos.
    LaunchedEffect(hasStorage) {
        if (!hasStorage || !prefs.autoResume || !prefs.wasPlaying) return@LaunchedEffect
        val path = prefs.lastPath ?: return@LaunchedEffect
        val f = File(path)
        waiting = f
        screen = Screen.PLAYER
        var tries = 0
        while (!f.exists() && tries < 80 && !cancelled) {
            delay(1500)
            tries++
        }
        if (!cancelled && f.exists()) {
            c.playFile(f, prefs.lastPos, true)
        } else {
            screen = Screen.BROWSER
        }
        waiting = null
    }

    fun up() {
        val d = dir ?: return
        val isRoot = roots.any { it.absolutePath == d.absolutePath }
        dir = if (isRoot) null else d.parentFile
    }

    BackHandler(enabled = screen != Screen.BROWSER || dir != null) {
        when (screen) {
            Screen.PLAYER -> {
                if (browserOverlay) {
                    val current = dir
                    val isRoot = current == null || roots.any { it.absolutePath == current.absolutePath }
                    if (isRoot) browserOverlay = false else up()
                    return@BackHandler
                }
                if (waiting != null) {
                    cancelled = true
                    waiting = null
                }
                c.save()
                screen = Screen.BROWSER
            }
            Screen.BROWSER -> up()
        }
    }

    AppBackground {
        if (!hasStorage) {
            MessageCard(
                title = "Permita o acesso aos vídeos",
                text = "O Media Car precisa ler os arquivos do aparelho e do pendrive para mostrar seus vídeos.",
                button = "Permitir",
                onButton = onRequestPermission,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            when (screen) {
                Screen.BROWSER -> BrowserScreen(
                    dir = dir,
                    roots = roots,
                    rescanKey = rescan,
                    remoteMove = remoteMove,
                    remoteSelect = remoteSelect,
                    lastPath = prefs.lastPath,
                    sizeLevel = sizeLevel,
                    canContinue = c.loaded || prefs.lastPath != null,
                    onSizeLevel = {
                        sizeLevel = it
                        prefs.sizeLevel = it
                    },
                    onContinue = {
                        if (c.loaded) {
                            c.player.play()
                        } else {
                            prefs.lastPath?.let { p ->
                                val f = File(p)
                                if (f.exists()) c.playFile(f, prefs.lastPos, true)
                            }
                        }
                        screen = Screen.PLAYER
                    },
                    backgroundPlaybackActive = c.backgroundMode,
                    onBackgroundPlayback = { c.enableBackgroundPlayback() },
                    onRescan = { rescan++ },
                    onUp = { up() },
                    onFolder = { dir = it },
                    onPlay = {
                        c.playFile(it, 0L, true)
                        screen = Screen.PLAYER
                    }
                )
                Screen.PLAYER -> {
                    Box(Modifier.fillMaxSize()) {
                        PlayerScreen(
                            c = c,
                            fill = fill,
                            waitingName = waiting?.nameWithoutExtension,
                            onCancelWait = {
                                cancelled = true
                                waiting = null
                                screen = Screen.BROWSER
                            },
                            onBack = {
                                c.save()
                                screen = Screen.BROWSER
                            },
                            onBackgroundPlayback = { c.enableBackgroundPlayback() },
                            onToggleFill = {
                                fill = !fill
                                prefs.fillMode = fill
                            }
                        )
                        if (browserOverlay) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.58f))
                                    .padding(24.dp)
                                    .background(Color(0xE6101118), RoundedCornerShape(24.dp))
                                    .border(1.5.dp, Pal.Purple.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                            ) {
                                BrowserScreen(
                                    dir = dir,
                                    roots = roots,
                                    rescanKey = rescan,
                                    remoteMove = remoteMove,
                                    remoteSelect = remoteSelect,
                                    lastPath = prefs.lastPath,
                                    sizeLevel = sizeLevel,
                                    canContinue = c.loaded || prefs.lastPath != null,
                                    onSizeLevel = {
                                        sizeLevel = it
                                        prefs.sizeLevel = it
                                    },
                                    onContinue = {
                                        browserOverlay = false
                                    },
                                    backgroundPlaybackActive = c.backgroundMode,
                                    onBackgroundPlayback = { c.enableBackgroundPlayback() },
                                    onRescan = { rescan++ },
                                    onUp = {
                                        val current = dir
                                        val isRoot = current == null || roots.any { it.absolutePath == current.absolutePath }
                                        if (isRoot) browserOverlay = false else up()
                                    },
                                    onFolder = { dir = it },
                                    onPlay = {
                                        c.playFile(it, 0L, true)
                                        browserOverlay = false
                                    },
                                    modifier = Modifier.fillMaxSize().padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
