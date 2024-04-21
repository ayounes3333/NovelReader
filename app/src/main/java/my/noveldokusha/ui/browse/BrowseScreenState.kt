package my.noveldokusha.ui.browse

sealed class BrowseScreenState<T> {
    class Loading<T>(val data: T? = null): BrowseScreenState<T>()
    class NoData<T>(val message: String = "No Data!"): BrowseScreenState<T>()
    class Error<T>(val error: Throwable): BrowseScreenState<T>()
    class Data<T>(val data: T): BrowseScreenState<T>()
}