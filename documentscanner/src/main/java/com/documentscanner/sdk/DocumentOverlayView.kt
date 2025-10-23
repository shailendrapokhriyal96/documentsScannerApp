package com.documentscanner.sdk

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var documentCorners: Array<android.graphics.Point>? = null
    private val paint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 15f
        style = Paint.Style.STROKE
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.BLACK) // Stronger shadow for better visibility
        alpha = 255 // Full opacity
    }
    
    private val cornerPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 20f
        style = Paint.Style.STROKE
        isAntiAlias = true
        setShadowLayer(12f, 0f, 0f, Color.BLACK) // Stronger shadow for better visibility
        alpha = 255 // Full opacity
    }
    
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#40FFFF00") // Semi-transparent yellow fill
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    fun setDocumentCorners(corners: Array<android.graphics.Point>) {
        documentCorners = corners
        invalidate()
    }
    
    fun clearOverlay() {
        documentCorners = null
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        documentCorners?.let { corners ->
            if (corners.size == 4) {
                drawDocumentOutline(canvas, corners)
                drawCornerMarkers(canvas, corners)
            }
        }
    }
    
    private fun drawDocumentOutline(canvas: Canvas, corners: Array<android.graphics.Point>) {
        val path = Path()
        
        // Move to first corner
        path.moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
        
        // Draw lines to each corner
        for (i in 1 until corners.size) {
            path.lineTo(corners[i].x.toFloat(), corners[i].y.toFloat())
        }
        
        // Close the path
        path.close()
        
        // Draw yellow fill first
        canvas.drawPath(path, fillPaint)
        
        // Draw bright yellow border
        canvas.drawPath(path, paint)
    }
    
    private fun drawCornerMarkers(canvas: Canvas, corners: Array<android.graphics.Point>) {
        val cornerRadius = 25f
        
        corners.forEach { corner ->
            // Draw filled yellow circle
            canvas.drawCircle(
                corner.x.toFloat(),
                corner.y.toFloat(),
                cornerRadius,
                Paint().apply {
                    color = Color.YELLOW
                    style = Paint.Style.FILL
                    setShadowLayer(10f, 0f, 0f, Color.BLACK)
                }
            )
            
            // Draw border circle
            canvas.drawCircle(
                corner.x.toFloat(),
                corner.y.toFloat(),
                cornerRadius,
                cornerPaint
            )
        }
    }
}
