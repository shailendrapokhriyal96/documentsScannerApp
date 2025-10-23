package com.documentScanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.documentscanner.sdk.DocumentScannerSDK
import com.documentScanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val documentScanner = DocumentScannerSDK()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.btnScanDocument.setOnClickListener {
            startDocumentScanning()
        }
    }
    
    private fun startDocumentScanning() {
        documentScanner.startScanning(this, REQUEST_CODE_SCAN)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SCAN) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val documentsCount = data?.getIntExtra(DocumentScannerSDK.RESULT_DOCUMENTS_CAPTURED, 0) ?: 0
                    Toast.makeText(this, "Captured $documentsCount documents", Toast.LENGTH_SHORT).show()
                }
                Activity.RESULT_CANCELED -> {
                    Toast.makeText(this, "Document scanning cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    companion object {
        private const val REQUEST_CODE_SCAN = 1001
    }
}