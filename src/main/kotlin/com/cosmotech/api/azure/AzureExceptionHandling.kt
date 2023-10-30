// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.storage.blob.models.BlobStorageException
import com.cosmotech.api.exceptions.CsmExceptionHandling
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

  @ExceptionHandler
  fun handleBlobStorageException(exception: BlobStorageException): ProblemDetail {
    val status =
        when (exception.statusCode) {
          HTTP_STATUS_CODE_CONFLICT -> HttpStatus.CONFLICT
          else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
    if (exception.message == null) {
      return ProblemDetail.forStatus(status)
    }
    return ProblemDetail.forStatusAndDetail(status, exception.message!!)
  }
}
