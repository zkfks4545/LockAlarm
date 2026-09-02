package com.routinealarm.app.youtube

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun YouTubePreview(value: String, modifier: Modifier = Modifier) {
    when (YouTubeEmbed.previewState(value)) {
        YouTubePreviewState.EMPTY -> PreviewMessage(
            modifier = modifier,
            message = "YouTube URL을 입력하면 미리보기가 표시됩니다.",
        )

        YouTubePreviewState.UNSUPPORTED -> PreviewMessage(
            modifier = modifier,
            message = "지원하지 않는 YouTube URL입니다. youtube.com/watch?v=… 또는 youtu.be/… 형식을 사용해 주세요.",
        )

        YouTubePreviewState.READY -> {
            var isPlaying by remember(value) { mutableStateOf(false) }
            key(value, isPlaying) {
                if (isPlaying) {
                    Column(
                        modifier = modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        ) {
                            YouTubePlayer(value = value, modifier = Modifier.fillMaxSize())
                        }
                        TextButton(onClick = { isPlaying = false }) {
                            Text("미리보기 닫기")
                        }
                    }
                } else {
                    YouTubeThumbnail(
                        value = value,
                        modifier = modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        onPlay = { isPlaying = true },
                    )
                }
            }
        }
    }
}

/** Dashboard-only YouTube surface: show the official thumbnail without an IFrame player. */
@Composable
fun YouTubeThumbnailOnly(value: String, modifier: Modifier = Modifier) {
    when (YouTubeEmbed.previewState(value)) {
        YouTubePreviewState.EMPTY -> PreviewMessage(
            modifier = modifier,
            message = "YouTube URL을 입력하면 썸네일이 표시됩니다.",
        )

        YouTubePreviewState.UNSUPPORTED -> PreviewMessage(
            modifier = modifier,
            message = "지원하지 않는 YouTube URL입니다.",
        )

        YouTubePreviewState.READY -> {
            val context = LocalContext.current
            key(value) {
                val webView = remember { createYouTubeThumbnailWebView(context, value) }
                AndroidView(
                    modifier = modifier,
                    factory = { webView },
                )
                DisposableEffect(Unit) {
                    onDispose { destroyYouTubeWebView(webView) }
                }
            }
        }
    }
}

/**
 * Compact, parent-controlled preview surface for alarm dashboard cards.
 *
 * Unlike [YouTubePreview], this composable does not own play/stop state. The
 * dashboard can therefore keep at most one card player alive and remove the
 * WebView from composition immediately when another card starts or preview is
 * switched off.
 */
@Composable
fun YouTubeCardPreview(
    value: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    when (YouTubeEmbed.previewState(value)) {
        YouTubePreviewState.EMPTY -> PreviewMessage(
            modifier = modifier,
            message = "YouTube URL을 입력하면 미리보기가 표시됩니다.",
        )

        YouTubePreviewState.UNSUPPORTED -> PreviewMessage(
            modifier = modifier,
            message = "지원하지 않는 YouTube URL입니다.",
        )

        YouTubePreviewState.READY -> {
            if (isPlaying) {
                Box(modifier = modifier) {
                    YouTubePlayer(value = value, modifier = Modifier.fillMaxSize())
                    TextButton(
                        onClick = onStop,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        Text("닫기")
                    }
                }
            } else {
                YouTubeThumbnail(
                    value = value,
                    modifier = modifier,
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun PreviewMessage(modifier: Modifier, message: String) {
    Text(
        text = message,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun YouTubeThumbnail(
    value: String,
    modifier: Modifier,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    key(value) {
        val webView = remember { createYouTubeThumbnailWebView(context, value) }
        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webView },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Button(onClick = onPlay) {
                    Text("▶ 재생")
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose { destroyYouTubeWebView(webView) }
        }
    }
}

private fun createYouTubeThumbnailWebView(context: Context, value: String): WebView {
    val webView = WebView(context)
    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    webView.setBackgroundColor(Color.BLACK)
    webView.settings.javaScriptEnabled = false
    webView.settings.domStorageEnabled = false
    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false
    webView.webViewClient = WebViewClient()
    YouTubeEmbed.thumbnailHtml(value)?.let { html ->
        webView.loadDataWithBaseURL(
            "${YouTubeEmbed.appOrigin(context.packageName)}/",
            html,
            "text/html",
            Charsets.UTF_8.name(),
            null,
        )
    }
    return webView
}
