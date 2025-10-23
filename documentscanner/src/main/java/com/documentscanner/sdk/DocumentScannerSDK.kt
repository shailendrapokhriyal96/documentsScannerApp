package com.documentscanner.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * Main SDK class for document scanning functionality
 */
class DocumentScannerSDK {

    companion object {
        const val RESULT_DOCUMENTS_CAPTURED = "captured_documents"
        const val RESULT_DOCUMENT_BITMAPS = "document_bitmaps"
    }
    
    /**
     * Start document scanning from an Activity
     */
    fun startScanning(activity: Activity, requestCode: Int = 1001) {
        val intent = Intent(activity, DocumentScannerActivity::class.java)
        activity.startActivityForResult(intent, requestCode)
    }
    
    /**
     * Start document scanning from a Fragment
     */
    fun startScanning(fragment: Fragment, requestCode: Int = 1001) {
        val intent = Intent(fragment.requireContext(), DocumentScannerActivity::class.java)
        fragment.startActivityForResult(intent, requestCode)
    }
    
    
    /**
     * Process captured documents with OpenCV
     */
    fun processDocument(bitmap: Bitmap): Bitmap? {
        return OpenCvDocumentProcessor.detectAndWarp(bitmap)
    }
    
    /**
     * Get document corners from bitmap
     */
    fun detectDocumentCorners(bitmap: Bitmap): Array<android.graphics.Point>? {
        val opencvPoints = OpenCvDocumentProcessor.detectDocumentCorners(bitmap)
        return opencvPoints?.map { 
            android.graphics.Point(it.x.toInt(), it.y.toInt()) 
        }?.toTypedArray()
    }
}

/**
 * Result of document scanning operation
 */
sealed class DocumentScanResult {
    data class Success(val documentsCount: Int) : DocumentScanResult()
    object Cancelled : DocumentScanResult()
    data class Error(val message: String) : DocumentScanResult()
}
