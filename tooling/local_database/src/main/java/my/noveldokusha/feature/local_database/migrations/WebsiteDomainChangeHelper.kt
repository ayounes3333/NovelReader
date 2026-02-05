package my.noveldokusha.feature.local_database.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("UnusedReceiverParameter")
internal fun MigrationsList.websiteDomainChangeHelper(
    it: SupportSQLiteDatabase,
    oldDomain: String,
    newDomain: String,
) {
    // readlightnovel source changed its domain to "newDomain"
    fun replace(columnName: String) =
        """$columnName = REPLACE($columnName, "$oldDomain", "$newDomain")"""

    fun like(columnName: String) =
        """($columnName LIKE "%$oldDomain%")"""

    // For Chapter table, we need to handle primary key conflicts
    // First, delete any existing chapters that would conflict with the new URLs
    it.execSQL(
        """
            DELETE FROM Chapter 
            WHERE url IN (
                SELECT REPLACE(url, "$oldDomain", "$newDomain") 
                FROM Chapter 
                WHERE ${like("url")} OR ${like("bookUrl")}
            ) AND NOT (${like("url")} OR ${like("bookUrl")})
        """.trimIndent()
    )

    // For ChapterBody table, delete conflicting entries as well
    it.execSQL(
        """
            DELETE FROM ChapterBody 
            WHERE url IN (
                SELECT REPLACE(url, "$oldDomain", "$newDomain") 
                FROM ChapterBody 
                WHERE ${like("url")}
            ) AND NOT ${like("url")}
        """.trimIndent()
    )

    it.execSQL(
        """
            UPDATE Book
            SET ${replace("url")},
                ${replace("coverImageUrl")}
            WHERE
                ${like("url")};
        """.trimIndent()
    )
    it.execSQL(
        """
            UPDATE Chapter
            SET ${replace("url")},
                ${replace("bookUrl")}
            WHERE
                ${like("url")} OR ${like("bookUrl")};
        """.trimIndent()
    )
    it.execSQL(
        """
            UPDATE ChapterBody
            SET ${replace("url")}
            WHERE
                ${like("url")};
        """.trimIndent()
    )
}
