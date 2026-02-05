package my.noveldokusha.features.localexplorer

import kotlinx.coroutines.flow.Flow
import my.noveldokusha.features.localexplorer.FileManager
import my.noveldokusha.features.localexplorer.model.BrowseResult
import java.io.File

interface BrowseModule {
    interface Repository {
        suspend fun browse(file: File? = FileManager.getCurrentDirectory()) : Flow<BrowseResult?>
        suspend fun getParentDirectories(): List<File>
    }
}