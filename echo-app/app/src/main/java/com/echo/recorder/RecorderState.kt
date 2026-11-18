package com.echo.recorder

import kotlinx.coroutines.flow.MutableStateFlow

/** Single source of truth shared by the service (writer) and the UI (reader). */
object RecorderState {
    enum class Status { Idle, Recording, Paused }

    data class UiState(
        val status: Status = Status.Idle,
        val elapsedMs: Long = 0,
        val levels: List<Float> = emptyList(),
        val bookmarks: List<Long> = emptyList(),
        val lastRecordingId: String? = null,
    )

    val state = MutableStateFlow(UiState())
}
