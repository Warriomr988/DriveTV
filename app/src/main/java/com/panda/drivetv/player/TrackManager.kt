package com.panda.drivetv.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

/** Pista de audio o subtítulo expuesta a la UI de TV. */
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

/**
 * Selección de pistas de audio y subtítulos (etapa 6, §6.9–6.10).
 * Opera sobre los [Tracks] actuales del reproductor Media3.
 */
class TrackManager(private val player: Player) {

    fun audioTracks(): List<TrackInfo> = tracksOf(C.TRACK_TYPE_AUDIO)

    fun subtitleTracks(): List<TrackInfo> = tracksOf(C.TRACK_TYPE_TEXT)

    fun selectAudio(track: TrackInfo) {
        select(track, C.TRACK_TYPE_AUDIO)
    }

    fun selectSubtitle(track: TrackInfo) {
        select(track, C.TRACK_TYPE_TEXT)
    }

    fun disableSubtitles() {
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player.trackSelectionParameters = params
    }

    fun enableSubtitles() {
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        player.trackSelectionParameters = params
    }

    private fun tracksOf(trackType: Int): List<TrackInfo> {
        val out = mutableListOf<TrackInfo>()
        val tracks: Tracks = player.currentTracks
        for ((gi, group) in tracks.groups.withIndex()) {
            if (group.type != trackType) continue
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                val label = format.label
                    ?: format.language
                    ?: format.sampleMimeType
                    ?: "Pista ${ti + 1}"
                out += TrackInfo(
                    groupIndex = gi,
                    trackIndex = ti,
                    label = label,
                    language = format.language,
                    isSelected = group.isTrackSelected(ti)
                )
            }
        }
        return out
    }

    private fun select(track: TrackInfo, trackType: Int) {
        val tracks: Tracks = player.currentTracks
        val group: TrackGroup = tracks.groups
            .getOrNull(track.groupIndex)
            ?.takeIf { it.type == trackType }
            ?.mediaTrackGroup
            ?: return
        val override = TrackSelectionOverride(group, track.trackIndex)
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .clearOverridesOfType(trackType)
            .addOverride(override)
            .build()
        player.trackSelectionParameters = params
    }
}
