package my.noveldokusha.ui.screens.databaseBookInfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.data.BookMetadata
import my.noveldokusha.scraper.DatabaseInterface
import my.noveldokusha.scraper.SearchGenre
import my.noveldokusha.ui.BaseActivity
import my.noveldokusha.ui.goToDatabaseBookInfo
import my.noveldokusha.ui.goToDatabaseSearchGenres
import my.noveldokusha.ui.goToGlobalSearch
import my.noveldokusha.ui.goToWebBrowser
import my.noveldokusha.ui.theme.Theme
import my.noveldokusha.utils.Extra_String

@AndroidEntryPoint
class DatabaseBookInfoActivity : BaseActivity() {
    class IntentData : Intent, DatabaseBookInfoStateBundle {
        override var databaseUrlBase by Extra_String()
        override var bookUrl by Extra_String()
        override var bookTitle by Extra_String()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, databaseUrlBase: String, bookMetadata: BookMetadata) : super(
            ctx,
            DatabaseBookInfoActivity::class.java
        ) {
            this.databaseUrlBase = databaseUrlBase
            this.bookUrl = bookMetadata.url
            this.bookTitle = bookMetadata.title
        }
    }

    private val viewModel by viewModels<DatabaseBookInfoViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Theme(appPreferences = appPreferences) {
                DatabaseBookInfoScreen(
                    state = viewModel.state,
                    onSourcesClick = ::openGlobalSearchPage,
                    onAuthorsClick = ::openSearchPageByAuthor,
                    onGenresClick = ::openSearchPageByGenres,
                    onBookClick = ::openBookInfo,
                    onOpenInWeb = { goToWebBrowser(viewModel.bookUrl) },
                    onPressBack = ::onBackPressed
                )
            }
        }
    }

    private fun openGlobalSearchPage() = goToGlobalSearch(text = viewModel.state.book.value.title)

    private fun openSearchPageByAuthor(author: DatabaseInterface.AuthorMetadata) {
        // TODO
    }

    private fun openSearchPageByGenres(genres: List<SearchGenre>) = goToDatabaseSearchGenres(
        includedGenresIds = genres.map { it.id },
        databaseUrlBase = viewModel.database.baseUrl
    )

    private fun openBookInfo(book: BookMetadata) = goToDatabaseBookInfo(
        bookMetadata = book,
        databaseUrlBase = viewModel.database.baseUrl
    )
}
