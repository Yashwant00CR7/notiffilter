package com.yk.notiffilter

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yk.notiffilter.data.NotifFilterDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v1 → v2 migration is hand-written and moves rows that cannot be regenerated:
 * under the on-device model a USER-pinned verdict is training data, not a cache
 * entry. Losing one costs the user work they did by hand, so the migration is tested
 * against a real on-disk v1 schema rather than verified by reading it.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotifFilterDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesUserLabelsAndQueuesModelRows() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO app_rules (packageName, appLabel, prose, enabled, repostKeepers, createdAt)
                VALUES ('in.swiggy.android', 'Swiggy', 'keep orders, drop coupons', 1, 1, 100)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO template_verdicts
                    (fingerprint, packageName, verdict, reason, source, sampleTitle, sampleText, updatedAt)
                VALUES ('fp-user', 'in.swiggy.android', 'HIDE', 'you said so', 'USER', 'FLAT 50%', 'off', 200)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO template_verdicts
                    (fingerprint, packageName, verdict, reason, source, sampleTitle, sampleText, updatedAt)
                VALUES ('fp-model', 'in.swiggy.android', 'KEEP', 'groq said so', 'MODEL', 'Delivered', 'enjoy', 300)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            DB, 2, true, NotifFilterDatabase.MIGRATION_1_2,
        )

        // Apps that were already filtering must keep filtering: dropping them back to
        // OBSERVE would silently stop a setup the user believes is finished.
        db.query("SELECT mode FROM app_rules WHERE packageName = 'in.swiggy.android'").use {
            assertTrue(it.moveToFirst())
            assertEquals("ACTIVE", it.getString(0))
        }

        // The user's own label survives untouched and keeps training the model.
        db.query("SELECT needsVerdict FROM template_verdicts WHERE fingerprint = 'fp-user'").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }

        // Groq's guess still filters, but is queued for confirmation and trains nothing.
        db.query("SELECT needsVerdict FROM template_verdicts WHERE fingerprint = 'fp-model'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
