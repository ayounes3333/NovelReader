package my.noveldokusha.features.localexplorer.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import my.noveldokusha.core.utils.getThenUpdateFlow
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.localexplorer.NovelFileInfo
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
                        // Read right before writing so a favorite toggled mid-scan is kept.
                        fileInfo.isFavorite =
                            db.novelFilesDao().getNovelFileInfo(fileInfo.id)?.isFavorite ?: false
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

    suspend fun setFavorite(novelFileInfo: NovelFileInfo, isFavorite: Boolean) {
        db.novelFilesDao().setFavorite(novelFileInfo.id, isFavorite)
    }

    suspend fun deleteFile(novelFileInfo: NovelFileInfo): Boolean {
        val file = File(novelFileInfo.path)
        val deleted = !file.exists() || file.delete()
        if (deleted) db.novelFilesDao().deleteByPath(novelFileInfo.path)
        return deleted
    }
}