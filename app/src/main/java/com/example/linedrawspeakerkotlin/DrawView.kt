package com.example.linedrawspeakerkotlin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.widget.LinearLayout
import java.util.*

class DrawView (context: Context?, private var _x: ArrayList<Float>, private var _y: ArrayList<Float>) : LinearLayout(context) {

    private val TAG = "DrawView" //For Logs
    private var paint: Paint? = null
    private var w = 0
    private var h = 0
    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var i = 0
        paint = Paint()
        paint!!.color = Color.BLUE
        paint!!.strokeWidth = 3f
        while (i < _x.size - 1) {
            Log.d(TAG, String.format("Drawing Line %d", i))
            canvas.drawLine(_x[i], _y[i], _x[i + 1], _y[i + 1], paint!!)
            i++
        }
        //connect to baseline: ~first y point.
        canvas.drawLine(
            _x[_x.size - 1], _y[_y.size - 1], _x[_x.size - 1], _y[0],
            paint!!
        )
        paint!!.color = Color.RED
        paint!!.strokeWidth = 5f
        canvas.drawLine(0f, _y[0], w.toFloat(), _y[0], paint!!)
    }

    //https://stackoverflow.com/questions/6652400/how-can-i-get-the-canvas-size-of-a-custom-view-outside-of-the-ondraw-method
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        this.w = w
        this.h = h
    }
}