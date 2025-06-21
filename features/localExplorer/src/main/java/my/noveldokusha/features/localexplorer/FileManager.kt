package my.noveldokusha.features.localexplorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import my.noveldokusha.core.appPreferences.SettingsManager
import my.noveldoksuha.coreui.components.Viewing
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@Suppress("MemberVisibilityCanBePrivate")
object FileManager {

    private lateinit var activity: ComponentActivity
    private lateinit var settingsManager: SettingsManager

    fun init(activity: ComponentActivity) {
        FileManager.activity = activity
        settingsManager = SettingsManager(activity)
        requestPermissions(denied = { activity.finishAffinity() })
    }

    enum class Sorting {
        NAME_ASC,
        NAME_DES,
        SIZE_ASC,
        SIZE_DES,
        TYPE_ASC,
        TYPE_DES,
        DATE_ASC,
        DATE_DES;

        val isAscendingOrder: Boolean
            get() = this == NAME_ASC ||
                    this == SIZE_ASC ||
                    this == TYPE_ASC ||
                    this == DATE_ASC
    }

    var sorting: Sorting = Sorting.DATE_ASC
    var viewing: Viewing = Viewing.LIST

    fun requestPermissions(granted: () -> Unit = {}, denied: () -> Unit = {}) {
        @Suppress("MemberVisibilityCanBePrivate", "MemberVisibilityCanBePrivate") val requestStoragePermissionLauncher =
            activity.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    granted()
                } else {
                    denied()
                }
            }
        if(!checkPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uri = Uri.parse("package:my.noveldokusha")
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    )
                )
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun checkPermissions() : Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun getStorageRoot(): File? {
        return if (checkPermissions())
            Environment.getExternalStorageDirectory()
        else null
    }

    fun getParents(current: File? = getCurrentDirectory()) : List<File> {
        val parents: ArrayList<File> = ArrayList()
        if (current?.absolutePath == getStorageRoot()?.absolutePath)
            return emptyList()
        val parent = current?.parentFile
        parent?.let {
            parents.add(it)
            parents.addAll(getParents(it))
        }
        return parents.reversed()
    }

    fun getCurrentDirectory(): File? {
        return if (checkPermissions()) {
            val path = settingsManager.currentDirectory
            if (path.isNotEmpty()) File(path) else getStorageRoot()
        } else null
    }

    fun goTo(directory: File? = getCurrentDirectory()): List<File> {
        return if (checkPermissions() && directory != null && directory.isDirectory) {
            settingsManager.currentDirectory = directory.absolutePath
            sortFiles( directory.listFiles()?.asList() ?: emptyList())
        } else emptyList()

    }

    fun getCachedNovelCover(filename: String): Bitmap? {
        val validFileName = filename.takeIf { it.length < 21 } ?: filename.substring(0, 20)
        val file = File(activity.cacheDir, "$validFileName.png")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.path)
        } else null
    }

    fun saveNovelCover(filename: String, bitmap: Bitmap) {
        //create a file to write bitmap data
        val validFileName = filename.takeIf { it.length < 21 } ?: filename.substring(0, 20)
        val f = File(activity.cacheDir, "$validFileName.png")
        f.parentFile?.mkdirs()
        f.createNewFile()

        //Convert bitmap to byte array
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos)
        val bitmapData = bos.toByteArray()

        //write the bytes in file
        val fos = FileOutputStream(f)
        fos.write(bitmapData)
        fos.flush()
        fos.close()
    }


    private fun sortFiles(files: List<File>): List<File> {
        val directories: ArrayList<File> = ArrayList()
        val singleFiles: ArrayList<File> = ArrayList()
        files.forEach { file ->
            if (file.isDirectory)
                directories.add(file)
            else if (file.isFile)
                singleFiles.add(file)
        }
        return if (sorting.isAscendingOrder) {
            directories.sortedBy {
                when(sorting) {
                    Sorting.NAME_ASC,
                    Sorting.SIZE_ASC,
                    Sorting.TYPE_ASC -> it.name
                    Sorting.DATE_ASC -> it.lastModified().toString()
                    else -> it.name
                }
            } + singleFiles.sortedBy {
                when (sorting) {
                    Sorting.NAME_ASC -> it.name
                    Sorting.SIZE_ASC -> it.length().toString()
                    Sorting.TYPE_ASC -> it.extension
                    Sorting.DATE_ASC -> it.lastModified().toString()
                    else -> it.name
                }
            }
        } else {
            directories.sortedByDescending {
                when(sorting) {
                    Sorting.NAME_DES,
                    Sorting.SIZE_DES,
                    Sorting.TYPE_DES -> it.name
                    Sorting.DATE_DES -> it.lastModified().toString()
                    else -> it.name
                }
            } + singleFiles.sortedByDescending {
                when (sorting) {
                    Sorting.NAME_DES -> it.name
                    Sorting.SIZE_DES -> it.length().toString()
                    Sorting.TYPE_DES -> it.extension
                    Sorting.DATE_DES -> it.lastModified().toString()
                    else -> it.name
                }
            }
        }
    }
}