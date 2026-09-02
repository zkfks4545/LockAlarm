package com.routinealarm.app.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.routinealarm.app.alarm.AlarmPlaybackLease
import com.routinealarm.app.alarm.AlarmPlaybackPositionStore
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

enum class YouTubePreviewState {
    EMPTY,
    UNSUPPORTED,
    READY,
}

object YouTubeEmbed {
    fun videoId(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val candidate = when {
            host == "youtu.be" -> uri.pathSegments().firstOrNull()
            host == "youtube.com" || host.endsWith(".youtube.com") -> when {
                uri.path.orEmpty() == "/watch" -> uri.getQueryParameter("v")
                uri.pathSegments().firstOrNull() in setOf("embed", "shorts", "live") ->
                    uri.pathSegments().getOrNull(1)
                else -> null
            }
            else -> null
        }
        return candidate?.takeIf { VIDEO_ID.matches(it) }
    }

    fun isSupportedUrl(value: String): Boolean = videoId(value) != null

    fun previewState(value: String): YouTubePreviewState = when {
        value.isBlank() -> YouTubePreviewState.EMPTY
        videoId(value) != null -> YouTubePreviewState.READY
        else -> YouTubePreviewState.UNSUPPORTED
    }

    fun thumbnailUrl(value: String): String? = videoId(value)
        ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    fun thumbnailHtml(value: String): String? {
        val imageUrl = thumbnailUrl(value) ?: return null
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
              <meta name="referrer" content="strict-origin-when-cross-origin">
              <style>
                html,body{width:100%;height:100%;margin:0;background:#000;overflow:hidden}
                img{display:block;width:100%;height:100%;object-fit:cover}
              </style>
            </head>
            <body><img src="$imageUrl" alt="YouTube 동영상 미리보기"></body>
            </html>
        """.trimIndent()
    }

    fun isShortsUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        return (host == "youtube.com" || host.endsWith(".youtube.com")) &&
            uri.pathSegments().firstOrNull() == "shorts" &&
            videoId(value) != null
    }

    fun appOrigin(applicationId: String): String = "https://$applicationId"

    fun playerHtml(
        value: String,
        applicationId: String = DEFAULT_APPLICATION_ID,
        startPositionMillis: Long = 0L,
    ): String? {
        val id = videoId(value) ?: return null
        val origin = appOrigin(applicationId)
        val startSeconds = String.format(
            Locale.US,
            "%.3f",
            startPositionMillis.coerceAtLeast(0L) / 1_000.0,
        )
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
              <meta name="referrer" content="strict-origin-when-cross-origin">
              <style>
                html,body,#player{width:100%;height:100%;margin:0;background:#000;overflow:hidden}
                #message{box-sizing:border-box;display:none;width:100%;height:100%;padding:32px;
                  align-items:center;justify-content:center;text-align:center;color:#fff;
                  background:#111;font:600 18px/1.55 sans-serif;white-space:pre-line}
              </style>
            </head>
            <body>
              <div id="player"></div>
              <div id="message" role="alert"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var player;
                function showMessage(text){
                  var playerElement=document.getElementById('player');
                  var messageElement=document.getElementById('message');
                  if(playerElement){playerElement.style.display='none';}
                  messageElement.textContent=text;
                  messageElement.style.display='flex';
                }
                function onPlayerError(event){
                  if(event.data===101 || event.data===150){
                    showMessage('이 영상은 게시자가 앱 내 재생을 허용하지 않았습니다.\n다른 YouTube 영상으로 설정해 주세요.');
                  }else if(event.data===153){
                    showMessage('YouTube가 이 앱을 확인하지 못했습니다.\n앱이나 Android System WebView를 업데이트한 뒤 다시 시도해 주세요.');
                  }else if(event.data===100){
                    showMessage('삭제되었거나 비공개인 영상이라 재생할 수 없습니다.');
                  }else{
                    showMessage('YouTube 영상을 재생할 수 없습니다. (오류 '+event.data+')');
                  }
                }
                function reportPosition(){
                  try{
                    if(player && player.getCurrentTime && window.RoutineAlarmBridge){
                      window.RoutineAlarmBridge.onPosition(String(player.getCurrentTime()));
                    }
                  }catch(ignored){}
                }
                function onYouTubeIframeAPIReady(){
                  player=new YT.Player('player',{
                    videoId:'$id',
                    width:'100%',height:'100%',
                    playerVars:{
                      autoplay:1,controls:1,playsinline:1,rel:0,loop:1,playlist:'$id',
                      origin:'$origin'
                    },
                    events:{
                      onReady:function(e){
                        if($startSeconds>0){e.target.seekTo($startSeconds,true);}
                        e.target.playVideo();
                        if(window.RoutineAlarmBridge){window.RoutineAlarmBridge.onReady();}
                        window.setInterval(reportPosition,500);
                      },
                      onStateChange:reportPosition,
                      onError:onPlayerError
                    }
                  });
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private const val DEFAULT_APPLICATION_ID = "com.routinealarm.app"
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    private fun URI.pathSegments(): List<String> =
        path.orEmpty().split('/').filter(String::isNotBlank)

    private fun URI.getQueryParameter(name: String): String? = rawQuery
        ?.split('&')
        ?.mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (URLDecoder.decode(pieces[0], Charsets.UTF_8.name()) != name) return@mapNotNull null
            URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
        }
        ?.firstOrNull()
}

@SuppressLint("SetJavaScriptEnabled")
fun createYouTubeWebView(
    context: Context,
    value: String,
    playbackSessionId: String? = null,
    onPlaybackReady: (() -> Unit)? = null,
): WebView {
    lateinit var webView: SessionYouTubeWebView
    webView = SessionYouTubeWebView(context)
    val playbackLease = playbackSessionId?.let { sessionId ->
        AlarmPlaybackPositionStore.attach(sessionId) { webView.pauseForSupersession() }
    }
    webView.bindPlaybackLease(playbackLease)
    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    webView.setBackgroundColor(Color.BLACK)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.mediaPlaybackRequiresUserGesture = false
    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    webView.webChromeClient = WebChromeClient()
    webView.webViewClient = WebViewClient()
    webView.addJavascriptInterface(
        PlaybackJavascriptBridge(playbackLease, onPlaybackReady),
        PLAYBACK_BRIDGE_NAME,
    )
    val applicationId = context.packageName
    val html = YouTubeEmbed.playerHtml(
        value = value,
        applicationId = applicationId,
        startPositionMillis = playbackLease?.positionMillis() ?: 0L,
    )
    if (html != null) {
        webView.loadDataWithBaseURL(
            "${YouTubeEmbed.appOrigin(applicationId)}/",
            html,
            "text/html",
            Charsets.UTF_8.name(),
            null,
        )
    } else {
        webView.post { onPlaybackReady?.invoke() }
    }
    return webView
}

private const val PLAYBACK_BRIDGE_NAME = "RoutineAlarmBridge"

private class PlaybackJavascriptBridge(
    private val playbackLease: AlarmPlaybackLease?,
    private val onPlaybackReady: (() -> Unit)?,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post { onPlaybackReady?.invoke() }
    }

    @JavascriptInterface
    fun onPosition(seconds: String) {
        val positionMillis = seconds.toDoubleOrNull()
            ?.times(1_000.0)
            ?.toLong()
            ?: return
        playbackLease?.updatePosition(positionMillis)
    }
}

private class SessionYouTubeWebView(context: Context) : WebView(context) {
    private var playbackLease: AlarmPlaybackLease? = null

    fun bindPlaybackLease(lease: AlarmPlaybackLease?) {
        playbackLease = lease
    }

    fun pauseForSupersession() {
        evaluateJavascript(
            "if(window.player&&player.pauseVideo){player.pauseVideo();}",
            null,
        )
        onPause()
    }

    fun releasePlaybackLease() {
        playbackLease?.release()
        playbackLease = null
    }

    override fun onDetachedFromWindow() {
        releasePlaybackLease()
        super.onDetachedFromWindow()
    }
}

fun destroyYouTubeWebView(webView: WebView) {
    (webView as? SessionYouTubeWebView)?.releasePlaybackLease()
    webView.stopLoading()
    webView.loadUrl("about:blank")
    webView.destroy()
}

@Composable
fun YouTubePlayer(value: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    key(value) {
        val webView = remember { createYouTubeWebView(context, value).apply { tag = value } }
        AndroidView(
            modifier = modifier,
            factory = { webView },
        )
        DisposableEffect(Unit) {
            onDispose {
                destroyYouTubeWebView(webView)
            }
        }
    }
}
