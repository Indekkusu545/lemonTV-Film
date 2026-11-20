package com.lemon.yingshi.tv.ui.player

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.lemon.yingshi.tv.R
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.LomenTVTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private var globalKeyHandler: ((KeyEvent) -> Boolean)? = null

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_EPISODE_TITLE = "extra_episode_title"
        const val EXTRA_MEDIA_ID = "extra_media_id"
        const val EXTRA_EPISODE_ID = "extra_episode_id"
        const val EXTRA_START_POSITION = "extra_start_position"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installCrashProbe()

        window.setBackgroundDrawableResource(R.color.background_dark)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.playerInstance.collectLatest { player ->
                    if (player != null) observePlayback(player)
                }
            }
        }

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE)
        val episodeTitle = intent.getStringExtra(EXTRA_EPISODE_TITLE)
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)
        val episodeId = intent.getStringExtra(EXTRA_EPISODE_ID)
        val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        Log.i(
            "PlayerCrashProbe",
            "onCreate playback args: mediaId=$mediaId, episodeId=$episodeId, " +
                "startPosition=$startPosition, videoUrl=${videoUrl.take(240)}"
        )

        setContent {
            LomenTVTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    PlayerScreen(
                        videoUrl = videoUrl,
                        title = title,
                        episodeTitle = episodeTitle,
                        mediaId = mediaId,
                        episodeId = episodeId,
                        startPosition = startPosition,
                        onBackPressed = { finish() },
                        viewModel = viewModel,
                        onRegisterGlobalKeyHandler = { handler ->
                            globalKeyHandler = handler
                        }
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = globalKeyHandler
        if (handler != null && handler(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private suspend fun observePlayback(player: Player) {
        val session = MediaSession.Builder(this, player).build()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val playbackChanged = events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED
                )
                if (playbackChanged) {
                    updateScreenOn(player)
                }
            }
        }
        player.addListener(listener)
        try {
            updateScreenOn(player)
            awaitCancellation()
        } finally {
            player.removeListener(listener)
            session.release()
            window.clearFlags(FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateScreenOn(player: Player) {
        if (player.shouldKeepScreenOn()) {
            window.addFlags(FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun installCrashProbe() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val safeIntent = intent
            val mediaId = safeIntent?.getStringExtra(EXTRA_MEDIA_ID)
            val episodeId = safeIntent?.getStringExtra(EXTRA_EPISODE_ID)
            val startPosition = safeIntent?.getLongExtra(EXTRA_START_POSITION, 0L)
            val videoUrl = safeIntent?.getStringExtra(EXTRA_VIDEO_URL)?.take(240)
            Log.e(
                "PlayerCrashProbe",
                "Uncaught exception in PlayerActivity thread=${thread.name}, " +
                    "mediaId=$mediaId, episodeId=$episodeId, startPosition=$startPosition, " +
                    "videoUrl=$videoUrl",
                throwable
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}

internal fun Player.shouldKeepScreenOn(): Boolean =
    playWhenReady && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
        (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
