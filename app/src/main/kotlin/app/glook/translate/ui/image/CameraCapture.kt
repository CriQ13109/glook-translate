package app.glook.translate.ui.image

import app.glook.translate.R
import app.glook.translate.core.str

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ZoomState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Observer
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.glook.translate.ui.components.ActionButton
import app.glook.translate.ui.components.ChipIconButton
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.Image as GalleryIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 앱 내 카메라(CameraX): 전체 화면 미리보기 + 셔터. 핀치 줌 / 줌 프리셋(기기가 노출하는 배율 범위 안에서) / 탭 초점.
 * 셔터 왼쪽에는 기본 카메라 앱처럼 가장 최근 사진 미리보기 한 장이 있고, 누르면 시스템 사진 선택기가 열립니다.
 * 사진 접근 권한이 없으면 미리보기 대신 갤러리 아이콘이 보이며, 선택기는 권한 없이도 동작합니다.
 */
@Composable
fun CameraCapture(
    onCaptured: (Uri, File) -> Unit,
    onOpenPicker: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camGranted by remember { mutableStateOf(has(context, Manifest.permission.CAMERA)) }
    var mediaGranted by remember { mutableStateOf(hasMediaAccess(context)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camGranted = has(context, Manifest.permission.CAMERA)
        mediaGranted = hasMediaAccess(context)
    }
    fun requestAll() = permissions.launch(buildList {
        if (!camGranted) add(Manifest.permission.CAMERA)
        if (!mediaGranted) {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray())
    LaunchedEffect(Unit) { if (!camGranted || !mediaGranted) requestAll() }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableStateOf<ZoomState?>(null) }
    LaunchedEffect(camGranted) {
        if (!camGranted) return@LaunchedEffect
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).await(context)
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }
    }
    DisposableEffect(camera) {
        val live = camera?.cameraInfo?.zoomState
        val obs = Observer<ZoomState> { zoom = it }
        live?.observe(lifecycleOwner, obs)
        onDispose { live?.removeObserver(obs) }
    }

    val latest by produceState<Uri?>(null, mediaGranted) { value = if (mediaGranted) withContext(Dispatchers.IO) { queryLatest(context) } else null }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (camGranted) {
            AndroidView(
                { previewView },
                Modifier.fillMaxSize()
                    .pointerInput(camera) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            val z = zoom ?: return@detectTransformGestures
                            camera?.cameraControl?.setZoomRatio((z.zoomRatio * zoomChange).coerceIn(z.minZoomRatio, z.maxZoomRatio))
                        }
                    }
                    .pointerInput(camera) {
                        detectTapGestures { o ->
                            val point = previewView.meteringPointFactory.createPoint(o.x, o.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    },
            )
        } else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.camera_permission), color = Color.White, style = MaterialTheme.typography.titleMedium)
            ActionButton(str(R.string.grant_permission), onClick = ::requestAll, selected = true)
        }

        Box(Modifier.safeDrawingPadding().padding(12.dp).align(Alignment.TopStart).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) {
            ChipIconButton(Icons.AutoMirrored.Filled.ArrowBack, str(R.string.back), onClick = onBack)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val z = zoom
            if (z != null && z.maxZoomRatio > 1.05f) {
                val presets = remember(z.minZoomRatio, z.maxZoomRatio) { zoomPresets(z.minZoomRatio, z.maxZoomRatio) }
                val nearest = presets.minByOrNull { kotlin.math.abs(it - z.zoomRatio) }
                Row(
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    presets.forEach { p ->
                        val on = p == nearest
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent)
                                .clickable { camera?.cameraControl?.setZoomRatio(p.coerceIn(z.minZoomRatio, z.maxZoomRatio)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(zoomLabel(p), color = if (on) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LatestThumb(latest, onClick = onOpenPicker)
                Box(
                    Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape).background(Color.White)
                        .clickable(enabled = camGranted) {
                            val file = File(File(context.cacheDir, "ocr").apply { mkdirs() }, "capture.jpg")
                            imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        onCaptured(FileProvider.getUriForFile(context, "${context.packageName}.ocr", file), file)
                                    }
                                    override fun onError(e: ImageCaptureException) { file.delete() }
                                },
                            )
                        },
                )
                Box(Modifier.size(56.dp))
            }
        }
    }
}

/** 셔터 왼쪽의 최근 사진 미리보기(권한 없으면 갤러리 아이콘). 누르면 사진 선택기. */
@Composable
private fun LatestThumb(uri: Uri?, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp: Bitmap? by produceState<Bitmap?>(null, uri) {
        value = uri?.let { withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(it, Size(240, 240), null) }.getOrNull() } }
    }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), str(R.string.gallery), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else ChipIconButton(GlookIcons.GalleryIcon, str(R.string.gallery), onClick = onClick)
    }
}

private fun has(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

private fun hasMediaAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= 34 -> has(context, Manifest.permission.READ_MEDIA_IMAGES) || has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> has(context, Manifest.permission.READ_MEDIA_IMAGES)
    else -> has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** 가장 최근 사진 1장 (셔터 옆 미리보기용). */
private fun queryLatest(context: Context): Uri? = runCatching {
    val args = Bundle().apply {
        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
    }
    context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), args, null)?.use { c ->
        if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null
    }
}.getOrNull()

/** 줌 프리셋: 광각(최소 배율 < 1 이면 그 값), 1x, 그리고 지원 범위 안의 2·3·5·10 배. */
private fun zoomPresets(min: Float, max: Float): List<Float> = buildList {
    if (min < 0.95f) add((kotlin.math.floor(min * 10f) / 10f).coerceAtLeast(min.coerceAtLeast(0.1f)).let { if (it < min) min else it })
    add(1f)
    for (z in listOf(2f, 3f, 5f, 10f)) if (z <= max + 0.01f && size < 5) add(z)
}

private fun zoomLabel(z: Float) = if (z == 1f) "1x" else if (z == z.toInt().toFloat()) "${z.toInt()}" else "%.1f".format(z)

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(context: Context): T = suspendCancellableCoroutine { cont ->
    addListener({ try { cont.resume(get()) } catch (e: Throwable) { cont.resumeWithException(e) } }, ContextCompat.getMainExecutor(context))
}
