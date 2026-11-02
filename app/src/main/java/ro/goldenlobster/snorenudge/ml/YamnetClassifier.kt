package ro.goldenlobster.snorenudge.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** YAMNet via LiteRT. Not thread-safe: call classify() from a single thread. */
class YamnetClassifier(context: Context) : AutoCloseable {

    class Result(
        val snoring: Float,
        val breathing: Float,
        val speech: Float,
        val topLabel: String,
        val topScore: Float,
        /** Max score over speech-like / non-snore competitor classes. */
        val competitor: Float,
    )

    private val classMap: ClassMap = context.assets.open(CLASS_MAP_ASSET).use { ClassMap.parse(it) }
    private val interpreter: Interpreter
    private val snoreIdx = classMap.indexOf("Snoring")
    private val breathIdx = classMap.indexOf("Breathing")
    private val speechIdx = classMap.indexOf("Speech")
    private val competitorIdx: IntArray = COMPETITORS.mapNotNull { name ->
        val i = classMap.indexOf(name)
        if (i < 0) Log.w(TAG, "Competitor class missing from class map: $name")
        i.takeIf { it >= 0 }
    }.toIntArray()
    private val output = Array(1) { FloatArray(NUM_CLASSES) }

    init {
        if (snoreIdx != 38) Log.w(TAG, "Snoring class index is $snoreIdx, expected 38")
        val model = loadModel(context)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    }

    fun classify(waveform: FloatArray): Result {
        require(waveform.size == INPUT_SAMPLES) { "Expected $INPUT_SAMPLES samples" }
        interpreter.run(waveform, output)
        val scores = output[0]
        var top = 0
        for (i in 1 until scores.size) if (scores[i] > scores[top]) top = i
        var comp = 0f
        for (i in competitorIdx) if (scores[i] > comp) comp = scores[i]
        return Result(
            snoring = scoreAt(scores, snoreIdx),
            breathing = scoreAt(scores, breathIdx),
            speech = scoreAt(scores, speechIdx),
            topLabel = classMap.names.getOrElse(top) { "#$top" },
            topScore = scores[top],
            competitor = comp,
        )
    }

    private fun scoreAt(s: FloatArray, i: Int) = if (i in s.indices) s[i] else 0f

    override fun close() {
        interpreter.close()
    }

    private fun loadModel(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }

    companion object {
        const val INPUT_SAMPLES = 15600
        const val NUM_CLASSES = 521
        private const val MODEL_ASSET = "yamnet.tflite"
        private const val CLASS_MAP_ASSET = "yamnet_class_map.csv"
        private const val TAG = "SnoreNudge"
        private val COMPETITORS = listOf(
            "Speech", "Child speech, kid speaking", "Babbling", "Baby cry, infant cry", "Baby laughter",
            "Laughter", "Children shouting", "Hubbub, speech noise, speech babble", "Cough",
        )
    }
}
