package com.documentscanner.sdk

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DocumentScannerViewModel : ViewModel() {
    private val _processedBitmap = MutableLiveData<Bitmap?>()
    val processedBitmap: LiveData<Bitmap?> = _processedBitmap

    fun setProcessedBitmap(bitmap: Bitmap?) {
        _processedBitmap.postValue(bitmap)
    }
}



