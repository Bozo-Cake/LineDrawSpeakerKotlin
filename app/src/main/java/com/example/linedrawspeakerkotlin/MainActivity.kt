package com.example.linedrawspeakerkotlin

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import java.util.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private var prompt: TextView? = null
    private var action: Button? = null
    private var mainMan: ConstraintLayout? = null
    private var dv: DrawView? = null
    private var dvAnimator: ObjectAnimator? = null

    private val TAG = "MAIN" //For Logs

    private val vibThresh = 10f
    private val vibrateTime = 100L //ms
    //private val vibrateAmp = 200 //1 - 255 for amplitude

    private val animateSize = 800f

    private var vibrator: Vibrator? = null
    private var xWave: ArrayList<Float>? = null
    private var yWave: ArrayList<Float>? = null
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

        //Move Drawing Canvas off of screen
        dv = findViewById(R.id.myCanvas2)

        dvAnimator = ObjectAnimator.ofFloat(dv, "translationY", animateSize).apply {
            duration = 1000
            start()
        }
        //dv!!.visibility = View.GONE
        dv = null

    }

    @SuppressLint("ClickableViewAccessibility")
    private fun letsDraw() {
        //Build Canvas and Activate
        dv = findViewById(R.id.myCanvas2)
        //dv!!.visibility = View.VISIBLE
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
        //dv!!.visibility = View.GONE
        dv = null
        action!!.text = "Draw"
        //Remove or update any onclickListeners.
        mainMan!!.setOnTouchListener(null)
        action!!.setOnClickListener { letsDraw() }
        //Save line to shared prefs
        //take user to page to clean it up and play it.
        return
        //Haha, this forces it to crash.
        TODO("Not yet implemented")
    }

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        //https://developer.android.com/reference/android/view/MotionEvent
        //OnRelease
        if (event.action == MotionEvent.ACTION_UP) {
            prompt!!.text = "Redraw to Redraw, or Save I.t."
            drawLine()
            //increaseResolution()
        }
        //OnTouch
        if (event.action == MotionEvent.ACTION_DOWN) {
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
            //If prev* don't update, you'll see a line from top corner to first touch.
            var prevX = 0f
            var prevY = 0f
            var diffX: Float
            //var diffY: Float
            val waveSize = xWave!!.size
            prompt!!.text = String.format("Drawing has %d points...", waveSize)
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

    private fun increaseResolution() {
//        lifecycleScope.launch(Dispatchers.Default) {
//            //Do the Mathic!!
//        }
        //or
        val doIt = async(Dispatchers.Default) {calculateBuffer(xWave, yWave)}
        val highResolutionBuffer = doIt.await()
        return
    }

    @SuppressLint("ObjectAnimatorBinding")
    private fun drawLine() {
        dv = findViewById(R.id.myCanvas2)
        dv!!.setCoordinates(xWave!!, yWave!!)
        dv!!.invalidate()

        //dv!!.setBackgroundColor(Color.WHITE)
        //dv!!.id = R.id.myCanvas
        //dv!!.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        Log.d(TAG, "Adding myCanvas to mainActivity")
        //mainMan!!.addView(dv)
    }

    /**
     * MediaPlayer: This class is the primary API for playing sound and video.
     * AudioManager: This class manages audio sources and audio output on a device.
     **/
//    private fun playSound() {
//        var attrs = AudioAttributes()
//        var format = AudioFormat()
//        var player = AudioTrack(AudioTrack.MODE_STATIC)
//        return
//    }

}