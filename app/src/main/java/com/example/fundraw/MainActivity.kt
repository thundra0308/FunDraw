package com.example.fundraw

import android.app.AlertDialog
import android.app.Dialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.get
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.Image
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.nvt.color.ColorPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception


class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    private val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        if(result.resultCode == RESULT_OK && result.data!=null) {
            val imageBackground: ImageView = findViewById(R.id.ivbackground)
            imageBackground.setImageURI(result.data?.data)
        }
    }

    private val request:  ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if(isGranted) {
                if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                }
                if(permissionName == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    lifecycleScope.launch {
                        val flDrawingView: FrameLayout = findViewById(R.id.fldrawingviewcontainer)
                        saveBitmapFile(getBitmapFromView(flDrawingView))
                    }
                }
            } else {
                if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                    Toast.makeText(this,"Permission Denied for Storage Read", Toast.LENGTH_SHORT).show()
                }
                if(permissionName == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    Toast.makeText(this,"Permission Denied for Storage Write", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE); // for hiding title

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        drawingView?.setSizeForBrush(20.toFloat())

        mImageButtonCurrentPaint = findViewById(R.id.pickcolor)
        var mDefaultColor: Int = 0
        mImageButtonCurrentPaint?.setOnClickListener {
            val colorPicker = ColorPickerDialog(this, Color.BLACK,true,
            object : ColorPickerDialog.OnColorPickerListener {
                override fun onCancel(dialog: ColorPickerDialog?) {

                }

                override fun onOk(dialog: ColorPickerDialog?, color: Int) {
                    mDefaultColor = color
                    drawingView?.setColor(mDefaultColor)
                }
            }
                )
            colorPicker.show()
        }

        val brush: ImageButton = findViewById(R.id.brush)
        brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val galleryButton: ImageButton = findViewById(R.id.gallery)
        galleryButton.setOnClickListener {
            when {
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                    showRationalDialog("FunDraw Requires Storage Permission", "Access is Denied\n Enable the Storage Read Permission from Permission Manager")
                }
                else -> {
                    request.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            }
        }

        val undo: ImageButton = findViewById(R.id.undo)
        undo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val save: ImageButton = findViewById(R.id.save)
        save.setOnClickListener {
            when {
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    showRationalDialog("FunDraw Requires Storage Permission", "Access is Denied\n Enable the Storage Write Permission from Permission Manager")
                }
                else -> {
                    request.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            }
        }

    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.smallBrush)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.mediumBrush)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.largeBrush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if(mBitmap!=null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
                    val f = File(externalCacheDir?.absoluteFile.toString() + File.separator + "FunDrawApp" + System.currentTimeMillis()/1000 + ".jpeg")
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath
                    runOnUiThread {
                        if(result.isNotEmpty()) {
                            Toast.makeText(applicationContext, "File Saved Successfully :$result", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(applicationContext, "Something Went Wrong", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }


    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private fun showRationalDialog(title: String, message: String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        .setMessage(message)
        .setPositiveButton("Cancel") { dialog, _->
            dialog.dismiss()
        }
        builder.create().show()
    }

}