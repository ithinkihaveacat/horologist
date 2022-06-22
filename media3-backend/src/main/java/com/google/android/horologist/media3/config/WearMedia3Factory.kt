/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.horologist.media3.config

import android.content.Context
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

public open class WearMedia3Factory(private val context: Context) {
    public fun audioSink(
        attemptOffload: Boolean,
    ): AudioSink {
        val offloadMode =
            if (attemptOffload)
                DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED
            else
                DefaultAudioSink.OFFLOAD_MODE_DISABLED

        return DefaultAudioSink.Builder()
            .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
            .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain())
            .setOffloadMode(offloadMode)
            .build()
    }

    public fun audioOnlyRenderersFactory(
        audioSink: AudioSink,
        mediaCodecSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
    ): RenderersFactory =
        RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf(
                MediaCodecAudioRenderer(
                    context, mediaCodecSelector, handler, audioListener, audioSink
                )
            )
        }

    public fun mediaCodecSelector(): MediaCodecSelector = MediaCodecSelector.DEFAULT
}