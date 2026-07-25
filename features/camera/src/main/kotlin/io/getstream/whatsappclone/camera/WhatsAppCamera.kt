/*
 * Copyright 2023 Stream.IO, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.whatsappclone.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.getstream.log.streamLog
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val PHOTO_SAVED_MESSAGE = "Photo saved — share from chat or Status"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WhatsAppCamera(
  isActive: Boolean = true
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(isActive) {
    if (isActive && !cameraPermission.status.isGranted) {
      cameraPermission.launchPermissionRequest()
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(MaterialTheme.colorScheme.background)
    ) {
      if (cameraPermission.status.isGranted) {
        CameraPreviewContent(
          isActive = isActive,
          lifecycleOwner = lifecycleOwner,
          onPhotoSaved = {
            scope.launch {
              snackbarHostState.showSnackbar(PHOTO_SAVED_MESSAGE)
            }
          }
        )
      } else {
        CameraPermissionPlaceholder()
      }
    }
  }
}

@Composable
private fun CameraPreviewContent(
  isActive: Boolean,
  lifecycleOwner: androidx.lifecycle.LifecycleOwner,
  onPhotoSaved: () -> Unit
) {
  val context = LocalContext.current
  val previewView = remember { PreviewView(context) }
  var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
  val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

  LaunchedEffect(isActive, lensFacing) {
    val cameraProvider = context.awaitCameraProvider(mainExecutor)
    if (!isActive) {
      // A CameraX preview continues consuming camera/CPU resources until it is unbound.
      cameraProvider.unbindAll()
      imageCapture = null
      return@LaunchedEffect
    }

    val preview = Preview.Builder().build().also {
      it.surfaceProvider = previewView.surfaceProvider
    }
    val capture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .build()
    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(lensFacing)
      .build()

    try {
      cameraProvider.unbindAll()
      cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        capture
      )
      imageCapture = capture
    } catch (error: Exception) {
      streamLog { "Camera bind failed: ${error.message}" }
      imageCapture = null
    }
  }

  DisposableEffect(context, mainExecutor) {
    onDispose {
      // HorizontalPager disposes inactive pages; release the camera immediately instead of
      // waiting for the activity lifecycle to stop.
      val providerFuture = ProcessCameraProvider.getInstance(context)
      providerFuture.addListener(
        {
          runCatching { providerFuture.get().unbindAll() }
            .onFailure { error ->
              streamLog { "Camera release failed: ${error.message}" }
            }
        },
        mainExecutor
      )
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    AndroidView(
      factory = { previewView },
      modifier = Modifier.fillMaxSize()
    )

    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(bottom = 32.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = {
          lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
          } else {
            CameraSelector.LENS_FACING_BACK
          }
        }
      ) {
        Icon(
          imageVector = Icons.Outlined.Cameraswitch,
          contentDescription = "Flip camera",
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(32.dp)
        )
      }

      Box(
        modifier = Modifier
          .size(72.dp)
          .border(4.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
          .padding(6.dp)
          .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
          .clickable {
            imageCapture?.let { capture ->
              capturePhoto(
                context = context,
                imageCapture = capture,
                executor = mainExecutor,
                onSaved = onPhotoSaved
              )
            }
          }
      )

      Spacer(modifier = Modifier.size(48.dp))
    }
  }
}

/**
 * CameraX returns a future even when its provider has not finished initializing. Waiting with
 * Future.get() from a Compose effect blocks the main thread and causes a visible tab-switch jank.
 */
private suspend fun Context.awaitCameraProvider(executor: Executor): ProcessCameraProvider =
  suspendCancellableCoroutine { continuation ->
    val providerFuture = ProcessCameraProvider.getInstance(this)
    providerFuture.addListener(
      {
        if (continuation.isActive) {
          runCatching { providerFuture.get() }
            .onSuccess { provider -> continuation.resume(provider) }
            .onFailure { error -> continuation.resumeWithException(error) }
        }
      },
      executor
    )
  }

@Composable
private fun CameraPermissionPlaceholder() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(24.dp)
    ) {
      Box(
        modifier = Modifier
          .size(88.dp)
          .background(MaterialTheme.colorScheme.secondary, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = WhatsAppIcons.Camera,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondary,
          modifier = Modifier.size(40.dp)
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "Allow camera access to take photos and videos for chats and Status.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
      )
    }
  }
}

private fun capturePhoto(
  context: Context,
  imageCapture: ImageCapture,
  executor: Executor,
  onSaved: () -> Unit
) {
  val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
  val contentValues = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, "BatchIt_$timestamp")
    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BatchIt")
    }
  }

  val outputOptions = ImageCapture.OutputFileOptions.Builder(
    context.contentResolver,
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    contentValues
  ).build()

  imageCapture.takePicture(
    outputOptions,
    executor,
    object : ImageCapture.OnImageSavedCallback {
      override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        onSaved()
      }

      override fun onError(exception: ImageCaptureException) {
        streamLog { "MediaStore capture failed: ${exception.message}" }
        saveToCache(context, imageCapture, executor, onSaved)
      }
    }
  )
}

private fun saveToCache(
  context: Context,
  imageCapture: ImageCapture,
  executor: Executor,
  onSaved: () -> Unit
) {
  val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
  val photoFile = File(context.cacheDir, "BatchIt_$timestamp.jpg")
  val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

  imageCapture.takePicture(
    outputOptions,
    executor,
    object : ImageCapture.OnImageSavedCallback {
      override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        onSaved()
      }

      override fun onError(exception: ImageCaptureException) {
        streamLog { "Cache capture failed: ${exception.message}" }
      }
    }
  )
}
