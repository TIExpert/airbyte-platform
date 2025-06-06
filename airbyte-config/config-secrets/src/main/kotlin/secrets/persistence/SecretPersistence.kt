/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.secrets.persistence

import io.airbyte.config.secrets.SecretCoordinate
import io.airbyte.config.secrets.SecretCoordinate.AirbyteManagedSecretCoordinate
import java.time.Instant

/**
 * Provides a read-only interface to a backing secrets store similar to [SecretPersistence].
 * In practice, the functionality should be provided by a [SecretPersistence.read] function.
 */
fun interface ReadOnlySecretPersistence {
  fun read(coordinate: SecretCoordinate): String
}

/**
 * Provides the ability to read and write secrets to a backing store. Assumes that secret payloads
 * are always strings. See {@link SecretCoordinate} for more information on how secrets are
 * identified. Note that write/delete operations only operate on [AirbyteManagedSecretCoordinate]s,
 * because external secret coordinates should never be written to or updated by Airbyte code.
 */
interface SecretPersistence : ReadOnlySecretPersistence {
  /**
   * Performs any initialization prior to utilization of the persistence object. This exists to make
   * it possible to create instances within a dependency management framework, where any
   * initialization logic should not be present in a constructor.
   *
   * @throws Exception if unable to perform the initialization.
   */
  @Throws(Exception::class)
  fun initialize() {}

  override fun read(coordinate: SecretCoordinate): String

  fun write(
    coordinate: AirbyteManagedSecretCoordinate,
    payload: String,
  )

  fun writeWithExpiry(
    coordinate: AirbyteManagedSecretCoordinate,
    payload: String,
    expiry: Instant? = null,
  ) {
    // Default implementation does not support expiry.
    write(coordinate, payload)
  }

  fun delete(coordinate: AirbyteManagedSecretCoordinate)

  fun disable(coordinate: AirbyteManagedSecretCoordinate) {
    println("secret persistence has not implemented disable.")
  }
}
