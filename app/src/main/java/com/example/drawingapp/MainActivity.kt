package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_brush_size.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {


    private val cropImage =
        registerForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                iv_background.setImageURI(result.uriContent)
            }
        }

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                iv_background.setImageURI(result.data?.data)
                iv_background.tag = result.data?.data.toString()
            }
        }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val name = it.key
                val isGranted = it.value
                if (isGranted) {
                    val pickIntent = Intent(
                        Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    )
                    openGalleryLauncher.launch(pickIntent)

                } else {
                    Toast.makeText(
                        this,
                        "Permission for $name not granted", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawing_view.setSizeForBrush(Constants.MEDIUM_BRUSH_SIZE)
        ib_brush.setOnClickListener { showBrushSizeDialog() }
        ll_colors.forEach { me -> me.setOnClickListener { paintClicked(it as ImageButton) } }
        ib_gallery.setOnClickListener {
            requestPermissions()
        }
        ib_undo.setOnClickListener { drawing_view.onClickUndo() }
        ib_redo.setOnClickListener { drawing_view.onClickRedo() }
        ib_save.setOnClickListener {
            if (isReadPermissionGranted()) {
                lifecycleScope.launch {
                    saveBitMapFile(getBitMapFromView(fl_image))
                }
            }
        }
        ib_clear.setOnClickListener {
            drawing_view.onClickClear()
            iv_background.setImageURI(null)
            iv_background.tag = ""
        }
        ib_crop.setOnClickListener{ startCrop() }


    }


    private fun isReadPermissionGranted(): Boolean {
        val res = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return res == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            showDialogRationale(
                "Drawing App", "Drawing App needs your permissions to " +
                        "access the gallery"
            )

        } else {
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun showDialogRationale(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }


    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        fun setSizeByButton(size: Float) {
            drawing_view.setSizeForBrush(size)
            brushDialog.dismiss()
        }
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.ib_size_small.setOnClickListener {
            setSizeByButton(Constants.SMALL_BRUSH_SIZE)
        }
        brushDialog.ib_size_large.setOnClickListener {
            setSizeByButton(Constants.LARGE_BRUSH_SIZE)
        }
        brushDialog.ib_size_medium.setOnClickListener {
            setSizeByButton(Constants.MEDIUM_BRUSH_SIZE)
        }
        brushDialog.show()
    }

    private fun paintClicked(view: ImageButton) {
        drawing_view.setColor(view.tag.toString())
        view.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )
        ll_colors.forEach {
            val me = it as ImageButton
            if (it !== view) {
                me.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.pallet_normal)
                )
            }
        }
    }

    private fun getBitMapFromView(view: View): Bitmap {
        val res = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(res)
        val bgImage = view.background
        if (bgImage != null) {
            bgImage.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return res
    }

    private suspend fun saveBitMapFile(mBitmap: Bitmap?): String {
        var res = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    val file = File(
                        externalCacheDir?.absoluteFile.toString()
                                + File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000
                                + ".png"
                    )

                    val fo = FileOutputStream(file)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    res = file.absolutePath

                    runOnUiThread {
                        if (res.isNotEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully on: $res", Toast.LENGTH_SHORT
                            ).show()
                            shareImage(res)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Oops! Something went wrong!", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    res = ""
                    e.printStackTrace()
                }
            }
        }
        return res
    }

    private fun shareImage(result: String) {
        MediaScannerConnection.scanFile(this, arrayOf(result), null) { path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share Via:"))
        }
    }

    private fun startCrop() {
        // start picker to get image for cropping and then use the image in cropping activity
        val currUri = iv_background.tag.toString().toUri()
        if (currUri == Uri.EMPTY){
            Snackbar.make(drawing_view, "No Image to crop", Snackbar.LENGTH_SHORT).show()
        }
        else {
            cropImage.launch(
                options(uri = currUri) {
                    setGuidelines(CropImageView.Guidelines.ON)
                    setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                }
            )
        }
    }


}