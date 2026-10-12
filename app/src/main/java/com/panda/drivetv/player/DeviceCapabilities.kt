package com.panda.drivetv.player

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

/**
 * Capacidades reales del dispositivo (etapa 6, §6.5 de la guía).
 * DriveTV no promete 4K/HDR por el archivo: la cadena completa debe soportarlo.
 */
data class DeviceCapabilities(
    val supportsAvc: Boolean,
    val supportsHevc: Boolean,
    val supportsVp9: Boolean,
    val supportsAv1: Boolean,
    val supportsHdr: Boolean,
    /** Decodificador Dolby Vision por hardware (v0.7.0). */
    val supportsDolbyVision: Boolean
) {
    companion object {
        fun fromDevice(context: Context): DeviceCapabilities {
            var avc = false
            var hevc = false
            var vp9 = false
            var av1 = false
            var dolbyVision = false
            runCatching {
                val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                for (info in codecs) {
                    if (info.isEncoder) continue
                    for (type in info.supportedTypes) {
                        when (type.lowercase()) {
                            "video/avc" -> avc = true
                            "video/hevc" -> hevc = true
                            "video/x-vnd.on2.vp9", "video/vp9" -> vp9 = true
                            "video/av01" -> av1 = true
                            // MIME que ExoPlayer usa para Dolby Vision.
                            "video/dolby-vision" -> dolbyVision = true
                        }
                    }
                }
            }
            var hdr = false
            runCatching {
                val display: Display? = context.display
                val hdrCaps = display?.hdrCapabilities
                hdr = hdrCaps != null &&
                    hdrCaps.supportedHdrTypes.isNotEmpty()
            }
            // Fallback: en TVs modernas casi siempre hay HDR si el display lo anuncia.
            if (!hdr && Build.VERSION.SDK_INT >= 24) {
                runCatching {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                    val d = wm.defaultDisplay
                    hdr = d?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
                }
            }
            // v0.7.0: el display también puede anunciar Dolby Vision aunque no
            // haya decodificador dedicado listado (algunos SoC lo integran).
            if (!dolbyVision && Build.VERSION.SDK_INT >= 24) {
                runCatching {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                    val types = wm.defaultDisplay?.hdrCapabilities?.supportedHdrTypes
                    if (types != null && types.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)) {
                        dolbyVision = true
                    }
                }
            }
            return DeviceCapabilities(avc, hevc, vp9, av1, hdr, dolbyVision)
        }
    }

    /** Resumen legible para la pantalla de ajustes. */
    fun summary(): String = buildString {
        append("AVC/H.264: ${sí(supportsAvc)} · ")
        append("HEVC/H.265: ${sí(supportsHevc)} · ")
        append("VP9: ${sí(supportsVp9)} · ")
        append("AV1: ${sí(supportsAv1)} · ")
        append("HDR: ${sí(supportsHdr)} · ")
        append("Dolby Vision: ${sí(supportsDolbyVision)}")
    }

    private fun sí(v: Boolean) = if (v) "sí" else "no"
}
