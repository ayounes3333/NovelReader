# Scraper Module Testing Solution

This document describes the comprehensive testing solution implemented for the NovelReader scraper module.

## Overview

The scraper module uses Jsoup to scrape websites for novels and loads them into the database. This testing solution provides both automated unit tests and an interactive testing interface within the app.

## Testing Components

### 1. Unit Tests (`/scraper/src/test/`)

#### Test Utilities (`TestUtils.kt`)
- **Mock Data Generation**: Creates sample book results, chapter results, and database book data
- **Mock HTML Generation**: Provides realistic HTML for testing scrapers
- **Mock Implementations**: 
  - `MockSourceCatalog`: Simulates a source website
  - `MockDatabase`: Simulates a novel database
  - `MockLocalSource`: Simulates local source functionality

#### Database Tests (`/databases/`)
- **NovelUpdatesTest.kt**: Tests the NovelUpdates database scraper
  - Catalog retrieval and pagination
  - Search functionality (by title and filters)
  - Book data parsing
  - Author data extraction
  - Error handling and network failures

#### Source Tests (`/sources/`)
- **RoyalRoadTest.kt**: Tests the RoyalRoad source scraper
  - Chapter title and content extraction
  - Catalog listing with pagination
  - Chapter list retrieval
  - Book cover and description extraction
  - Search functionality

#### Core Tests
- **ScraperTest.kt**: Tests the main Scraper class
  - Source and database initialization
  - URL compatibility checking
  - Language mapping validation
  - Source/database discovery

- **TextExtractorTest.kt**: Tests HTML text extraction
  - Simple and nested HTML handling
  - Special character decoding
  - Empty element handling
  - Line break preservation

- **IntegrationTest.kt**: End-to-end workflow tests
  - Complete scraper workflow simulation
  - Error condition handling
  - HTML parsing resilience

#### Network Tests (`/network/`)
- **NetworkResilienceTest.kt**: Tests network layer robustness
  - Timeout handling
  - HTTP error code responses
  - Malformed HTML parsing
  - Large response handling
  - Concurrent request management
  - Character encoding issues

### 2. Interactive Testing Interface

#### Settings Screen Integration
A new "Scraper Testing" section has been added to the Settings screen that allows users to:

- **Test Sources**: Check all configured novel sources for connectivity and functionality
- **Test Databases**: Verify database scrapers are working correctly  
- **Test All**: Run comprehensive tests on both sources and databases

#### Features
- **Real-time Testing**: Tests run asynchronously with progress indicators
- **Detailed Results**: Shows success/failure status, response times, and error details
- **Summary Statistics**: Displays total, successful, and failed test counts
- **Visual Indicators**: Color-coded status icons for easy result interpretation

#### Implementation Files
- `ScraperTestingSection.kt`: UI component for the testing interface
- `ScraperTestingViewModel.kt`: ViewModel handling test execution and state management
- Updated `SettingsScreen.kt` and `SettingsScreenBody.kt` for integration

## Test Execution

### Running Unit Tests
```bash
# Run all scraper tests
./gradlew :scraper:test

# Run specific test class
./gradlew :scraper:test --tests "*NovelUpdatesTest*"

# Run tests with coverage
./gradlew :scraper:jacocoTestReport
```

### Using Interactive Testing
1. Open the app and navigate to Settings
2. Scroll to the "Scraper Testing" section
3. Choose from:
   - "Test Sources" - Tests all novel source websites
   - "Test DBs" - Tests all database scrapers
   - "Test All" - Comprehensive testing of both
4. View results with detailed status and performance metrics

## Test Coverage

The testing solution covers:

### Functional Areas
- ✅ **HTTP Network Requests**: Connection handling, timeouts, error responses
- ✅ **HTML Parsing**: Jsoup document processing, malformed HTML handling
- ✅ **Data Extraction**: Text extraction, metadata parsing, URL resolution
- ✅ **Search Functionality**: Title search, filter-based search, pagination
- ✅ **Content Retrieval**: Book catalogs, chapter lists, book details
- ✅ **Error Handling**: Network failures, parsing errors, missing data

### Test Types
- **Unit Tests**: Individual component testing with mocks
- **Integration Tests**: End-to-end workflow verification
- **UI Tests**: Interactive testing interface
- **Network Tests**: Connection and parsing resilience
- **Performance Tests**: Response time measurement

## Benefits

### For Developers
- **Automated Regression Testing**: Catch breaking changes early
- **Mock-based Testing**: Test without depending on external websites
- **Comprehensive Coverage**: All major scraper functionality tested
- **Easy Test Maintenance**: Centralized test utilities and patterns

### For Users
- **Real-time Diagnostics**: Check if sources/databases are working
- **Performance Monitoring**: See response times for different sources
- **Troubleshooting**: Identify which sources are having issues
- **Reliability Assurance**: Confidence that scrapers are functioning

## Adding New Tests

### For New Sources
1. Create test class in `/scraper/src/test/java/my/noveldokusha/scraper/sources/`
2. Use `TestUtils` for mock data generation
3. Test core functionality: catalog, search, chapters, content extraction
4. Handle error cases and edge conditions

### For New Databases
1. Create test class in `/scraper/src/test/java/my/noveldokusha/scraper/databases/`
2. Test catalog, search, filters, book data, author data
3. Verify pagination and error handling
4. Use MockWebServer for network simulation

### Mock HTML Creation
- Use `TestUtils.createMockCatalogHtml()` patterns
- Include realistic HTML structure matching target site
- Test both valid and malformed HTML scenarios

## Future Enhancements

### Planned Improvements
- **Test Scheduling**: Automatic periodic testing
- **Performance Benchmarking**: Historical response time tracking
- **Test Report Export**: Save detailed test results
- **Custom Test Configurations**: User-defined test parameters
- **Notification System**: Alert users to failing sources

### Integration Possibilities
- **CI/CD Pipeline**: Automated testing in build process
- **Monitoring Dashboard**: Real-time source health monitoring  
- **Analytics Integration**: Track source reliability over time
- **User Feedback**: Report issues directly from test results

This comprehensive testing solution ensures the reliability and maintainability of the scraper module while providing valuable diagnostic tools for both developers and end users.