package com.routinealarm.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.routinealarm.app.model.VisualKind
import com.routinealarm.app.alarm.AlarmPlaybackLease
import com.routinealarm.app.alarm.AlarmPlaybackPositionStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun AlarmVisual(
    visualUri: String?,
    visualKind: VisualKind,
    modifier: Modifier = Modifier,
    cropToFill: Boolean = true,
    playVideo: Boolean = true,
) {
    key(visualUri, visualKind, cropToFill, playVideo) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                createAlarmVisualView(
                    context = context,
                    visualUri = visualUri,
                    visualKind = visualKind,
                    cropToFill = cropToFill,
                    playVideo = playVideo,
                )
            },
        )
    }
}

fun createAlarmVisualView(
    context: Context,
    visualUri: String?,
    visualKind: VisualKind,
    cropToFill: Boolean = true,
    playVideo: Boolean = true,
    playbackSessionId: String? = null,
    onPlaybackReady: (() -> Unit)? = null,
): View {
    if (visualUri == null || visualKind == VisualKind.NONE) {
        return TextView(context).apply {
            setBackgroundColor(Color.rgb(28, 24, 36))
            setTextColor(Color.WHITE)
            text = "알람"
            textSize = 48f
            gravity = Gravity.CENTER
            post { onPlaybackReady?.invoke() }
        }
    }
    val uri = runCatching { Uri.parse(visualUri) }
        .getOrElse {
            return createUnavailableVisualView(context, onPlaybackReady)
        }
    return when (visualKind) {
        VisualKind.VIDEO -> if (!playVideo) {
            createVideoPosterView(context, uri, cropToFill)
        } else if (playbackSessionId == null) {
            createPreviewVideoView(
                context = context,
                uri = uri,
                cropToFill = cropToFill,
                onPlaybackReady = onPlaybackReady,
            )
        } else {
            createPlayingVideoView(
                context = context,
                uri = uri,
                cropToFill = cropToFill,
                playbackSessionId = playbackSessionId,
                onPlaybackReady = onPlaybackReady,
            )
        }

        VisualKind.IMAGE,
        VisualKind.ANIMATED_IMAGE,
        -> ImageView(context).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = if (cropToFill) {
                ImageView.ScaleType.CENTER_CROP
            } else {
                ImageView.ScaleType.FIT_CENTER
            }
            runCatching { loadDrawable(context, uri) }
                .onSuccess { image ->
                    setImageDrawable(image)
                    (image as? Animatable)?.start()
                }
            post { onPlaybackReady?.invoke() }
        }

        VisualKind.NONE -> View(context).apply {
            setBackgroundColor(Color.BLACK)
            post { onPlaybackReady?.invoke() }
        }
    }
}

/**
 * Keeps an inaccessible or unsupported local URI from taking down the home
 * screen when an old document-provider grant has disappeared. A poster is
 * still shown when possible; a normal URI keeps the existing muted loop.
 */
private fun createPlayingVideoView(
    context: Context,
    uri: Uri,
    cropToFill: Boolean,
    playbackSessionId: String?,
    onPlaybackReady: (() -> Unit)?,
): View {
    val poster = createVideoPosterView(context, uri, cropToFill)
    val video = runCatching {
        CenterCropVideoView(context, cropToFill, playbackSessionId).apply {
            readVideoDisplaySize(context, uri)?.let { setSourceVideoSize(it.width, it.height) }
            setVideoURI(uri)
        }
    }.getOrNull()

    if (video == null) {
        poster.post { onPlaybackReady?.invoke() }
        return poster
    }

    return FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        // The child is a SurfaceView-backed VideoView. Keep the containing
        // card as the hard crop boundary, especially for wide two-column
        // layouts where the card aspect is much wider than most videos.
        clipChildren = true
        clipToPadding = true
        addView(
            poster,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        video.setOnPreparedListener { player ->
            runCatching {
                // readVideoDisplaySize() includes encoded rotation. Only use
                // the prepared dimensions as a fallback when metadata was not
                // available; otherwise a 90/270-degree video would lose its
                // corrected aspect ratio here.
                if (!video.hasSourceVideoSize && player.videoWidth > 0 && player.videoHeight > 0) {
                    video.setSourceVideoSize(player.videoWidth, player.videoHeight)
                }
                // Keep the surface bounded by the card. CenterCropVideoView
                // applies an explicit view transform below so the source
                // ratio is preserved even on OEM SurfaceView implementations
                // that ignore MediaPlayer's cropping scaling mode.
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                player.isLooping = true
                // Home-card video previews remain muted; alarm playback uses
                // its separate foreground audio path.
                player.setVolume(0f, 0f)
                video.restorePlaybackPosition()
                video.start()
                poster.visibility = View.GONE
                onPlaybackReady?.invoke()
            }.onFailure {
                video.visibility = View.GONE
                poster.visibility = View.VISIBLE
                onPlaybackReady?.invoke()
            }
        }
        video.setOnErrorListener { _, _, _ ->
            video.visibility = View.GONE
            poster.visibility = View.VISIBLE
            onPlaybackReady?.invoke()
            true
        }
    }
}

/**
 * Home/editor previews use TextureView instead of VideoView's SurfaceView.
 * The view stays exactly the size of its card; only the texture content is
 * center-cropped, so adjacent cards cannot receive an enlarged surface.
 */
private fun createPreviewVideoView(
    context: Context,
    uri: Uri,
    cropToFill: Boolean,
    onPlaybackReady: (() -> Unit)?,
): View {
    val poster = createVideoPosterView(context, uri, cropToFill)
    val video = runCatching {
        PreviewTextureVideoView(
            context = context,
            uri = uri,
            cropToFill = cropToFill,
            sourceSize = readVideoDisplaySize(context, uri),
            poster = poster,
            onPlaybackReady = onPlaybackReady,
        )
    }.getOrNull()

    if (video == null) {
        poster.post { onPlaybackReady?.invoke() }
        return poster
    }

    return FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        clipChildren = true
        clipToPadding = true
        addView(
            poster,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
    }
}

private class PreviewTextureVideoView(
    context: Context,
    private val uri: Uri,
    private val cropToFill: Boolean,
    private var sourceSize: VideoDisplaySize?,
    private val poster: View,
    private val onPlaybackReady: (() -> Unit)?,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var outputSurface: Surface? = null
    private var playerGeneration = 0L

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        updateTextureTransform(width, height)
        startPlayer(surface)
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        updateTextureTransform(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        poster.visibility = VISIBLE
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        surfaceTextureListener = null
        releasePlayer()
        super.onDetachedFromWindow()
    }

    private fun startPlayer(surfaceTexture: SurfaceTexture) {
        releasePlayer()
        val generation = ++playerGeneration
        val surface = Surface(surfaceTexture)
        outputSurface = surface
        val candidate = runCatching {
            MediaPlayer().apply {
                setDataSource(context, uri)
                setSurface(surface)
                isLooping = true
                setVolume(0f, 0f)
            }
        }.getOrNull()

        if (candidate == null) {
            releasePlayer()
            poster.visibility = VISIBLE
            onPlaybackReady?.invoke()
            return
        }

        player = candidate
        candidate.setOnPreparedListener { prepared ->
            if (generation != playerGeneration || player !== candidate) {
                runCatching { prepared.release() }
                return@setOnPreparedListener
            }
            if (sourceSize == null && prepared.videoWidth > 0 && prepared.videoHeight > 0) {
                sourceSize = VideoDisplaySize(prepared.videoWidth, prepared.videoHeight)
            }
            updateTextureTransform(width, height)
            runCatching {
                prepared.isLooping = true
                prepared.setVolume(0f, 0f)
                prepared.start()
                poster.visibility = GONE
                onPlaybackReady?.invoke()
            }.onFailure {
                handlePlaybackFailure(generation)
            }
        }
        candidate.setOnErrorListener { _, _, _ ->
            if (generation == playerGeneration && player === candidate) {
                handlePlaybackFailure(generation)
                true
            } else {
                false
            }
        }
        runCatching { candidate.prepareAsync() }
            .onFailure { handlePlaybackFailure(generation) }
    }

    private fun handlePlaybackFailure(generation: Long) {
        if (generation != playerGeneration) return
        releasePlayer()
        poster.visibility = VISIBLE
        onPlaybackReady?.invoke()
    }

    private fun updateTextureTransform(viewWidth: Int, viewHeight: Int) {
        val size = sourceSize
        if (viewWidth <= 0 || viewHeight <= 0 || size == null) {
            setTransform(Matrix())
            return
        }
        val widthScale = viewWidth.toFloat() / size.width.toFloat()
        val heightScale = viewHeight.toFloat() / size.height.toFloat()
        val scale = if (cropToFill) {
            max(widthScale, heightScale)
        } else {
            min(widthScale, heightScale)
        }
        setTransform(Matrix().apply {
            setScale(scale, scale, viewWidth / 2f, viewHeight / 2f)
        })
    }

    private fun releasePlayer() {
        playerGeneration += 1L
        player?.runCatching {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            stop()
            release()
        }
        player = null
        outputSurface?.runCatching { release() }
        outputSurface = null
    }
}

private fun createUnavailableVisualView(
    context: Context,
    onPlaybackReady: (() -> Unit)?,
): TextView = TextView(context).apply {
    setBackgroundColor(Color.rgb(28, 24, 36))
    setTextColor(Color.WHITE)
    text = "미디어를 불러올 수 없습니다"
    textSize = 14f
    gravity = Gravity.CENTER
    contentDescription = "미디어를 불러올 수 없습니다"
    post { onPlaybackReady?.invoke() }
}

/**
 * A non-playing card preview for local videos. Multiple media players can
 * contend for the device decoder, so compact dashboard cards decode one
 * poster frame and release the retriever while the featured card keeps the
 * actual muted loop running.
 */
private fun createVideoPosterView(
    context: Context,
    uri: Uri,
    cropToFill: Boolean,
): ImageView {
    val poster = readVideoPoster(context, uri)
    return ImageView(context).apply {
        setBackgroundColor(Color.BLACK)
        scaleType = if (cropToFill) {
            ImageView.ScaleType.CENTER_CROP
        } else {
            ImageView.ScaleType.FIT_CENTER
        }
        if (poster != null) {
            setImageBitmap(poster)
        } else {
            contentDescription = "영상 미리보기를 불러올 수 없습니다"
        }
    }
}

private fun readVideoPoster(context: Context, uri: Uri): android.graphics.Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

data class VideoDisplaySize(
    val width: Int,
    val height: Int,
) {
    val isLandscape: Boolean get() = width >= height
}

/** Returns the video's displayed size, after its encoded rotation is applied. */
fun readVideoDisplaySize(context: Context, uri: Uri): VideoDisplaySize? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val encodedWidth = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val encodedHeight = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val rotation = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?.let { ((it % 360) + 360) % 360 }
            ?: 0
        if (rotation == 90 || rotation == 270) {
            VideoDisplaySize(encodedHeight, encodedWidth)
        } else {
            VideoDisplaySize(encodedWidth, encodedHeight)
        }
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

fun readLocalVisualDisplaySize(
    context: Context,
    visualUri: String?,
    visualKind: VisualKind,
): VideoDisplaySize? {
    val uri = visualUri?.let(Uri::parse) ?: return null
    return when (visualKind) {
        VisualKind.VIDEO -> readVideoDisplaySize(context, uri)
        VisualKind.IMAGE,
        VisualKind.ANIMATED_IMAGE,
        -> readImageDisplaySize(context, uri)
        VisualKind.NONE -> null
    }
}

private fun readImageDisplaySize(context: Context, uri: Uri): VideoDisplaySize? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_WIDTH)
                ?.toIntOrNull()
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_HEIGHT)
                ?.toIntOrNull()
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_ROTATION)
                ?.toIntOrNull()
                ?.let { ((it % 360) + 360) % 360 }
                ?: 0
            if (width != null && width > 0 && height != null && height > 0) {
                return if (rotation == 90 || rotation == 270) {
                    VideoDisplaySize(height, width)
                } else {
                    VideoDisplaySize(width, height)
                }
            }
        } catch (_: Exception) {
            // Fall through to the decoded drawable dimensions.
        } finally {
            retriever.release()
        }
    }
    return runCatching { loadDrawable(context, uri) }
        .getOrNull()
        ?.let { drawable ->
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            if (width > 0 && height > 0) VideoDisplaySize(width, height) else null
        }
}

private class CenterCropVideoView(
    context: Context,
    private val cropToFill: Boolean,
    playbackSessionId: String?,
) : VideoView(context) {
    private var sourceWidth = 0
    private var sourceHeight = 0
    val hasSourceVideoSize: Boolean
        get() = sourceWidth > 0 && sourceHeight > 0
    private val handler = Handler(Looper.getMainLooper())
    private val playbackLease: AlarmPlaybackLease? = playbackSessionId?.let { sessionId ->
        AlarmPlaybackPositionStore.attach(sessionId) { capturePositionAndPause() }
    }
    // A fold/window transition can briefly tear down the underlying SurfaceView.
    // During that gap VideoView may report position 0 even though playback has
    // not logically restarted. Keep the last valid position so that transient
    // surface state cannot overwrite the session hand-off position.
    private var lastStablePositionMillis = playbackLease?.positionMillis() ?: 0L
    private val positionCaptureLoop = object : Runnable {
        override fun run() {
            capturePosition()
            handler.postDelayed(this, POSITION_CAPTURE_INTERVAL_MILLIS)
        }
    }

    fun setSourceVideoSize(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
        requestLayout()
        updateCropTransform()
    }

    private fun updateCropTransform() {
        if (!cropToFill || width <= 0 || height <= 0 || !hasSourceVideoSize) {
            scaleX = 1f
            scaleY = 1f
            pivotX = width / 2f
            pivotY = height / 2f
            return
        }
        pivotX = width / 2f
        pivotY = height / 2f
        // VideoView asks MediaPlayer to fit the source into its surface while
        // preserving the source ratio. Uniformly enlarge that fitted result
        // until it covers the whole card; the parent then clips the overflow.
        // Using one scale for both axes is important: scaling only one axis
        // would reintroduce the wide-card flattening this view is avoiding.
        val fitted = calculateAspectFitSize(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            maxWidth = width,
            maxHeight = height,
        )
        val coverScale = max(
            width.toFloat() / fitted.width.toFloat(),
            height.toFloat() / fitted.height.toFloat(),
        )
        scaleX = coverScale
        scaleY = coverScale
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateCropTransform()
    }

    fun restorePlaybackPosition() {
        val savedPosition = playbackLease?.positionMillis() ?: return
        if (savedPosition <= 0L) return
        val safePosition = duration.takeIf { it > 0 }
            ?.let { savedPosition % it }
            ?: savedPosition
        seekTo(safePosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun capturePosition(): Boolean {
        val position = runCatching {
            val current = currentPosition.toLong()
            val currentDuration = duration
            if (!isPlaying || currentDuration <= 0 || current <= 0L) null else current
        }.getOrNull() ?: return false
        lastStablePositionMillis = position
        playbackLease?.updatePosition(position)
        return true
    }

    private fun capturePositionAndPause() {
        if (!capturePosition() && lastStablePositionMillis > 0L) {
            playbackLease?.updatePosition(lastStablePositionMillis)
        }
        runCatching { pause() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val targetWidth = MeasureSpec.getSize(widthMeasureSpec)
        val targetHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (!cropToFill && sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0) {
            val fitted = calculateAspectFitSize(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                maxWidth = targetWidth,
                maxHeight = targetHeight,
            )
            setMeasuredDimension(fitted.width, fitted.height)
        } else {
            // Keep the SurfaceView exactly inside its Compose card. Never measure
            // larger than the parent: SurfaceView buffers are not clipped like
            // ordinary child views and would otherwise bleed into another card.
            setMeasuredDimension(targetWidth, targetHeight)
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(positionCaptureLoop)
        capturePositionAndPause()
        runCatching { stopPlayback() }
        playbackLease?.release()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(positionCaptureLoop)
        handler.post(positionCaptureLoop)
    }

    private companion object {
        const val POSITION_CAPTURE_INTERVAL_MILLIS = 250L
    }
}

/** Largest whole-pixel rectangle that keeps the source ratio inside the available bounds. */
fun calculateAspectFitSize(
    sourceWidth: Int,
    sourceHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
): VideoDisplaySize {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(maxWidth > 0 && maxHeight > 0)

    val sourceIsWiderThanBounds =
        sourceWidth.toLong() * maxHeight > maxWidth.toLong() * sourceHeight
    return if (sourceIsWiderThanBounds) {
        VideoDisplaySize(
            width = maxWidth,
            height = (sourceHeight.toDouble() * maxWidth / sourceWidth)
                .roundToInt()
                .coerceIn(1, maxHeight),
        )
    } else {
        VideoDisplaySize(
            width = (sourceWidth.toDouble() * maxHeight / sourceHeight)
                .roundToInt()
                .coerceIn(1, maxWidth),
            height = maxHeight,
        )
    }
}

private fun loadDrawable(context: Context, uri: Uri): Drawable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            Drawable.createFromStream(stream, uri.toString())
        }
    }
