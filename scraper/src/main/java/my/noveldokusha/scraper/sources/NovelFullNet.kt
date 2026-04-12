package my.noveldokusha.scraper.sources

import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R

/**
 * Novel main page example:
 * https://novelfull.net/reincarnated-as-a-slime.html
 *
 * Contains novels not present on novelfull.com.
 */
class NovelFullNet(networkClient: NetworkClient) : AllNovel(networkClient) {
    override val id = "novel_full_net"
    override val nameStrId = R.string.source_name_novel_full_net
    override val baseUrl = "https://novelfull.net"
    override val catalogUrl = "$baseUrl/hot-novel?page=1"
    override val mainCatalogPath = "hot-novel"
    override val ajaxChapterPath = "ajax-chapter-option"
}
