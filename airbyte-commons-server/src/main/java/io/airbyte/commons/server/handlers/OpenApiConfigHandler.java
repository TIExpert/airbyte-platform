/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.server.handlers;

import io.airbyte.commons.resources.MoreResources;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * OpenApiConfigHandler. Javadocs suppressed because api docs should be used as source of truth.
 */
@Singleton
public class OpenApiConfigHandler {

  private static final File TMP_FILE;

  static {
    try {
      TMP_FILE = File.createTempFile("airbyte", "openapiconfig");
      TMP_FILE.deleteOnExit();
      Files.writeString(TMP_FILE.toPath(), MoreResources.readResource("config.yaml"));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public File getFile() {
    return TMP_FILE;
  }

}
