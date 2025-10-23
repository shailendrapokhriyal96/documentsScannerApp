    package com.documentscanner.sdk

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object OpenCvDocumentProcessor {
    
    private var isOpenCVInitialized = false
    
    init {
        // Don't initialize OpenCV here - it will be initialized when first used
        isOpenCVInitialized = false
    }
    
    private fun initializeOpenCV(): Boolean {
        if (isOpenCVInitialized) return true
        
        try {
            if (!org.opencv.android.OpenCVLoader.initDebug()) {
                android.util.Log.e("OpenCV", "Unable to load OpenCV")
                isOpenCVInitialized = false
                return false
            } else {
                android.util.Log.d("OpenCV", "OpenCV loaded successfully")
                isOpenCVInitialized = true
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e("OpenCV", "OpenCV initialization error", e)
            isOpenCVInitialized = false
            return false
        }
    }
    // Simple auto-detect largest quadrilateral and apply perspective transform + cleanup
    fun detectAndWarp(input: Bitmap): Bitmap? {
        if (!initializeOpenCV()) {
            android.util.Log.e("OpenCV", "OpenCV not available - returning original image")
            return input // Return original image when OpenCV is not available
        }
        
        val src = Mat()
        Utils.bitmapToMat(input, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var maxArea = 0.0
        var docContour: MatOfPoint2f? = null
        for (c in contours) {
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(MatOfPoint(*approx.toArray()))
                if (area > maxArea) {
                    maxArea = area
                    docContour = approx
                }
            }
        }

        if (docContour == null) {
            return null
        }

        val ordered = orderPoints(docContour!!.toArray())
        val width = src.width()
        val height = src.height()
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((width - 1).toDouble(), 0.0),
            Point((width - 1).toDouble(), (height - 1).toDouble()),
            Point(0.0, (height - 1).toDouble())
        )
        val m = Imgproc.getPerspectiveTransform(MatOfPoint2f(*ordered), dstPts)
        val warped = Mat(Size(width.toDouble(), height.toDouble()), src.type())
        Imgproc.warpPerspective(src, warped, m, warped.size())

        // Cleanup: convert to grayscale and adaptive threshold for a scan-like look
        val warpedGray = Mat()
        Imgproc.cvtColor(warped, warpedGray, Imgproc.COLOR_BGR2GRAY)
        val thresholded = Mat()
        Imgproc.adaptiveThreshold(warpedGray, thresholded, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 15.0)

        val out = Bitmap.createBitmap(thresholded.cols(), thresholded.rows(), Bitmap.Config.ARGB_8888)
        Imgproc.cvtColor(thresholded, warped, Imgproc.COLOR_GRAY2BGR)
        Utils.matToBitmap(warped, out)
        return out
    }

    private fun orderPoints(pts: Array<Point>): Array<Point> {
        // Order: top-left, top-right, bottom-right, bottom-left
        val sorted = pts.sortedWith(compareBy<Point> { it.y }.thenBy { it.x })
        val topTwo = sorted.take(2).sortedBy { it.x }
        val bottomTwo = sorted.takeLast(2).sortedByDescending { it.x }
        return arrayOf(topTwo[0], topTwo[1], bottomTwo[0], bottomTwo[1])
    }
    
    /**
     * Detect document corners from a bitmap
     */
    fun detectDocumentCorners(input: Bitmap): Array<org.opencv.core.Point>? {
        if (!initializeOpenCV()) {
            android.util.Log.e("OpenCV", "OpenCV not available")
            return null
        }
        
        val src = Mat()
        Utils.bitmapToMat(input, src)
        
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        
        var maxArea = 0.0
        var docContour: MatOfPoint2f? = null
        for (c in contours) {
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(MatOfPoint(*approx.toArray()))
                if (area > maxArea) {
                    maxArea = area
                    docContour = approx
                }
            }
        }
        
        return if (docContour != null) {
            val ordered = orderPoints(docContour.toArray())
            ordered
        } else null
    }
}



