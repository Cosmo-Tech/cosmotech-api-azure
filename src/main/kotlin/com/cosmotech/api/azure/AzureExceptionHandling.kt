// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.storage.blob.models.BlobStorageException
import com.cosmotech.api.exceptions.CsmExceptionHandling
import java.net.URI
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private const val HTTP_STATUS_CODE_CONFLICT = 409

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class AzureExceptionHandling : CsmExceptionHandling() {

  private val httpStatusCodeTypePrefix = "https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/"

  @ExceptionHandler
  fun handleBlobStorageException(exception: BlobStorageException): ProblemDetail {
    val status =
        when (exception.statusCode) {
          HTTP_STATUS_CODE_CONFLICT -> HttpStatus.CONFLICT
          else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

    val problemDetail = ProblemDetail.forStatus(status)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + status.value())

    if (exception.message != null) {
      problemDetail.detail = exception.message
    }
    return problemDetail
  }
}
