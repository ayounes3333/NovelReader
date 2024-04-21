package my.noveldokusha.ui.browse

import kotlinx.coroutines.flow.Flow
import my.noveldokusha.ui.browse.FileManager
import my.noveldokusha.ui.browse.model.Browsable
import java.io.File

interface BrowseModule {
    interface Repository {
        suspend fun browse(file: File? = FileManager.getCurrentDirectory()) : Flow<List<Browsable>?>
        suspend fun getParentDirectories(): List<File>
    }
}