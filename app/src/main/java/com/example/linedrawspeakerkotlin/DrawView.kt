package com.example.linedrawspeakerkotlin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.widget.LinearLayout
import java.util.*

class DrawView : LinearLayout {
    private val TAG = "DrawView" //For Logs
    private var paint: Paint? = null
    private var w = 0
    private var h = 0
    private var xWave: ArrayList<Float>? = null
    private var yWave: ArrayList<Float>? = null
    //constructor(context: Context?, private var xWave: ArrayList<Float>, private var yWave: ArrayList<Float>)
    constructor(context: Context) : super(context)
    constructor(c: Context, attrs: AttributeSet) : super(c, attrs)
    constructor(c: Context, attrs: AttributeSet, defStyleAttr: Int) : super(c, attrs, defStyleAttr)
    constructor(c: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(c, attrs, defStyleAttr, defStyleRes)

    public fun setCoordinates(x: ArrayList<Float>, y: ArrayList<Float>){
        xWave = x
        yWave = y
    }
    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (xWave == null || yWave == null){
            Log.d(TAG, "method setCoordinates has not been invoked with valid coordinates.")
            return;
        }
        var i = 0
        paint = Paint()
        paint!!.color = Color.BLUE
        paint!!.strokeWidth = 3f
        while (i < xWave!!.size - 1) {
            Log.d(TAG, String.format("Drawing Line %d", i))
            canvas.drawLine(xWave!![i], yWave!![i], xWave!![i + 1], yWave!![i + 1], paint!!)
            i++
        }
        //connect to baseline: ~first y point.
        canvas.drawLine(
            xWave!![xWave!!.size - 1], yWave!![yWave!!.size - 1], xWave!![xWave!!.size - 1], yWave!![0],
            paint!!
        )
        paint!!.color = Color.RED
        paint!!.strokeWidth = 5f
        canvas.drawLine(0f, yWave!![0], w.toFloat(), yWave!![0], paint!!)
    }

    //https://stackoverflow.com/questions/6652400/how-can-i-get-the-canvas-size-of-a-custom-view-outside-of-the-ondraw-method
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        this.w = w
        this.h = h
    }
}