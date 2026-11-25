package br.com.mediacar

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Mantém o ExoPlayer vivo fora da Activity e publica comandos padrão de mídia. */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val controller = PlayerController.shared
        if (controller != null) {
            mediaSession = MediaSession.Builder(this, controller.player).build()
        } else {
            // O serviço é iniciado pelo PlayerController, portanto normalmente já existe.
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Se ainda estiver tocando, não encerre o serviço ao remover o app dos recentes.
        // O serviço permanece em primeiro plano enquanto o Media3 mantém a sessão ativa.
        if (mediaSession?.player?.isPlaying != true) {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlayerController.shared = null
        super.onDestroy()
    }
}
