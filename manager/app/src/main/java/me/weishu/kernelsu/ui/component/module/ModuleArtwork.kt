package me.weishu.kernelsu.ui.component.module

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp
import okhttp3.Request

@Composable
internal fun ModuleArtwork(url: String, description: String?, modifier: Modifier = Modifier, screenshot: Boolean = false) {
    var retry by remember(url) { mutableIntStateOf(0) }
    val image by produceState<Result<ImageBitmap>?>(null, url, retry, screenshot) {
        value = null
        if (url.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        check(response.isSuccessful)
                        val bytes = response.body.byteStream().use { it.readNBytes(8 * 1024 * 1024 + 1) }
                        check(bytes.size <= 8 * 1024 * 1024)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        check(bounds.outWidth > 0 && bounds.outHeight > 0)
                        val options = BitmapFactory.Options().apply {
                            val maximum = if (screenshot) 900 else 192
                            while (bounds.outWidth / inSampleSize > maximum || bounds.outHeight / inSampleSize > maximum) {
                                inSampleSize *= 2
                            }
                        }
                        checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).asImageBitmap()
                    }
                }
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = image?.getOrNull()
        if (bitmap != null) {
            Image(bitmap, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (screenshot && image?.isFailure == true) {
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.network_retry)) }
        } else {
            Icon(Icons.Outlined.Extension, contentDescription = description)
        }
    }
}

@Composable
internal fun InstalledModuleArtwork(moduleId: String, version: Int, path: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, moduleId, version, path) {
        value = withContext(Dispatchers.IO) { runCatching {
            require(moduleId.matches(Regex("[A-Za-z0-9_.-]+")))
            val uri = android.net.Uri.parse(path ?: "")
            val raw = uri.path ?: error("No icon path")
            require(java.nio.file.Path.of(raw).normalize().toString().startsWith("/data/adb/modules/$moduleId/"))
            val file = com.topjohnwu.superuser.io.SuFile(raw).apply { shell = me.weishu.kernelsu.ui.util.getRootShell(true) }
            val bytes = com.topjohnwu.superuser.io.SuFileInputStream.open(file).use { it.readNBytes(1024 * 1024 + 1) }
            require(bytes.size <= 1024 * 1024)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            val options = BitmapFactory.Options().apply { while (bounds.outWidth / inSampleSize > 192 || bounds.outHeight / inSampleSize > 192) inSampleSize *= 2 }
            checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).asImageBitmap()
        }.getOrNull() }
    }
    if (bitmap != null) Image(bitmap!!, null, modifier, contentScale = ContentScale.Fit)
    else Icon(Icons.Outlined.Extension, null, modifier)
}
