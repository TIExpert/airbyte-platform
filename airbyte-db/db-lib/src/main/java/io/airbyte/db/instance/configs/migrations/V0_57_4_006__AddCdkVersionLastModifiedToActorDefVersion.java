/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.db.instance.configs.migrations;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V0_57_4_006__AddCdkVersionLastModifiedToActorDefVersion extends BaseJavaMigration {

  private static final Logger LOGGER = LoggerFactory.getLogger(V0_57_4_006__AddCdkVersionLastModifiedToActorDefVersion.class);

  static void addCdkVersionToActorDefinitionVersion(final DSLContext ctx) {
    ctx.alterTable("actor_definition_version")
        .addColumnIfNotExists(DSL.field("cdk_version", SQLDataType.VARCHAR(256).nullable(true)))
        .execute();
  }

  static void addLastPublishedToActorDefinitionVersion(final DSLContext ctx) {
    ctx.alterTable("actor_definition_version")
        .addColumnIfNotExists(DSL.field("last_published", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(true)))
        .execute();
  }

  static void addMetricsToActorDefinition(final DSLContext ctx) {
    ctx.alterTable("actor_definition")
        .addColumnIfNotExists(DSL.field("metrics", SQLDataType.JSONB.nullable(true)))
        .execute();
  }

  @Override
  public void migrate(final Context context) throws Exception {
    LOGGER.info("Running migration: {}", this.getClass().getSimpleName());

    // Warning: please do not use any jOOQ generated code to write a migration.
    // As database schema changes, the generated jOOQ code can be deprecated. So
    // old migration may not compile if there is any generated code.
    final DSLContext ctx = DSL.using(context.getConnection());

    addCdkVersionToActorDefinitionVersion(ctx);
    addLastPublishedToActorDefinitionVersion(ctx);
    addMetricsToActorDefinition(ctx);
  }

}
