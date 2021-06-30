package com.hungknow.pdfsdk.exceptions

class PageRenderingException(val page: Int, cause: Throwable): Exception(cause) {
}