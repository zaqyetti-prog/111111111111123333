package br.com.mediacar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Lista de pastas e vídeos com itens GRANDES.
 * [sizeLevel] 0 = médio, 1 = grande, 2 = enorme.
 */
@Composable
fun BrowserScreen(
    dir: File?,
    roots: List<File>,
    rescanKey: Int,
    remoteMove: Int,
    remoteSelect: Int,
    lastPath: String?,
    sizeLevel: Int,
    canContinue: Boolean,
    onSizeLevel: (Int) -> Unit,
    onContinue: () -> Unit,
    backgroundPlaybackActive: Boolean,
    onBackgroundPlayback: () -> Unit,
    onRescan: () -> Unit,
    onUp: () -> Unit,
    onFolder: (File) -> Unit,
    onPlay: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = 1f + 0.3f * sizeLevel
    val listingState = produceState<Pair<List<File>, List<File>>?>(initialValue = null, dir, rescanKey) {
        value = if (dir == null) null else withContext(Dispatchers.IO) { FileUtil.list(dir) }
    }
    val listing = listingState.value
    val listState = rememberLazyListState()
    var selectedIndex by remember(dir, rescanKey) { mutableIntStateOf(0) }
    var lastRemoteMove by remember { mutableIntStateOf(0) }
    var lastRemoteSelect by remember { mutableIntStateOf(0) }
    val entries = if (dir == null) roots.map { it to false }
        else listing?.let { (folders, videos) -> (folders.map { it to false } + videos.map { it to true }) }.orEmpty()

    LaunchedEffect(remoteMove, entries.size) {
        val delta = (remoteMove - lastRemoteMove).coerceIn(-100, 100)
        lastRemoteMove = remoteMove
        if (entries.isNotEmpty() && delta != 0) {
            selectedIndex = (selectedIndex + delta).mod(entries.size)
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(remoteSelect) {
        if (remoteSelect != lastRemoteSelect) {
            lastRemoteSelect = remoteSelect
            if (entries.isNotEmpty()) {
                val (file, isVideo) = entries[selectedIndex.coerceIn(0, entries.lastIndex)]
                if (isVideo) onPlay(file) else onFolder(file)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // Cabeçalho
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackgroundPlaybackButton(active = backgroundPlaybackActive, onClick = onBackgroundPlayback)
            Spacer(Modifier.width(10.dp))
            if (dir != null) {
                CircleButton(Glyph.BACK, onUp, 72.dp)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Pastas", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    dir?.absolutePath ?: "Escolha onde estão seus vídeos",
                    fontSize = 20.sp,
                    color = Pal.Sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            GlassButton(onClick = { onSizeLevel((sizeLevel + 1) % 3) }) {
                Text(
                    "Tamanho: " + listOf("Médio", "Grande", "Enorme")[sizeLevel.coerceIn(0, 2)],
                    fontSize = 22.sp,
                    color = Color.White
                )
            }
            if (canContinue) {
                Spacer(Modifier.width(12.dp))
                GlassButton(onClick = onContinue, strong = true) {
                    GlyphIcon(Glyph.PLAY, 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Continuar", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Lista
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (dir == null) {
                items(roots, key = { it.absolutePath }) { f ->
                    ItemRow(Glyph.DRIVE, Pal.Accent, FileUtil.rootLabel(f), f.absolutePath, roots.indexOf(f) == selectedIndex, false, scale) {
                        onFolder(f)
                    }
                }
                item {
                    ItemRow(Glyph.CHEVRON, Pal.Purple, "Atualizar lista", "Use depois de espetar o pendrive", false, false, scale) {
                        onRescan()
                    }
                }
            } else if (listing != null) {
                val (folders, videos) = listing
                if (folders.isEmpty() && videos.isEmpty()) {
                    item {
                        Text(
                            "Nenhuma pasta ou vídeo aqui.",
                            fontSize = 28.sp,
                            color = Pal.Sub,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                items(folders, key = { it.absolutePath }) { f ->
                    ItemRow(Glyph.FOLDER, Pal.Orange, f.name, null, folders.indexOf(f) == selectedIndex, true, scale) { onFolder(f) }
                }
                items(videos, key = { it.absolutePath }) { f ->
                    val isLast = f.absolutePath == lastPath
                    val sub = FileUtil.fmtSize(f.length()) + if (isLast) "   •   último assistido" else ""
                    ItemRow(Glyph.VIDEO, Pal.Pink, f.nameWithoutExtension, sub, isLast || (folders.size + videos.indexOf(f) == selectedIndex), false, scale) { onPlay(f) }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    glyph: Glyph,
    tint: Color,
    title: String,
    sub: String?,
    highlight: Boolean,
    chevron: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height((92f * scale).dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = if (highlight) 0.025f else 0.055f))
            .border(
                width = if (highlight) 2.dp else 1.dp,
                color = if (highlight) Pal.Purple else Color.White.copy(alpha = 0.13f),
                shape = RoundedCornerShape(26.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size((60f * scale).dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tint.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, (38f * scale).dp)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = (28f * scale).sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = if (sub == null) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub != null) {
                Text(
                    sub,
                    fontSize = (20f * scale).sp,
                    color = Pal.Sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (chevron) {
            GlyphIcon(Glyph.CHEVRON, (40f * scale).dp, Pal.Sub)
        }
    }
}
