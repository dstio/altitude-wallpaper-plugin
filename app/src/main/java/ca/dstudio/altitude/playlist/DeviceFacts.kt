package ca.dstudio.altitude.playlist

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

/** What this device can play and show: Auto is 4K only with both of the first two; 1080p is HEVC where it decodes it. */
data class DeviceFacts(val decodes4kHevc: Boolean, val panel4k: Boolean, val decodes1080Hevc: Boolean) {

    /** What Auto plays: 4K when this device decodes it and its panel shows it. */
    val plays4k: Boolean get() = decodes4kHevc && panel4k

    companion object {
        private const val HEVC = "video/hevc"

        /** A decoder that lists HDR10 decodes Main 10 too. The HDR10 constant is inlined: below Android 7 no decoder lists it. */
        @SuppressLint("InlinedApi")
        private val MAIN_10 = setOf(CodecProfileLevel.HEVCProfileMain10, CodecProfileLevel.HEVCProfileMain10HDR10)

        /** Read once a process (the codec list is slow to read), then kept: the facts do not change. */
        @Volatile
        private var known: DeviceFacts? = null

        fun read(context: Context): DeviceFacts = known ?: DeviceFacts(
            decodes4kHevc = runCatching { decodesHevc(3840, 2160) }.getOrDefault(false),
            panel4k = runCatching { panel4k(context) }.getOrDefault(false),
            decodes1080Hevc = runCatching { decodesHevc(1920, 1080) }.getOrDefault(false),
        ).also { known = it }

        /**
         * A non-secure HEVC decoder for the Main 10 profile (Apple's SDR clips are 10-bit) that takes
         * [width] x [height] at 30 fps. From Android 10 it must be a hardware one: a software decoder
         * claims sizes it cannot play in time.
         */
        private fun decodesHevc(width: Int, height: Int): Boolean = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            val type = info.supportedTypes.firstOrNull { it.equals(HEVC, ignoreCase = true) }
            type != null && !info.isEncoder &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || info.isHardwareAccelerated) &&
                info.getCapabilitiesForType(type).let { caps ->
                    !caps.isFeatureRequired(CodecCapabilities.FEATURE_SecurePlayback) &&
                        caps.profileLevels.any { it.profile in MAIN_10 } &&
                        caps.videoCapabilities?.areSizeAndRateSupported(width, height, 30.0) == true
                }
        }

        /** The panel's largest mode is 4K: many 4K TVs draw their interface, and report their window, at 1080p. */
        private fun panel4k(context: Context): Boolean {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                ?: return false
            return display.supportedModes.any { maxOf(it.physicalWidth, it.physicalHeight) >= 3840 }
        }
    }
}
