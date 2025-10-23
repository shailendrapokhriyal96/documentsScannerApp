package com.documentscanner.sdk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.documentscanner.sdk.databinding.ActivityDocumentScannerBinding
import com.documentscanner.sdk.databinding.DocumentFinalViewBinding
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Date

class DocumentScannerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDocumentScannerBinding
    private lateinit var finalDocumentBinding: DocumentFinalViewBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: androidx.camera.core.Camera? = null
    
    // Document scanning state
    private var isScanning = false
    private var capturedDocuments = mutableListOf<Bitmap>()
    private var currentDocumentCorners: Array<org.opencv.core.Point>? = null
    private var isOpenCVAvailable = false
    
    // UI state
    private var isFlashOn = false
    private var isAutoMode = true
    
    // ViewPager adapter
    private lateinit var documentAdapter: DocumentViewPagerAdapter
    private lateinit var finalDocumentAdapter: DocumentViewPagerAdapter
    private var isDocumentViewVisible = false
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize final document view binding
        finalDocumentBinding = DocumentFinalViewBinding.inflate(layoutInflater)
        
        // Add the final document view to the container
        binding.finalDocumentView.addView(finalDocumentBinding.root)
        
        // Initialize OpenCV
        try {
            if (!OpenCVLoader.initDebug()) {
                Log.e("OpenCV", "Unable to load OpenCV - continuing without advanced features")
                Toast.makeText(this, "OpenCV not available - using basic mode", Toast.LENGTH_SHORT).show()
                isOpenCVAvailable = false
            } else {
                Log.d("OpenCV", "OpenCV loaded successfully")
                isOpenCVAvailable = true
            }
        } catch (e: Exception) {
            Log.e("OpenCV", "OpenCV initialization error - continuing without advanced features", e)
            Toast.makeText(this, "OpenCV not available - using basic mode", Toast.LENGTH_SHORT).show()
            isOpenCVAvailable = false
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupUI()
        checkCameraPermission()
    }
    
    private fun setupUI() {
        // Cancel button
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        
        // Flash toggle
        binding.btnFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            binding.btnFlash.setImageResource(
                if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
            camera?.cameraControl?.enableTorch(isFlashOn)
        }
        
        // Auto mode toggle (only available when OpenCV is working)
        binding.btnAuto.setOnClickListener {
            if (isOpenCVAvailable) {
                isAutoMode = !isAutoMode
                binding.btnAuto.setTextColor(
                    if (isAutoMode) Color.WHITE else Color.GRAY
                )
            } else {
                Toast.makeText(this, "Auto mode requires AI detection", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Manual capture button
        binding.btnCapture.setOnClickListener {
            if (isOpenCVAvailable) {
                if (currentDocumentCorners != null) {
                    captureDocument()
                } else {
                    Toast.makeText(this, "No document detected", Toast.LENGTH_SHORT).show()
                }
            } else {
                // When OpenCV is not available, allow manual capture without detection
                captureDocument()
            }
        }
        
        // Save button
        binding.btnSave.setOnClickListener {
            saveDocument()
        }
        
        // Captured preview click listener
        binding.capturedPreview.setOnClickListener {
            showFullScreenDocumentView()
        }
        
        // Back to camera button
        binding.btnBackToCamera.setOnClickListener {
            hideFullScreenDocumentView()
        }
        
        // Save document button
        binding.btnSaveDocument.setOnClickListener {
            saveDocument()
        }
        
        // Final document view controls
        setupFinalDocumentView()
        
        // Initialize ViewPager
        initializeViewPager()
        
        // Update status text
        updateStatusText()
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA
            ) -> {
                Toast.makeText(this, "Camera permission is required for document scanning", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            val imageAnalyzer = if (isOpenCVAvailable) {
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, DocumentAnalyzer { corners: Array<org.opencv.core.Point>? ->
                            runOnUiThread {
                                updateDocumentOverlay(corners)
                            }
                        })
                    }
            } else {
                // Create a dummy analyzer that does nothing when OpenCV is not available
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                            imageProxy.close() // Just close the image without processing
                        })
                    }
            }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun updateDocumentOverlay(corners: Array<org.opencv.core.Point>?) {
        currentDocumentCorners = corners
        
        if (corners != null && corners.size == 4) {
            // Convert OpenCV points to Android points for UI
            val androidPoints = corners.map { 
                android.graphics.Point(it.x.toInt(), it.y.toInt()) 
            }.toTypedArray()
            
            // Show document detected overlay with BRIGHT YELLOW BORDER
            binding.documentOverlay.visibility = View.VISIBLE
            binding.documentOverlay.setDocumentCorners(androidPoints)
            
            Log.d("DocumentScanner", "AI detected document with corners: ${androidPoints.size}")
            
            // Update status with prominent feedback
            updateStatusText("✓ Document detected! Hold steady...")
            
            // Auto capture if in auto mode
            if (isAutoMode && !isScanning) {
                // Use Handler for delayed execution instead of coroutines
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (currentDocumentCorners != null) {
                        captureDocument()
                    }
                }, 1000) // Wait 1 second for stable detection
            }
        } else {
            // Hide overlay and clear it
            binding.documentOverlay.visibility = View.GONE
            binding.documentOverlay.clearOverlay()
            if (isOpenCVAvailable) {
                updateStatusText("Position the document in view")
            } else {
                updateStatusText("Position document and tap to capture")
            }
        }
    }
    
    private fun captureDocument() {
        if (isScanning) return
        
        isScanning = true
        updateStatusText("Capturing document...")
        
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processCapturedImage(image)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                    isScanning = false
                    updateStatusText("Capture failed")
                }
            }
        )
    }
    
    private fun processCapturedImage(image: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(image)
            val processedBitmap = OpenCvDocumentProcessor.detectAndWarp(bitmap)
            
            if (processedBitmap != null) {
                capturedDocuments.add(processedBitmap)
                updateStatusText("Ready for next scan.")
                
                // Show preview of captured document in center
                binding.capturedPreview.setImageBitmap(processedBitmap)
                binding.capturedPreview.visibility = View.VISIBLE
                
                // Show save button in right bottom
                binding.btnSave.visibility = View.VISIBLE
                binding.btnSave.text = "Save (${capturedDocuments.size})"
                
                // Update ViewPager adapter if it exists
                if (::documentAdapter.isInitialized) {
                    documentAdapter = DocumentViewPagerAdapter(capturedDocuments)
                    binding.documentViewPager.adapter = documentAdapter
                }
                
                // Update final document adapter if it exists
                if (::finalDocumentAdapter.isInitialized) {
                    finalDocumentAdapter = DocumentViewPagerAdapter(capturedDocuments)
                    finalDocumentBinding.finalDocumentViewPager.adapter = finalDocumentAdapter
                }
                
                // Reset scanning state
                isScanning = false
                currentDocumentCorners = null
            } else {
                Toast.makeText(this, "Failed to process document", Toast.LENGTH_SHORT).show()
                isScanning = false
                updateStatusText("Position the document in view")
            }
        } catch (e: Exception) {
            Log.e("DocumentScanner", "Error processing image", e)
            isScanning = false
            updateStatusText("Position the document in view")
        } finally {
            image.close()
        }
    }
    
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    private fun updateStatusText(text: String? = null) {
        val statusText = text ?: if (capturedDocuments.isEmpty()) {
            "Position the document in view"
        } else {
            "Ready for next scan"
        }
        binding.tvStatus.text = statusText
        
        // Change text color based on status
        if (statusText.contains("detected", ignoreCase = true)) {
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            binding.tvStatus.textSize = 18f // Make it bigger for detection
        } else {
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.tvStatus.textSize = 16f
        }
    }
    
    // Show document view on same page after save
    private fun showDocumentView() {
        if (capturedDocuments.isNotEmpty()) {
            // Hide camera preview and show document view
            binding.previewView.visibility = View.GONE
            binding.documentOverlay.visibility = View.GONE
            binding.capturedPreview.visibility = View.GONE
            binding.btnSave.visibility = View.GONE
            
            // Show the captured document in full screen
            val documentBitmap = capturedDocuments.last()
            binding.capturedPreview.setImageBitmap(documentBitmap)
            binding.capturedPreview.visibility = View.VISIBLE
            binding.capturedPreview.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            binding.capturedPreview.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            binding.capturedPreview.scaleType = ImageView.ScaleType.FIT_CENTER
            
            // Update status
            updateStatusText("Document saved! Tap to scan another.")
            
            // Add tap to continue scanning
            binding.capturedPreview.setOnClickListener {
                // Reset to camera view
                binding.previewView.visibility = View.VISIBLE
                binding.documentOverlay.visibility = View.VISIBLE
                binding.capturedPreview.layoutParams.width = 200
                binding.capturedPreview.layoutParams.height = 150
                binding.capturedPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                updateStatusText("Position the document in view")
            }
        } else {
            Toast.makeText(this, "No documents captured", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Initialize ViewPager for document viewing
    private fun initializeViewPager() {
        documentAdapter = DocumentViewPagerAdapter(capturedDocuments)
        binding.documentViewPager.adapter = documentAdapter
        
        // Configure ViewPager to prevent navigation issues
        binding.documentViewPager.isUserInputEnabled = true
        binding.documentViewPager.offscreenPageLimit = 1
        
        // Add page change listener to update page indicators
        binding.documentViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicators(position)
            }
        })
    }
    
    // Update page indicators
    private fun updatePageIndicators(currentPosition: Int) {
        binding.pageIndicator.removeAllViews()
        
        for (i in capturedDocuments.indices) {
            val indicator = ImageView(this)
            val layoutParams = ViewGroup.LayoutParams(24, 24)
            indicator.layoutParams = layoutParams
            indicator.setPadding(8, 0, 8, 0)
            
            if (i == currentPosition) {
                indicator.setImageResource(android.R.drawable.radiobutton_on_background)
            } else {
                indicator.setImageResource(android.R.drawable.radiobutton_off_background)
            }
            
            binding.pageIndicator.addView(indicator)
        }
    }
    
    // Show full screen document view when clicking on preview
    private fun showFullScreenDocumentView() {
        if (capturedDocuments.isNotEmpty()) {
            isDocumentViewVisible = true
            
            // Hide camera and controls
            binding.previewView.visibility = View.GONE
            binding.documentOverlay.visibility = View.GONE
            binding.capturedPreview.visibility = View.GONE
            binding.btnSave.visibility = View.GONE
            binding.topControls.visibility = View.GONE
            binding.bottomControls.visibility = View.GONE
            binding.tvStatus.visibility = View.GONE
            
            // Update adapter with current documents
            documentAdapter = DocumentViewPagerAdapter(capturedDocuments)
            binding.documentViewPager.adapter = documentAdapter
            
            // Show document view container and ViewPager
            binding.documentViewContainer.visibility = View.VISIBLE
            binding.pageIndicator.visibility = View.VISIBLE
            
            // Show document viewer controls
            binding.documentViewerControls.visibility = View.VISIBLE
            
            // Update page indicators
            updatePageIndicators(0)
            
            // Disable back button handling while in document view
            binding.documentViewPager.isUserInputEnabled = true
        }
    }
    
    // Hide full screen document view and return to camera
    private fun hideFullScreenDocumentView() {
        isDocumentViewVisible = false
        
        // Show camera and controls
        binding.previewView.visibility = View.VISIBLE
        binding.documentOverlay.visibility = View.VISIBLE
        binding.topControls.visibility = View.VISIBLE
        binding.bottomControls.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        
        // Hide document view container and page indicators
        binding.documentViewContainer.visibility = View.GONE
        binding.pageIndicator.visibility = View.GONE
        binding.documentViewerControls.visibility = View.GONE
        
        // Show preview if we have captured documents
        if (capturedDocuments.isNotEmpty()) {
            val documentBitmap = capturedDocuments.last()
            binding.capturedPreview.setImageBitmap(documentBitmap)
            binding.capturedPreview.visibility = View.VISIBLE
            binding.btnSave.visibility = View.VISIBLE
            binding.btnSave.text = "Save (${capturedDocuments.size})"
            
            // Re-enable preview click listener
            binding.capturedPreview.setOnClickListener {
                showFullScreenDocumentView()
            }
        }
        
        updateStatusText("Ready for next scan.")
    }
    
    // Setup final document view controls
    private fun setupFinalDocumentView() {
        // Back button - resets to initial camera state
        finalDocumentBinding.btnBack.setOnClickListener {
            hideFinalDocumentView()
        }
        
        // Share button
        finalDocumentBinding.btnShare.setOnClickListener {
            // Implement share functionality
            Toast.makeText(this, "Share document", Toast.LENGTH_SHORT).show()
        }
        
        // More options button
        finalDocumentBinding.btnMore.setOnClickListener {
            // Implement more options
            Toast.makeText(this, "More options", Toast.LENGTH_SHORT).show()
        }
        
        // Bottom action bar buttons
        finalDocumentBinding.btnList.setOnClickListener {
            // Show document list
            Toast.makeText(this, "Document list", Toast.LENGTH_SHORT).show()
        }
        
        finalDocumentBinding.btnCamera.setOnClickListener {
            // Return to camera and reset to initial state
            hideFinalDocumentView()
        }
        
        finalDocumentBinding.btnCompass.setOnClickListener {
            // Compass functionality
            Toast.makeText(this, "Compass", Toast.LENGTH_SHORT).show()
        }
        
        // Initialize final document ViewPager
        initializeFinalDocumentViewPager()
    }
    
    // Initialize final document ViewPager
    private fun initializeFinalDocumentViewPager() {
        finalDocumentAdapter = DocumentViewPagerAdapter(capturedDocuments)
        finalDocumentBinding.finalDocumentViewPager.adapter = finalDocumentAdapter
        
        // Configure ViewPager
        finalDocumentBinding.finalDocumentViewPager.isUserInputEnabled = true
        finalDocumentBinding.finalDocumentViewPager.offscreenPageLimit = 1
        
        // Add page change listener to update page indicators
        finalDocumentBinding.finalDocumentViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateFinalPageIndicators(position)
            }
        })
    }
    
    // Update final page indicators
    private fun updateFinalPageIndicators(currentPosition: Int) {
        finalDocumentBinding.finalPageIndicator.removeAllViews()
        
        for (i in capturedDocuments.indices) {
            val indicator = ImageView(this)
            val layoutParams = ViewGroup.LayoutParams(24, 24)
            indicator.layoutParams = layoutParams
            indicator.setPadding(8, 0, 8, 0)
            
            if (i == currentPosition) {
                indicator.setImageResource(android.R.drawable.radiobutton_on_background)
            } else {
                indicator.setImageResource(android.R.drawable.radiobutton_off_background)
            }
            
            finalDocumentBinding.finalPageIndicator.addView(indicator)
        }
    }
    
    // Save the document
    private fun saveDocument() {
        if (capturedDocuments.isNotEmpty()) {
            // Show final document view
            showFinalDocumentView()
        }
    }
    
    // Show final document view
    private fun showFinalDocumentView() {
        if (capturedDocuments.isNotEmpty()) {
            // Hide all other views
            binding.previewView.visibility = View.GONE
            binding.documentOverlay.visibility = View.GONE
            binding.capturedPreview.visibility = View.GONE
            binding.btnSave.visibility = View.GONE
            binding.topControls.visibility = View.GONE
            binding.bottomControls.visibility = View.GONE
            binding.tvStatus.visibility = View.GONE
            binding.documentViewContainer.visibility = View.GONE
            binding.pageIndicator.visibility = View.GONE
            binding.documentViewerControls.visibility = View.GONE
            
            // Show final document view
            binding.finalDocumentView.visibility = View.VISIBLE
            
            // Update final document adapter with all captured documents
            finalDocumentAdapter = DocumentViewPagerAdapter(capturedDocuments)
            finalDocumentBinding.finalDocumentViewPager.adapter = finalDocumentAdapter
            
            // Update page indicators
            updateFinalPageIndicators(0)
            
            // Set scan date
            val currentDate = DateFormat.format("MMM dd, yyyy", Date()).toString()
            finalDocumentBinding.tvScanDate.text = currentDate
            
            isDocumentViewVisible = false // Reset flag since we're in final view
        }
    }
    
    // Hide final document view and return to camera
    private fun hideFinalDocumentView() {
        // Hide final document view
        binding.finalDocumentView.visibility = View.GONE
        
        // Clear all captured documents to reset to initial state
        capturedDocuments.clear()
        
        // Reset adapters
        if (::documentAdapter.isInitialized) {
            documentAdapter = DocumentViewPagerAdapter(capturedDocuments)
            binding.documentViewPager.adapter = documentAdapter
        }
        
        if (::finalDocumentAdapter.isInitialized) {
            finalDocumentAdapter = DocumentViewPagerAdapter(capturedDocuments)
            finalDocumentBinding.finalDocumentViewPager.adapter = finalDocumentAdapter
        }
        
        // Show camera and controls
        binding.previewView.visibility = View.VISIBLE
        binding.documentOverlay.visibility = View.VISIBLE
        binding.topControls.visibility = View.VISIBLE
        binding.bottomControls.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        
        // Hide preview and save button to return to initial state
        binding.capturedPreview.visibility = View.GONE
        binding.btnSave.visibility = View.GONE
        
        // Reset to initial status text
        updateStatusText("Position the document in view")
        
        // Reset document view visibility flag
        isDocumentViewVisible = false
    }
    
    override fun onBackPressed() {
        if (binding.finalDocumentView.visibility == View.VISIBLE) {
            // If we're in final document view, go back to camera
            hideFinalDocumentView()
        } else if (isDocumentViewVisible) {
            // If we're in document view, go back to camera instead of closing app
            hideFullScreenDocumentView()
        } else {
            // If we're in camera view, close the app
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure proper state when returning to the activity
        if (isDocumentViewVisible && capturedDocuments.isNotEmpty()) {
            // Refresh the adapter if we're in document view
            documentAdapter = DocumentViewPagerAdapter(capturedDocuments)
            binding.documentViewPager.adapter = documentAdapter
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}