package com.example.linedrawspeakerkotlin

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.*

class MainActivity : AppCompatActivity() {
    private var prompt: TextView? = null
    private var action: Button? = null
    private var mainMan: ConstraintLayout? = null

    private val TAG = "MAIN" //For Logs

    private val vibThresh = 10f
    private val vibrateTime = 100 //ms

    private var vibrator: Vibrator? = null
    var xWave: ArrayList<Float>? = null
    var yWave: ArrayList<Float>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        mainMan = findViewById(R.id.mainView)
        prompt = findViewById(R.id.instructions)
        action = findViewById(R.id.action)

        action?.setOnClickListener { letsDraw() }
        prompt?.text = "Use the button :)"

        //Build Canvas and Activate
//        T.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
//            this.onTouch(
//                v,
//                event
//            )
//        })
    }

    private fun letsDraw() {
        prompt?.text = String.format("%s%d", getString(R.string.touchToBegin), xWave?.size)
        val dv = DrawView(this, xWave!!, yWave!!)
        dv.setBackgroundColor(Color.WHITE)
        dv.id = R.id.myCanvas
    }

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        //https://developer.android.com/reference/android/view/MotionEvent
        //OnRelease
        if (event.action == MotionEvent.ACTION_UP) {
            drawLine()
            increaseResolution()
        }
        //OnTouch
        if (event.action == MotionEvent.ACTION_DOWN) {
            T?.setText(R.string.pressed)
            //wave = new HashMap<Float, Float>();
            //wave.put(0f, 0f);
            xWave = ArrayList()
            yWave = ArrayList()
            xWave!!.add(event.x)
            yWave!!.add(event.y)
        }
        //OnMove
        if (event.action == MotionEvent.ACTION_MOVE) {
            //A motion event seems to have 0 - 4 coordinates in each.
            var lastX = event.x
            var lastY: Float? = event.y
            //If prev* don't update, you'll see a line from top corner to first touch.
            var prevX = 0f
            var prevY = 0f
            var diffX: Float
            var diffY: Float
            val waveSize = xWave!!.size
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
            if (diffX < 0) {
                lastX = prevX
                lastY = prevY
            } else {
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
                }
                tooFast = false
            }
            return true
        }
        return false
    }

    fun increaseResolution() {
        return
    }

    private fun drawLine() {
        T!!.text = String.format("%s%d", getString(R.string.touchToBegin), xWave!!.size)
        val dv = DrawView(this, xWave!!, yWave!!)
        dv.setBackgroundColor(Color.WHITE)
        dv.id = myCanvas
        val mainView = findViewById<RelativeLayout>(R.id.mainView)
        mainView.addView(dv)
        var params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_BOTTOM, RelativeLayout.TRUE)
        val btn = Button(this)
        btn.layoutParams = params
        btn.id = R.id.reset
        btn.setOnClickListener {
            val main = findViewById<ViewGroup>(R.id.mainView)
            main.removeView(findViewById(R.id.myCanvas))
            main.removeView(findViewById(R.id.reset))
            main.removeView(findViewById(R.id.player))
        }
        btn.text = "           Go Back"
        mainView.addView(btn, params)
        params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_RIGHT, RelativeLayout.TRUE)
        val play = Button(this)
        play.id = R.id.player
        play.layoutParams = params
        play.setOnClickListener { playSound() }
        play.text = "Play"
        mainView.addView(play, params)
    }

    fun playSound() {
        return
    }
}