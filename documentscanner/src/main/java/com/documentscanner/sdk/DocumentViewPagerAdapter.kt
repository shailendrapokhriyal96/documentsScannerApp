package com.documentscanner.sdk

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class DocumentViewPagerAdapter(
    private val documents: List<Bitmap>
) : RecyclerView.Adapter<DocumentViewPagerAdapter.DocumentViewHolder>() {

    class DocumentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.documentImageView)
        
        init {
            // Ensure the image view can handle touch events properly
            imageView.isClickable = true
            imageView.isFocusable = true
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document_view, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.imageView.setImageBitmap(documents[position])
    }

    override fun getItemCount(): Int = documents.size
}
