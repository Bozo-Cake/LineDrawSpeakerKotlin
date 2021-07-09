package com.example.linedrawspeakerkotlin

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.sin
//https://commons.apache.org/proper/commons-math/javadocs/api-3.5/org/apache/commons/math3/fitting/HarmonicCurveFitter.html

class MainActivity : AppCompatActivity() {
    private var prompt: TextView? = null
    private var action: Button? = null
    private var mainMan: ConstraintLayout? = null
    private var dv: DrawView? = null
    private var dvAnimator: ObjectAnimator? = null
    private var cpAnimator: ObjectAnimator? = null
    private var freqInput: SeekBar? = null
    private var freqView: TextView? = null
    private var commandPalate: ConstraintLayout? = null
    private var coordinateView: TextView? = null

    private val TAG = "MAIN" //For Logs

    private val vibThresh = 10f
    private val vibrateTime = 100L //ms
    private val defaultSampleRate = 44100
    private var sampleRate = defaultSampleRate
    private val defaultFrequency = 262
    private var setFrequency = defaultFrequency
    private val playTime = 2000 //ms

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

        //Draw pure sine wave by default
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        var width = displayMetrics.widthPixels
        var height = displayMetrics.heightPixels
        calculateSingleSineWave(width, height)
        drawLine()

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
        playSound()
//        sampleRate = defaultSampleRate
//        calculatePureSignBuffer(setFrequency, 2, sampleRate).play()
        //the following need to be put in their own suspend function and called from there.
        //val doIt = lifecycleScope.async(Dispatchers.Default) {calculateBuffer()}
        //audioBuffer = doIt.await()

    }

    private fun calculateBuffer() {
        //audioBuffer.Size = samplesRate * playTime
        var tempAudioBuffer = ArrayList<Float>()

        //map drawing coordinates to +-1.0f for AudioTrack.write()
        val viewHeight = dv?.height
        val shiftDiff = viewHeight!! / 2f
        val waveSize = xWave!!.size
        val stepSize = waveSize/(sampleRate/setFrequency) + 1
        for (a in 0 until waveSize step stepSize) {
            tempAudioBuffer.add((yWave!![a] - shiftDiff) / (-viewHeight / 2)) //Negative to invert +- values
        }

        val samplesPerWave = tempAudioBuffer.size
        Log.d(TAG, String.format("Magical Number is ${samplesPerWave * setFrequency}"))
        val playBufferSize = samplesPerWave * setFrequency * playTime / 1000
        Log.d(TAG, String.format("Stats:\nWaveSamples: $samplesPerWave\nFreq: $setFrequency\nPlayTime: $playTime ms\nAudioBufferSize: $playBufferSize"))
        audioBuffer = FloatArray(playBufferSize)
        var i = 0
        for (f in 0 until setFrequency) {
            for (p in 0 until samplesPerWave) {//Points per cycle
                audioBuffer!![i++] = tempAudioBuffer[p]
                //Log.d(TAG, tempAudioBuffer[p].toString())
            }
        }
        //return audioBuffer!!
    }
    private fun calculateSingleSineWave(width: Int, height: Int) {
        xWave = ArrayList()
        yWave = ArrayList()
        var i = 0
        val samples = width * 0.9f
        while (i < samples) {
            xWave!!.add(i.toFloat() + 100)
            yWave!!.add((sin(Math.PI * i * 2 / samples).toFloat() * height / -2.3f) + height/2 - 25)
            i++
        }
    }

    fun playSound() {
        Log.d(TAG, "Attempting to play Sound with your ghetto line :)")
        val buffSize = audioBuffer!!.size
//        val player = AudioTrack.Builder()
//            .setAudioAttributes(AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_MEDIA)
//                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                .build())
//            .setAudioFormat(AudioFormat.Builder()
//                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
//                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
//                .setSampleRate(sampleRate)
//                .build())
//            .setBufferSizeInBytes(buffSize)
//            .build()
        val player = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
            buffSize * (java.lang.Float.SIZE / 8),
            AudioTrack.WRITE_BLOCKING
        )
        for (i in audioBuffer!!) {
            Log.d("PLAYBUFFER", i.toString())
        }
        Log.d(TAG, String.format("More Stats\nArrayCount: ${audioBuffer!!.size}\nSampleRate: $sampleRate"))//buffSize is currently 4 * audioBuffer!!.size
        player.write(audioBuffer!!, 0, buffSize, AudioTrack.WRITE_BLOCKING)
        return
    }
}