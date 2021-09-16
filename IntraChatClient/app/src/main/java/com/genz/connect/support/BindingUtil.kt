package com.genz.connect.support

import android.os.Environment
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.genz.connect.client.Message
import com.google.android.material.imageview.ShapeableImageView
import java.io.File

object BindingUtil {

    @BindingAdapter("image")
    @JvmStatic
    fun setImage(imageView: ShapeableImageView, value: Message?) {
        if (value?.binary == true) {
            setImage(imageView, value.content)
        }
    }

    @BindingAdapter("reply")
    @JvmStatic
    fun setReplyImage(imageView: ShapeableImageView, value: Message?) {
        if (value?.replyBinary == true) {
            setImage(imageView, value.replyContent)
        }
    }

    @JvmStatic
    private fun setImage(imageView: ShapeableImageView, name: String?) {
        name?.also {
            val file = imageView.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            //val uri = Uri.fromFile(File(file, it))

//            GlobalScope.launch(Dispatchers.IO) {
//                imageView.context.contentResolver.openInputStream(uri)?.use { stream ->
//                    val bitmap = BitmapFactory.decodeStream(stream)
//                    BitmapFactory.Options().apply {
//                        outMimeType = "image/gif"
//
//                    }
//
//                    launch(Dispatchers.Main) {
//                        imageView.setImageBitmap(bitmap)
//                    }
//                }

//            }

            Glide.with(imageView.context)
                .load(File(file, it))
                .override(Target.SIZE_ORIGINAL)
                .into(imageView)

            //imageView.isVisible = true
        }
    }

}