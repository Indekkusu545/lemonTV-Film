package com.lemon.yingshi.tv.ui.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PlaybackScreenOnTest {
    private val player = mock(Player::class.java)

    @Test
    fun keepScreenOnAcrossPlaybackAndRebufferingUntilPaused() {
        `when`(player.playWhenReady).thenReturn(true)
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        assertTrue(player.shouldKeepScreenOn())

        `when`(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        assertTrue(player.shouldKeepScreenOn())

        `when`(player.playWhenReady).thenReturn(false)
        assertFalse(player.shouldKeepScreenOn())
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        assertFalse(player.shouldKeepScreenOn())
    }

    @Test
    fun allowScreensaverWhenPlaybackEndsStopsOrLosesAudioFocus() {
        `when`(player.playWhenReady).thenReturn(true)
        `when`(player.playbackState).thenReturn(Player.STATE_ENDED)
        assertFalse(player.shouldKeepScreenOn())
        `when`(player.playbackState).thenReturn(Player.STATE_IDLE)
        assertFalse(player.shouldKeepScreenOn())

        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        `when`(player.playbackSuppressionReason)
            .thenReturn(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        assertFalse(player.shouldKeepScreenOn())
    }
}
