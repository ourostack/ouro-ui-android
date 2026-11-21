package bot.ouro.shop.speech

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log

/** Logs each utterance as `OuroSpoken: <text>` and speaks nothing, so a test reads exactly what TalkBack sent to speech. */
class RecordingTtsService : TextToSpeechService() {
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        TextToSpeech.LANG_AVAILABLE

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        TextToSpeech.LANG_AVAILABLE

    override fun onGetLanguage(): Array<String> = arrayOf("eng", "USA", "")

    override fun onStop() = Unit

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        Log.i("OuroSpoken", request.charSequenceText.toString())
        callback.start(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
        callback.done()
    }

    private companion object {
        const val SAMPLE_RATE = 16000
    }
}
