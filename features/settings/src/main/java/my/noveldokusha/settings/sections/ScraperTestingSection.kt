package my.noveldokusha.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.noveldokusha.settings.R
import my.noveldoksuha.coreui.theme.AppElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScraperTestingSection(
    sources: List<ScraperTestResult>,
    databases: List<ScraperTestResult>,
    isTestingInProgress: Boolean,
    onTestSources: () -> Unit,
    onTestDatabases: () -> Unit,
    onTestAll: () -> Unit,
    onTestIndividualSource: (String) -> Unit = {},
    onTestIndividualDatabase: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.scraper_testing_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Testing Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTestSources,
                enabled = !isTestingInProgress,
                modifier = Modifier.weight(1f)
            ) {
                if (isTestingInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Sources")
            }

            Button(
                onClick = onTestDatabases,
                enabled = !isTestingInProgress,
                modifier = Modifier.weight(1f)
            ) {
                if (isTestingInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test DBs")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onTestAll,
            enabled = !isTestingInProgress,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Test All Sources & Databases")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results Section
        if (sources.isNotEmpty() || databases.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.card)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Test Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Summary Statistics
                    TestSummary(sources = sources, databases = databases)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sources Results
                    if (sources.isNotEmpty()) {
                        Text(
                            text = "Sources (${sources.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        sources.forEach { result ->
                            DetailedTestResultItem(
                                result = result,
                                onTestIndividual = { onTestIndividualSource(result.id) },
                                isTestingInProgress = isTestingInProgress
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Databases Results
                    if (databases.isNotEmpty()) {
                        Text(
                            text = "Databases (${databases.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        databases.forEach { result ->
                            DetailedTestResultItem(
                                result = result,
                                onTestIndividual = { onTestIndividualDatabase(result.id) },
                                isTestingInProgress = isTestingInProgress
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestSummary(
    sources: List<ScraperTestResult>,
    databases: List<ScraperTestResult>
) {
    val allResults = sources + databases
    val successCount = allResults.count { it.status == TestStatus.SUCCESS }
    val errorCount = allResults.count { it.status == TestStatus.ERROR }
    val totalCount = allResults.size

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryItem(
            count = totalCount,
            label = "Total",
            color = MaterialTheme.colorScheme.onSurface
        )
        SummaryItem(
            count = successCount,
            label = "Success",
            color = Color(0xFF4CAF50)
        )
        SummaryItem(
            count = errorCount,
            label = "Failed",
            color = Color(0xFFF44336)
        )
    }
}

@Composable
private fun SummaryItem(
    count: Int,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailedTestResultItem(
    result: ScraperTestResult,
    onTestIndividual: () -> Unit,
    isTestingInProgress: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.card),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header with name and test button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (result.status) {
                            TestStatus.SUCCESS -> Icons.Default.CheckCircle
                            TestStatus.ERROR -> Icons.Default.Error
                            TestStatus.TESTING -> Icons.Default.Refresh
                            TestStatus.NOT_TESTED -> Icons.Default.Help
                        },
                        contentDescription = null,
                        tint = when (result.status) {
                            TestStatus.SUCCESS -> Color(0xFF4CAF50)
                            TestStatus.ERROR -> Color(0xFFF44336)
                            TestStatus.TESTING -> MaterialTheme.colorScheme.primary
                            TestStatus.NOT_TESTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = result.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = result.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Individual test button
                IconButton(
                    onClick = onTestIndividual,
                    enabled = !isTestingInProgress
                ) {
                    Icon(
                        imageVector = if (result.status == TestStatus.TESTING) 
                            Icons.Default.HourglassEmpty else Icons.Default.PlayArrow,
                        contentDescription = "Test this source",
                        tint = if (result.status == TestStatus.TESTING) 
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Details section
            if (result.testDetails.isNotEmpty() || result.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                
                // Test details
                result.testDetails.forEach { detail ->
                    DetailRow(
                        label = detail.operation,
                        status = detail.status,
                        message = detail.message,
                        responseTime = detail.responseTime
                    )
                }
                
                // Error information
                result.error?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = "Error Details:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF44336)
                            )
                            Text(
                                text = error.take(200) + if (error.length > 200) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            // Response time and overall status
            if (result.responseTime > 0 || result.status != TestStatus.NOT_TESTED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (result.responseTime > 0) {
                        Text(
                            text = "Total: ${result.responseTime}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = when (result.status) {
                            TestStatus.SUCCESS -> "✓ All tests passed"
                            TestStatus.ERROR -> "✗ Tests failed"
                            TestStatus.TESTING -> "⏳ Testing..."
                            TestStatus.NOT_TESTED -> "Not tested"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (result.status) {
                            TestStatus.SUCCESS -> Color(0xFF4CAF50)
                            TestStatus.ERROR -> Color(0xFFF44336)
                            TestStatus.TESTING -> MaterialTheme.colorScheme.primary
                            TestStatus.NOT_TESTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    status: TestStatus,
    message: String,
    responseTime: Long = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (status) {
                TestStatus.SUCCESS -> Icons.Default.CheckCircle
                TestStatus.ERROR -> Icons.Default.Cancel
                TestStatus.TESTING -> Icons.Default.HourglassEmpty
                TestStatus.NOT_TESTED -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = when (status) {
                TestStatus.SUCCESS -> Color(0xFF4CAF50)
                TestStatus.ERROR -> Color(0xFFF44336)
                TestStatus.TESTING -> MaterialTheme.colorScheme.primary
                TestStatus.NOT_TESTED -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (responseTime > 0) {
            Text(
                text = "${responseTime}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class ScraperTestResult(
    val name: String,
    val id: String,
    val baseUrl: String,
    val status: TestStatus,
    val details: String = "",
    val responseTime: Long = 0L,
    val error: String? = null,
    val testDetails: List<TestDetail> = emptyList()
)

data class TestDetail(
    val operation: String,
    val status: TestStatus,
    val message: String = "",
    val responseTime: Long = 0L
)

enum class TestStatus {
    SUCCESS,
    ERROR,
    TESTING,
    NOT_TESTED
}