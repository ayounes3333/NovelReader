package my.noveldokusha.scraper

import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.databases.BakaUpdates
import my.noveldokusha.scraper.databases.NovelUpdates
import my.noveldokusha.scraper.sources.AT
import my.noveldokusha.scraper.sources.AllNovel
import my.noveldokusha.scraper.sources.BacaLightnovel
import my.noveldokusha.scraper.sources.BestLightNovel
import my.noveldokusha.scraper.sources.BoxNovel
import my.noveldokusha.scraper.sources.FreeWebNovel
import my.noveldokusha.scraper.sources.HiraethTranslation
import my.noveldokusha.scraper.sources.IndoWebnovel
import my.noveldokusha.scraper.sources.KolNovel
import my.noveldokusha.scraper.sources.KoreanNovelsMTL
import my.noveldokusha.scraper.sources.LibRead
import my.noveldokusha.scraper.sources.LightNovelWorld
import my.noveldokusha.scraper.sources.LightNovelsTranslations
import my.noveldokusha.scraper.sources.LocalSource
import my.noveldokusha.scraper.sources.MTLNovel
import my.noveldokusha.scraper.sources.MeioNovel
import my.noveldokusha.scraper.sources.MoreNovel
import my.noveldokusha.scraper.sources.MtlNovelsMe
import my.noveldokusha.scraper.sources.NovelBin
import my.noveldokusha.scraper.sources.NovelFull
import my.noveldokusha.scraper.sources.NovelFullNet
import my.noveldokusha.scraper.sources.NovelHall
import my.noveldokusha.scraper.sources.Novelku
import my.noveldokusha.scraper.sources.NovLove
import my.noveldokusha.scraper.sources.NovelsOnline
import my.noveldokusha.scraper.sources.PawRead
import my.noveldokusha.scraper.sources.ReadLightNovel
import my.noveldokusha.scraper.sources.ReadNovelFull
import my.noveldokusha.scraper.sources.Reddit
import my.noveldokusha.scraper.sources.RoyalRoad
import my.noveldokusha.scraper.sources.Saikai
import my.noveldokusha.scraper.sources.SakuraNovel
import my.noveldokusha.scraper.sources.Scribblehub
import my.noveldokusha.scraper.sources.Sousetsuka
import my.noveldokusha.scraper.sources.WbNovel
import my.noveldokusha.scraper.sources.Wuxia
import my.noveldokusha.scraper.sources.WuxiaWorld
import my.noveldokusha.scraper.sources._1stKissNovel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Scraper @Inject constructor(
    networkClient: NetworkClient,
    localSource: LocalSource
) {
    val databasesList = setOf(
        NovelUpdates(networkClient),
        BakaUpdates(networkClient)
    )

    val sourcesList = setOf(
        localSource,
        LightNovelsTranslations(networkClient),
        ReadLightNovel(networkClient),
        ReadNovelFull(networkClient),
        RoyalRoad(networkClient),
        my.noveldokusha.scraper.sources.NovelUpdates(networkClient),
        Reddit(),
        AT(),
        Wuxia(networkClient),
        BestLightNovel(networkClient),
        _1stKissNovel(networkClient),
        Sousetsuka(),
        Saikai(networkClient),
        BoxNovel(networkClient),
        LightNovelWorld(networkClient),
        NovelHall(networkClient),
        MTLNovel(networkClient),
        WuxiaWorld(networkClient),
        KoreanNovelsMTL(networkClient),
        IndoWebnovel(networkClient),
        BacaLightnovel(networkClient),
        SakuraNovel(networkClient),
        MeioNovel(networkClient),
        MoreNovel(networkClient),
        Novelku(networkClient),
        WbNovel(networkClient),
        NovelBin(networkClient),
        // Sources imported from QuickNovel
        AllNovel(networkClient),
        NovelFull(networkClient),
        NovelFullNet(networkClient),
        LibRead(networkClient),
        FreeWebNovel(networkClient),
        MtlNovelsMe(networkClient),
        Scribblehub(networkClient),
        KolNovel(networkClient),
        PawRead(networkClient),
        NovLove(networkClient),
        NovelsOnline(networkClient),
        HiraethTranslation(networkClient),
    )

    val sourcesCatalogsList = sourcesList.filterIsInstance<SourceInterface.Catalog>()
    val sourcesCatalogsLanguagesList = sourcesCatalogsList.mapNotNull { it.language }.toSet()

    private fun String.isCompatibleWithBaseUrl(baseUrl: String): Boolean {
        val normalizedUrl = if (this.endsWith("/")) this else "$this/"
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return normalizedUrl.startsWith(normalizedBaseUrl)
    }

    fun getCompatibleSource(url: String): SourceInterface? =
        sourcesList.find { url.isCompatibleWithBaseUrl(it.baseUrl) }

    fun getCompatibleSourceCatalog(url: String): SourceInterface.Catalog? =
        sourcesCatalogsList.find { url.isCompatibleWithBaseUrl(it.baseUrl) }

    fun getCompatibleDatabase(url: String): DatabaseInterface? =
        databasesList.find { url.isCompatibleWithBaseUrl(it.baseUrl) }
}
