package com.example.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import java.util.Stack


class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private lateinit var drawPath: CustomPath
    private lateinit var canvasBitmap: Bitmap
    private lateinit var drawPaint: Paint
    private lateinit var canvasPaint: Paint
    private var brushSize = 0f
    private var color = Color.BLACK
    private lateinit var canvas: Canvas
    private val paths = ArrayList<CustomPath>()
    private val undoPaths = Stack<CustomPath>()

    init {
        setupDrawing()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(canvasBitmap)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)
        for (path in paths){
            drawPaint.strokeWidth = path.brushThickness
            drawPaint.color = path.color
            canvas.drawPath(path, drawPaint)
        }

        if (!drawPath.isEmpty) {
            drawPaint.strokeWidth = drawPath.brushThickness
            drawPaint.color = drawPath.color
            canvas.drawPath(drawPath, drawPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event?.x
        val touchY = event?.y
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.color = color
                drawPath.brushThickness = brushSize

                drawPath.reset()
                if (touchX != null) {
                    if (touchY != null) {
                        drawPath.moveTo(touchX, touchY)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchX != null) {
                    if (touchY != null) {
                        drawPath.lineTo(touchX, touchY)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                paths.add(drawPath)
                drawPath = CustomPath(color, brushSize)
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun setupDrawing() {
        drawPaint = Paint()
        drawPath = CustomPath(color, brushSize)
        drawPaint.color = color
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND
        canvasPaint = Paint(Paint.DITHER_FLAG)
    }
    fun setSizeForBrush(newSize : Float){
        brushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        newSize, resources.displayMetrics)
        drawPaint.strokeWidth = brushSize
    }
    fun setColor(string: String){
        color = Color.parseColor(string)
        drawPaint.color = color
    }
    fun onClickUndo(){
        if (paths.size > 0){
            undoPaths.push(paths.removeAt(paths.size - 1))
            invalidate()
        }
    }
    fun onClickRedo(){
        if (!undoPaths.isEmpty()){
            paths.add(undoPaths.pop())
            invalidate()
        }
    }

    internal inner class CustomPath(var color: Int, var brushThickness: Float) : Path() {
    }
}