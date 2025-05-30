/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.db.instance.development

import io.airbyte.db.instance.DatabaseMigrator
import io.airbyte.db.instance.FlywayDatabaseMigrator
import io.airbyte.db.instance.development.MigrationDevHelper.getSecondToLastMigrationVersion
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfo
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.BaselineResult
import org.flywaydb.core.api.output.MigrateResult
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This migrator can prepare the database with previous migrations, and only run the latest
 * migration for testing. It is used in [MigrationDevHelper.runLastMigration].
 */
class DevDatabaseMigrator : DatabaseMigrator {
  // A migrator that can run all migrations.
  private val fullMigrator: DatabaseMigrator

  // A migrator that will not run the last migration. It prepares the database to the state right
  // before the last migration.
  private val baselineMigrator: DatabaseMigrator

  constructor(fullMigrator: FlywayDatabaseMigrator) {
    this.fullMigrator = fullMigrator
    this.baselineMigrator = getBaselineMigrator(fullMigrator)
  }

  constructor(fullMigrator: FlywayDatabaseMigrator, baselineVersion: MigrationVersion) {
    this.fullMigrator = fullMigrator
    this.baselineMigrator = getBaselineMigratorForVersion(fullMigrator, baselineVersion)
  }

  private class NoOpDatabaseMigrator : DatabaseMigrator {
    override fun migrate(): MigrateResult? = null

    override fun list(): List<MigrationInfo> = emptyList()

    override fun getLatestMigration(): MigrationInfo? = null

    override fun createBaseline(): BaselineResult? = null

    override fun dumpSchema(): String = ""
  }

  override fun migrate(): MigrateResult? = fullMigrator.migrate()

  override fun list(): List<MigrationInfo>? = fullMigrator.list()

  override fun getLatestMigration(): MigrationInfo? = fullMigrator.getLatestMigration()

  override fun createBaseline(): BaselineResult? {
    fullMigrator.createBaseline()
    // Run all previous migration except for the last one to establish the baseline database state.
    baselineMigrator.migrate()
    return fullMigrator.createBaseline()
  }

  override fun dumpSchema(): String = fullMigrator.dumpSchema()

  companion object {
    private val LOGGER: Logger = LoggerFactory.getLogger(DevDatabaseMigrator::class.java)

    private fun getBaselineConfig(fullMigrator: FlywayDatabaseMigrator): FluentConfiguration {
      val fullConfig = fullMigrator.flyway.configuration

      val flywayConfiguration =
        Flyway
          .configure()
          .dataSource(fullConfig.dataSource)
          .baselineVersion(fullConfig.baselineVersion)
          .baselineDescription(fullConfig.baselineDescription)
          .baselineOnMigrate(fullConfig.isBaselineOnMigrate)
          .installedBy(fullConfig.installedBy)
          .table(fullConfig.table)
          .locations(*fullConfig.locations)

      // Setting the transactional lock to false allows us run queries outside transactions
      // without hanging. This enables creating indexes concurrently (i.e. without locking tables)
      flywayConfiguration.pluginRegister
        .getPlugin(PostgreSQLConfigurationExtension::class.java)
        .isTransactionalLock =
        false

      return flywayConfiguration
    }

    /**
     * Create a baseline migration from a full migrator. The baseline migrator does not run the last
     * migration, which will be usually the migration to be tested.
     */
    private fun getBaselineMigrator(fullMigrator: FlywayDatabaseMigrator): DatabaseMigrator {
      val baselineConfig = getBaselineConfig(fullMigrator)

      val secondToLastMigrationVersion = getSecondToLastMigrationVersion(fullMigrator)
      if (secondToLastMigrationVersion.isEmpty) {
        LOGGER.info("There is zero or one migration. No extra baseline setup is needed.")
        return NoOpDatabaseMigrator()
      }

      // Set the baseline flyway config to not run the last migration by setting the target migration
      // version.
      LOGGER.info("Baseline migrator target version: ${secondToLastMigrationVersion.get()}")
      baselineConfig.target(secondToLastMigrationVersion.get())

      return FlywayDatabaseMigrator(fullMigrator.database, baselineConfig.load())
    }

    /**
     * Create a baseline migration from a specified target migration version.
     */
    private fun getBaselineMigratorForVersion(
      fullMigrator: FlywayDatabaseMigrator,
      migrationVersion: MigrationVersion,
    ): DatabaseMigrator {
      val baselineConfig = getBaselineConfig(fullMigrator)

      // Set the baseline flyway config to run up to a target migration version.
      LOGGER.info("Baseline migrator target version: $migrationVersion")
      baselineConfig.target(migrationVersion)

      return FlywayDatabaseMigrator(fullMigrator.database, baselineConfig.load())
    }
  }
}
