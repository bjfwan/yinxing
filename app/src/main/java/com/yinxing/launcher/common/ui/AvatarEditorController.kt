package com.yinxing.launcher.common.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AvatarEditorController(
    private val activity: AppCompatActivity,
    private val onAvatarReady: (Uri, Bitmap) -> Unit
) {
    fun edit(sourceUri: Uri) {
        activity.lifecycleScope.launch {
            val bitmap = runCatching {
                MediaThumbnailLoader.loadBitmap(activity, sourceUri, 1200, 1200)
            }.getOrNull()
            if (bitmap == null) {
                Toast.makeText(activity, R.string.avatar_load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            showEditor(bitmap)
        }
    }

    private fun showEditor(bitmap: Bitmap) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_avatar_editor, null)
        val cropView = view.findViewById<AvatarCropView>(R.id.avatar_crop_view)
        val confirm = view.findViewById<MaterialCardView>(R.id.btn_avatar_confirm)
        val dialog = LauncherDialogFactory.create(activity, view, dismissOnTouchOutside = false)
        cropView.setBitmap(bitmap)
        view.findViewById<MaterialCardView>(R.id.btn_avatar_cancel).setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            confirm.isClickable = false
            view.findViewById<TextView>(R.id.tv_avatar_confirm).text = activity.getString(R.string.action_processing)
            activity.lifecycleScope.launch {
                val croppedBitmap = cropView.createCroppedBitmap()
                val uri = withContext(Dispatchers.IO) {
                    saveTemporaryCrop(croppedBitmap)
                }
                if (uri == null) {
                    confirm.isClickable = true
                    view.findViewById<TextView>(R.id.tv_avatar_confirm).text = activity.getString(R.string.action_use_photo)
                    Toast.makeText(activity, R.string.avatar_save_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                onAvatarReady(uri, croppedBitmap)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun saveTemporaryCrop(bitmap: Bitmap): Uri? {
        return runCatching {
            val directory = File(activity.cacheDir, "avatar-edits").apply { mkdirs() }
            val target = File(directory, "avatar-${System.currentTimeMillis()}.jpg")
            FileOutputStream(target).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            directory.listFiles()
                ?.sortedByDescending(File::lastModified)
                ?.drop(8)
                ?.forEach(File::delete)
            Uri.fromFile(target)
        }.getOrNull()
    }
}
