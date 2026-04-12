package my.noveldokusha.scraper.sources

import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R

/**
 * Novel main page example:
 * https://novelfull.com/reincarnated-as-a-slime.html
 */
class NovelFull(networkClient: NetworkClient) : AllNovel(networkClient) {
    override val id = "novel_full"
    override val nameStrId = R.string.source_name_novel_full
    override val baseUrl = "https://novelfull.com"
    override val catalogUrl = "$baseUrl/most-popular?page=1"
    override val ajaxChapterPath = "ajax-chapter-option"
    override val mainCatalogPath = "most-popular"
}
