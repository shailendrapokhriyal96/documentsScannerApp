package com.documentscanner.sdk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

class DocumentAnalyzer(
    private val onDocumentDetected: (Array<org.opencv.core.Point>?) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastDetectionTime = 0L
    private val detectionInterval = 100L // Detect every 100ms for faster AI detection
    
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < detectionInterval) {
            imageProxy.close()
            return
        }
        lastDetectionTime = currentTime
        
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val corners = detectDocumentCorners(bitmap)
                onDocumentDetected(corners)
            } else {
                onDocumentDetected(null)
            }
        } catch (e: Exception) {
            Log.e("DocumentAnalyzer", "Error analyzing image", e)
            onDocumentDetected(null)
        } finally {
            imageProxy.close()
        }
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        try {
            val buffer: ByteBuffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                Log.e("DocumentAnalyzer", "Failed to decode image bytes")
                return null
            }
            return bitmap
        } catch (e: Exception) {
            Log.e("DocumentAnalyzer", "Error converting ImageProxy to Bitmap", e)
            return null
        }
    }
    
    private fun detectDocumentCorners(bitmap: Bitmap): Array<org.opencv.core.Point>? {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        
        // Apply Gaussian blur for better edge detection
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
        
        // Edge detection - optimized for document detection
        val edges = Mat()
        Imgproc.Canny(gray, edges, 20.0, 60.0) // More sensitive edge detection
        
        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        // Find the largest quadrilateral
        var maxArea = 0.0
        var bestContour: MatOfPoint2f? = null
        
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 500) { // Lower minimum area threshold for better detection
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
                
                // Check if it's a quadrilateral (4 corners)
                if (approx.total() == 4L) {
                    // Additional check: ensure it's roughly rectangular
                    val points = approx.toArray()
                    if (isRectangularShape(points) && area > maxArea) {
                        maxArea = area
                        bestContour = approx
                    }
                }
            }
        }
        
        val result = if (bestContour != null) {
            val points = bestContour.toArray()
            if (points.size == 4) {
                // Order points: top-left, top-right, bottom-right, bottom-left
                val orderedPoints = orderPoints(points)
                Log.d("DocumentAnalyzer", "Document detected with area: $maxArea")
                orderedPoints
            } else {
                Log.d("DocumentAnalyzer", "Document detected but not 4 corners: ${points.size}")
                null
            }
        } else {
            Log.d("DocumentAnalyzer", "No document detected")
            null
        }
        
        return result
    }
    
    private fun isRectangularShape(points: Array<org.opencv.core.Point>): Boolean {
        if (points.size != 4) return false
        
        // Calculate angles between consecutive points
        val angles = mutableListOf<Double>()
        for (i in 0 until 4) {
            val p1 = points[i]
            val p2 = points[(i + 1) % 4]
            val p3 = points[(i + 2) % 4]
            
            val angle = calculateAngle(p1, p2, p3)
            angles.add(angle)
        }
        
        // Check if angles are roughly 90 degrees (allowing some tolerance)
        return angles.all { angle -> Math.abs(angle - 90.0) < 30.0 }
    }
    
    private fun calculateAngle(p1: org.opencv.core.Point, p2: org.opencv.core.Point, p3: org.opencv.core.Point): Double {
        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y
        
        val dot = v1x * v2x + v1y * v2y
        val mag1 = Math.sqrt(v1x * v1x + v1y * v1y)
        val mag2 = Math.sqrt(v2x * v2x + v2y * v2y)
        
        if (mag1 == 0.0 || mag2 == 0.0) return 0.0
        
        val cosAngle = dot / (mag1 * mag2)
        val angle = Math.acos(Math.max(-1.0, Math.min(1.0, cosAngle)))
        return Math.toDegrees(angle)
    }
    
    private fun orderPoints(pts: Array<org.opencv.core.Point>): Array<org.opencv.core.Point> {
        // Sort by y-coordinate first, then by x-coordinate
        val sorted = pts.sortedWith(compareBy<org.opencv.core.Point> { it.y }.thenBy { it.x })
        
        // Top two points (smallest y values)
        val topTwo = sorted.take(2).sortedBy { it.x }
        
        // Bottom two points (largest y values)  
        val bottomTwo = sorted.takeLast(2).sortedBy { it.x }
        
        return arrayOf(
            topTwo[0],      // top-left
            topTwo[1],      // top-right
            bottomTwo[1],   // bottom-right
            bottomTwo[0]    // bottom-left
        )
    }
}
