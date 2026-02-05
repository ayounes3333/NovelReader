package my.noveldokusha.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.aliyounes.aurui.components.AurToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import my.noveldoksuha.coreui.components.CollapsibleDivider
import my.noveldokusha.settings.viewmodels.ScraperTestingViewModel
import my.noveldokusha.tooling.backup_create.onBackupCreate
import my.noveldokusha.tooling.backup_restore.onBackupRestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = viewModel()
    val scraperTestingViewModel: ScraperTestingViewModel = hiltViewModel()
    val sourceResults by scraperTestingViewModel.sourceResults.collectAsState()
    val databaseResults by scraperTestingViewModel.databaseResults.collectAsState()
    val isTestingInProgress by scraperTestingViewModel.isTestingInProgress.collectAsState()

    Scaffold(
        topBar = {
            Column {
                AurToolbar(
                    title = stringResource(id = R.string.title_settings)
                )
            }
        },
        content = { innerPadding ->
            SettingsScreenBody(
                state = viewModel.state,
                onFollowSystem = viewModel::onFollowSystemChange,
                onThemeSelected = viewModel::onThemeChange,
                onCleanDatabase = viewModel::cleanDatabase,
                onCleanImageFolder = viewModel::cleanImagesFolder,
                onBackupData = onBackupCreate(),
                onRestoreData = onBackupRestore(),
                onDownloadTranslationModel = viewModel::downloadTranslationModel,
                onRemoveTranslationModel = viewModel::removeTranslationModel,
                onCheckForUpdatesManual = viewModel::onCheckForUpdatesManual,
                scraperTestingSources = sourceResults,
                scraperTestingDatabases = databaseResults,
                scraperTestingInProgress = isTestingInProgress,
                onTestSources = scraperTestingViewModel::testSources,
                onTestDatabases = scraperTestingViewModel::testDatabases,
                onTestAllScrapers = scraperTestingViewModel::testAll,
                onTestIndividualSource = scraperTestingViewModel::testIndividualSource,
                onTestIndividualDatabase = scraperTestingViewModel::testIndividualDatabase,
                modifier = Modifier.padding(innerPadding),
            )
        }
    )
}

