package me.weishu.kernelsu.data.repository

import java.io.IOException

class ReleaseHttpException(val statusCode: Int, val rateLimited: Boolean, val retryAfterSeconds: Long?) :
    IOException("Release HTTP $statusCode; rate limited: $rateLimited; retry after seconds: $retryAfterSeconds")
