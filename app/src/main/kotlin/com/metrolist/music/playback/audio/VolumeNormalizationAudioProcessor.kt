package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow


@UnstableApi
@SuppressWarnings("Deprecated")
class VolumeNormalizationAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    
    var enabled = false
        set(value) {
            if (field != value) {
                field = value
                Timber.tag(TAG).d("Normalization processor enabled: $value")
            }
        }

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var targetGainMb: Int = 0
    private var linearGain: Double = 1.0

    companion object {
        private const val TAG = "VolumeNormalizationProcessor"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    @Synchronized
    fun setTargetGain(gainMb: Int) {
        if (targetGainMb != gainMb) {
            targetGainMb = gainMb
            linearGain = 10.0.pow(gainMb / 2000.0)
            Timber.tag(TAG).d("Target gain set to $gainMb mB (Linear multiplier: $linearGain)")
        }
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        Timber.tag(TAG).d("Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        if (encoding != C.ENCODING_PCM_16BIT) {
            val exception = AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            throw exception
        }

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!enabled || targetGainMb == 0) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            if (outputBuffer.capacity() < remaining) {
                outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        if (outputBuffer === EMPTY_BUFFER || outputBuffer === inputBuffer) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else if (outputBuffer.capacity() < inputSize) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val sampleCount = inputSize / 2

        repeat(sampleCount) {
            val sample = inputBuffer.getShort()
            val processed = (sample * linearGain).coerceIn(-32768.0, 32767.0).toInt().toShort()
            outputBuffer.putShort(processed)
        }

        outputBuffer.flip()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer.remaining() == 0
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        targetGainMb = 0
        linearGain = 1.0
        enabled = false
    }
}
