package my.noveldokusha.features.localexplorer.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import my.noveldokusha.core.utils.getThenUpdateFlow
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.features.localexplorer.BrowseModule
import my.noveldokusha.features.localexplorer.extractor.utils.extractor
import my.noveldokusha.features.localexplorer.FileManager
import java.io.File
import javax.inject.Inject

data class BrowseResult(
    val browsables: List<Browsable>,
    val parents: List<File>
)

class BrowseRepository @Inject constructor(
    private val db: AppDatabase
) : BrowseModule.Repository {

    override suspend fun browse(file: File?): Flow<BrowseResult?> {
        val directory = file ?: FileManager.getCurrentDirectory()
        return if (directory != null && directory.isDirectory) {
            // Calculate parents once on background thread
            val parents = FileManager.getParents(directory)

            getThenUpdateFlow(cached = {
                val directories = FileManager.goTo(directory)
                    .filter { it.isDirectory }
                    .sortedBy { it.name }
                    .map { Browsable(it) }
                val files = db.novelFilesDao().getForDirectory(directory.absolutePath)
                    .sortedBy { it.title }
                    .asFlow()
                    .map { novelFileInfo ->
                        Browsable(File(novelFileInfo.path), novelFileInfo)
                    }
                    .toList()
                BrowseResult(
                    browsables = ArrayList<Browsable>().apply {
                        addAll(directories)
                        addAll(files)
                    },
                    parents = parents
                )
            }, server = {
                val all = FileManager.goTo(directory)
                val directories = all
                    .filter { it.isDirectory }
                    .sortedBy { it.name }
                    .map { Browsable(it) }
                val files = all
                    .filter { !it.isDirectory }
                    .sortedBy { it.name }
                    .asFlow()
                    .map { selectedFile ->
                        val fileInfo = selectedFile.extractor.novelInfo
                        db.novelFilesDao().upsert(fileInfo)
                        Browsable(selectedFile, fileInfo)
                    }
                    .toList()
                BrowseResult(
                    browsables = ArrayList<Browsable>().apply {
                        addAll(directories)
                        addAll(files)
                    },
                    parents = parents
                )
            })
        } else {
            throw Exception("Missing File Access Permissions!")
        }
    }

    override suspend fun getParentDirectories(): List<File> = FileManager.getParents()
}