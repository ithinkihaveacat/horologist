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

package com.google.android.horologist.mediasample.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.horologist.media.repository.PlayerRepository
import com.google.android.horologist.media3.offload.AudioOffloadManager
import com.google.android.horologist.mediasample.AppConfig
import com.google.android.horologist.mediasample.catalog.UampService
import com.google.android.horologist.mediasample.di.MediaApplicationContainer
import com.google.android.horologist.mediasample.domain.SettingsRepository
import com.google.android.horologist.mediasample.ui.debug.OffloadState
import com.google.android.horologist.networks.data.DataRequestRepository
import com.google.android.horologist.networks.data.DataUsageReport
import com.google.android.horologist.networks.data.Networks
import com.google.android.horologist.networks.status.NetworkRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class MediaPlayerAppViewModel(
    networkRepository: NetworkRepository,
    dataRequestRepository: DataRequestRepository,
    audioOffloadManager: AudioOffloadManager,
    private val settings: SettingsRepository,
    private val playerRepository: PlayerRepository,
    private val uampService: UampService,
    private val appConfig: AppConfig
) : ViewModel() {
    val networkStatus: StateFlow<Networks> = networkRepository.networkStatus

    val networkUsage: StateFlow<DataUsageReport?> = dataRequestRepository.currentPeriodUsage()
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = null
        )

    val showTimeTextInfo: Boolean = appConfig.showTimeTextInfo

    val deepLinkPrefix: String = appConfig.deeplinkUriPrefix

    val ticker = flow {
        while (true) {
            delay(1.seconds)
            emit(Unit)
        }
    }

    val offloadState: StateFlow<OffloadState> = combine(
        audioOffloadManager.sleepingForOffload,
        audioOffloadManager.offloadSchedulingEnabled,
        audioOffloadManager.format,
        audioOffloadManager.times,
        ticker
    ) { sleepingForOffload, offloadSchedulingEnabled, format, times, _ ->
        OffloadState(
            sleepingForOffload = sleepingForOffload,
            offloadSchedulingEnabled = offloadSchedulingEnabled,
            format = format,
            times = times.timesToNow(sleepingForOffload)
        )
    }.stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OffloadState(
            sleepingForOffload = audioOffloadManager.sleepingForOffload.value,
            offloadSchedulingEnabled = audioOffloadManager.offloadSchedulingEnabled.value,
            format = audioOffloadManager.format.value,
            times = audioOffloadManager.times.value.timesToNow(audioOffloadManager.sleepingForOffload.value)
        )
    )

    suspend fun loadItems() {
        if (playerRepository.currentMediaItem.value == null) {
            try {
                val mediaItems = uampService.catalog().music.map {
                    it.toMediaItem()
                }

                playerRepository.setMediaItems(mediaItems)
                playerRepository.prepare()
            } catch (ioe: IOException) {
                // Nothing
            }
        }
    }

    suspend fun startupSetup(navigateToLibrary: () -> Unit) {
        val loadAtStartup =
            settings.settingsFlow.first().loadItemsAtStartup

        // setMediaItems is a noop before this point
        playerRepository.connected.filter { it }.first()

        if (loadAtStartup) {
            loadItems()
        } else {
            navigateToLibrary()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MediaPlayerAppViewModel(
                    this[MediaApplicationContainer.NetworkRepositoryKey]!!,
                    this[MediaApplicationContainer.DataRequestRepositoryKey]!!,
                    this[MediaApplicationContainer.AudioOffloadManagerKey]!!,
                    this[MediaApplicationContainer.SettingsRepositoryKey]!!,
                    this[MediaApplicationContainer.PlayerRepositoryImplKey]!!,
                    this[MediaApplicationContainer.UampServiceKey]!!,
                    this[MediaApplicationContainer.AppConfigKey]!!,
                )
            }
        }
    }
}