package com.example.linedrawspeakerkotlin

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.w3c.dom.Text
import java.util.*

class MainActivity : AppCompatActivity() {
    private var prompt: TextView? = null
    private var action: Button? = null
    private var mainMan: ConstraintLayout? = null
    private var dv: DrawView? = null
    private var dvAnimator: ObjectAnimator? = null
    private var cpAnimator: ObjectAnimator? = null
    private var freqInput: SeekBar? = null
    private var freqView: TextView? = null
    private var autoFreq: Switch? = null
    private var commandPalate: ConstraintLayout? = null
    private var coordinateView: TextView? = null

    private val TAG = "MAIN" //For Logs

    private val vibThresh = 10f
    private val vibrateTime = 100L //ms
    private val defaultSampleRate = 44100
    private var sampleRate = defaultSampleRate
    private val defaultFrequency = 262
    private var setFrequency = defaultFrequency

    private var vibrator: Vibrator? = null
    private var xWave: ArrayList<Float>? = null
    private var yWave: ArrayList<Float>? = null
    private var audioBuffer: FloatArray? = null

    @SuppressLint("ObjectAnimatorBinding")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        mainMan = findViewById(R.id.mainView)
        prompt = findViewById(R.id.instructions)
        action = findViewById(R.id.action)

        action?.setOnClickListener { letsDraw() }
        prompt?.text = getString(R.string.letUsBegin)

        //https://developer.android.com/training/animation
        dv = findViewById(R.id.myCanvas2)
        dvAnimator = ObjectAnimator.ofFloat(dv, "alpha", 0.5f).apply {
            duration = 1000
            start()
        }

        commandPalate = findViewById(R.id.commandPalate)
        cpAnimator = ObjectAnimator.ofFloat(commandPalate, "translationY",
            resources.getDimension(R.dimen.commandPalateHeight) * -1
        ).apply {
            duration = 500
        }

        //set frequencyInput selector onProgressUpdate listener
        freqInput = findViewById(R.id.frequencySelector)
        freqInput?.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar,
                                           progress: Int, fromUser: Boolean) {
                // Update FreqView UI
                freqView = findViewById(R.id.freqView)
                freqView!!.text = "${seek.progress} Hz"
                setFrequency = progress
                //ToDo: Play tone feedback respective to changing value
            }

            override fun onStartTrackingTouch(seek: SeekBar) {
                return
            }

            override fun onStopTrackingTouch(seek: SeekBar) {
                // write custom code for progress is stopped
                Toast.makeText(this@MainActivity,
                    "Verified Frequency is $setFrequency Hz",
                    Toast.LENGTH_SHORT).show()

            }
        })
        dv = null

        /**
         * ToDo: Vertical orientation mode ->
         * automatically slide screen to the left, user will only side up and down
        **/
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun letsDraw() {
        //Build Canvas and Activate
        dv = findViewById(R.id.myCanvas2)
        mainMan!!.setOnTouchListener { v, event -> this.onTouch(v, event) }
        prompt!!.text = "Redraw to Redraw"
        action!!.text = "Save"
        dvAnimator?.reverse()
        action!!.setOnClickListener { acceptLine() }
    }

    @SuppressLint("ObjectAnimatorBinding")
    private fun acceptLine() {
        //User is happy with line that is draw.
        //Delete or change any Views strictly associated with drawing.
        prompt!!.text = "Line Saved?"
        dvAnimator!!.start()
        dv = null
        action!!.text = "Draw"
        //Remove or update any onclickListeners.
        mainMan!!.setOnTouchListener(null)
        action!!.setOnClickListener { letsDraw() }
        //Save line to shared prefs
        //take user to page to clean it up and play it.
        return
    }

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        //https://developer.android.com/reference/android/view/MotionEvent
        //OnRelease
        if (event.action == MotionEvent.ACTION_UP) {
            prompt!!.text = "Redraw to Redraw, or Save I.t."
            cpAnimator?.reverse()
            drawLine()
        }
        //OnTouch
        if (event.action == MotionEvent.ACTION_DOWN) {
            coordinateView = findViewById(R.id.debugCoordinates)
            cpAnimator?.start()
            prompt!!.setText(R.string.pressed)
            xWave = ArrayList()
            yWave = ArrayList()
            xWave!!.add(event.x)
            yWave!!.add(event.y)
        }
        //OnMove
        if (event.action == MotionEvent.ACTION_MOVE) {
            //A motion event seems to have 0 - 4 coordinates in each.
            val lastX = event.x
            val lastY: Float = event.y
            coordinateView?.text = String.format("X: %f, Y: %f", lastX, lastY)

            //If prev* don't update, you'll see a line from top corner to first touch.
            var prevX = 0f
            var prevY = 0f
            var diffX: Float
            //var diffY: Float
            val waveSize = xWave!!.size
            prompt!!.text = String.format("Drawing has %d/%d points...", waveSize, defaultSampleRate/setFrequency)
            //Update prev* values after at least one entry is saved.
            if (waveSize > 0) { //Should always be true because first entry is saved in OnTouch
                prevX = xWave!![waveSize - 1]
                prevY = yWave!![waveSize - 1]
            }
            val sz = event.historySize
            //Save Everything
            var tooFast = false
            for (i in 0 until sz) {
                var x = event.getHistoricalX(i)
                var y = event.getHistoricalY(i)
                diffX = x - prevX
                //If drawing backtracks, keep largest X value for each new Y
                //Sound Waves must pass vertical line test
                if (diffX < 0) {
                    x = prevX
                    y = prevY
                } else {
                    xWave!!.add(x)
                    yWave!!.add(y)
                }
                if (diffX > vibThresh) {
                    tooFast = true
                }
                prevX = x
                prevY = y
            } //Skips the last one; manually add the last one.
            //No Backtracking
            diffX = lastX - prevX
            if (diffX >= 0) {
                xWave!!.add(lastX)
                yWave!!.add(lastY!!)
            }
            if (diffX > vibThresh) {
                tooFast = true
            }

            //Vibrate if moving too fast
            if (tooFast) {
                if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator!!.vibrate(
                        VibrationEffect.createOneShot(
                            vibrateTime.toLong(),
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    //Depreciated in API 26
                    vibrator!!.vibrate(vibrateTime.toLong())
                    //vibrator!!.vibrate(VibrationEffect.createOneShot(vibrateTime, vibrateAmp))
                }
            }
        }
        //if you return false, it will pass on motion events to any parent view.
        return true
    }

    @SuppressLint("ObjectAnimatorBinding")
    private fun drawLine() {
        dv = findViewById(R.id.myCanvas2)
        dv!!.setCoordinates(xWave!!, yWave!!)
        dv!!.invalidate()
        Log.d(TAG, "Adding myCanvas to mainActivity")
    }

    public fun letsHearIt(v: View) {
        calculateBuffer()
        val doIt = lifecycleScope.async(Dispatchers.Default) {calculateBuffer()}
        //audioBuffer = doIt.await()

    }

    public fun calculateBuffer(): FloatArray {
        /**
         * Rule of thumb, sample at least twice per cycle
         * To achieve 44.1 KHz sampling rate
         *  @20 Hz requires 2,205 samples per cycle
         *  @20 KHz requires 2.205 samples per cycle
         *  The sampling rate will change based on the desired frequency.
         **/
        val size = yWave!!.size
        var sampleRate = defaultSampleRate
        autoFreq = findViewById(R.id.autoSmplRate)
        if (autoFreq!!.isActivated) {
            sampleRate = size * setFrequency
        }
        audioBuffer = FloatArray(size)
        val viewHeight = dv?.height
//        val lineMax = yWave!!.maxOrNull() //Smaller Logical value
//        val lineMin = yWave!!.minOrNull() //Larger Logical value
//        val lineHeight = lineMax!! - lineMin!!
//        val topView2Line = (viewHeight!! - lineHeight!!) / 2f
//        val shiftDiff = topView2Line - lineMax
        val shiftDiff = viewHeight!! / 2f
        for (a in 0 until size) {
            audioBuffer!![a] = (yWave!![a] - shiftDiff) / -viewHeight!!
            Log.d(TAG, String.format("Point %d: %f -> %f", a, yWave!![a], audioBuffer!![a]))
        }
        return audioBuffer!!
    }

    public fun playSound() {
        var attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        var format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)//ToDo: Adjust amplitude range to (-1.0 to 1.0)
            .setSampleRate(sampleRate)//44.1 KHz ToDo: suggest change from analyzing line to reduce resolution increasing.
            .build()
        /** AudioFormat
         * https://developer.android.com/reference/android/media/AudioFormat
         * Encoding:
         *    8 bit unsigned int (0 to 255 with 128 offset for zero)
         *    16 bit short (-32768 to 32767)
         *    32 bit float (-1.0 to 1.0)
         */

        val buffSize = audioBuffer!!.size
        var sessionID = AudioManager.AUDIO_SESSION_ID_GENERATE//?

        /**
         * public AudioTrack (AudioAttributes attributes,
         *                      AudioFormat format,
         *                      int bufferSizeInBytes,
         *                      int mode,
         *                      int sessionId)
         */
        var player = AudioTrack(attrs, format, buffSize, AudioTrack.MODE_STATIC, sessionID)
        //https://developer.android.com/reference/android/media/AudioTrack#write(float[],%20int,%20int,%20int)
        //val float[] highResolutionBuffer
        val offsetInFloats = 0
        val sizeInFloats = buffSize.toFloat()

        /**
         * WRITE_BLOCKING: the write will block until all data has been written to the audio sink.
         * WRITE_NON_BLOCKING: the write will return immediately after queuing as much audio data for playback as possible without blocking.
         */
        val writeMode = AudioTrack.WRITE_BLOCKING
        //player.write(float[] audioData, int offsetInFloats, int sizeInFloats, int writeMode)
        player.write(audioBuffer!!, 0, buffSize, writeMode)
        return
    }
}