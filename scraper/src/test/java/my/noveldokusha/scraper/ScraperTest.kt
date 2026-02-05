package my.noveldokusha.scraper

import kotlinx.coroutines.test.runTest
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ScraperTest {

    @Mock
    private lateinit var mockNetworkClient: NetworkClient

    private lateinit var scraper: Scraper

    @Before
    fun setup() {
        val localSource = TestUtils.MockLocalSource()
        scraper = Scraper(mockNetworkClient, localSource)
    }

    @Test
    fun `scraper should have databases and sources initialized`() {
        // Assert
        assertTrue("Should have databases", scraper.databasesList.isNotEmpty())
        assertTrue("Should have sources", scraper.sourcesList.isNotEmpty())
        assertTrue("Should have catalog sources", scraper.sourcesCatalogsList.isNotEmpty())
        assertTrue("Should have language list", scraper.sourcesCatalogsLanguagesList.isNotEmpty())
    }

    @Test
    fun `getCompatibleSource should find matching source by URL`() {
        // Act
        val royalRoadSource = scraper.getCompatibleSource("https://www.royalroad.com/fiction/12345")
        val unknownSource = scraper.getCompatibleSource("https://unknown-site.com/book/123")

        // Assert
        assertNotNull("Should find RoyalRoad source", royalRoadSource)
        assertEquals("Should match RoyalRoad ID", "royal_road", royalRoadSource?.id)
        assertNull("Should not find unknown source", unknownSource)
    }

    @Test
    fun `getCompatibleSourceCatalog should find matching catalog source`() {
        // Act
        val catalogSource = scraper.getCompatibleSourceCatalog("https://www.royalroad.com/fiction/12345")

        // Assert
        assertNotNull("Should find catalog source", catalogSource)
        assertTrue("Should be catalog interface", catalogSource is SourceInterface.Catalog)
    }

    @Test
    fun `getCompatibleDatabase should find matching database by URL`() {
        // Act
        val novelUpdatesDb = scraper.getCompatibleDatabase("https://www.novelupdates.com/series/test/")
        val unknownDb = scraper.getCompatibleDatabase("https://unknown-database.com/book/123")

        // Assert
        assertNotNull("Should find NovelUpdates database", novelUpdatesDb)
        assertEquals("Should match NovelUpdates ID", "novel_updates", novelUpdatesDb?.id)
        assertNull("Should not find unknown database", unknownDb)
    }

    @Test
    fun `sources should have correct language mappings`() {
        // Act & Assert
        val englishSources = scraper.sourcesCatalogsList.filter { it.language == LanguageCode.ENGLISH }
        assertTrue("Should have English sources", englishSources.isNotEmpty())
        
        val languageSet = scraper.sourcesCatalogsLanguagesList
        assertTrue("Should include English", languageSet.contains(LanguageCode.ENGLISH))
    }

    @Test
    fun `all sources should have valid IDs and base URLs`() {
        // Act & Assert
        scraper.sourcesList.forEach { source ->
            assertNotNull("Source ID should not be null", source.id)
            assertFalse("Source ID should not be blank", source.id.isBlank())
            assertNotNull("Base URL should not be null", source.baseUrl)
            assertFalse("Base URL should not be blank", source.baseUrl.isBlank())
            assertTrue("Base URL should be valid", 
                source.baseUrl.startsWith("http://") || source.baseUrl.startsWith("https://"))
        }
    }

    @Test
    fun `all databases should have valid IDs and base URLs`() {
        // Act & Assert
        scraper.databasesList.forEach { database ->
            assertNotNull("Database ID should not be null", database.id)
            assertFalse("Database ID should not be blank", database.id.isBlank())
            assertNotNull("Base URL should not be null", database.baseUrl)
            assertFalse("Base URL should not be blank", database.baseUrl.isBlank())
            assertTrue("Base URL should be valid", 
                database.baseUrl.startsWith("http://") || database.baseUrl.startsWith("https://"))
        }
    }

    @Test
    fun `URL compatibility check should handle edge cases`() {
        // Act & Assert
        val sourceWithTrailingSlash = scraper.getCompatibleSource("https://www.royalroad.com/")
        val sourceWithoutTrailingSlash = scraper.getCompatibleSource("https://www.royalroad.com")

        assertNotNull("Should handle URL with trailing slash", sourceWithTrailingSlash)
        assertNotNull("Should handle URL without trailing slash", sourceWithoutTrailingSlash)
    }
}