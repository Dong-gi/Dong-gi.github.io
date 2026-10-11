package io.github.donggi.iroirobrowser

import android.content.Context
import android.webkit.WebSettings
import io.github.donggi.iroirobrowser.core.UserAgents

/**
 * 앱이 스스로 내는 요청(출구 확인·광고 목록 받기)의 User-Agent — 쪽과 같은 **일반화된 꼴**(사용자 결정, 2026-10-05). WebView 의 기본값을 그대로 쓰면
 * 기기 모델·`wv` 가 실린다(실험실에서 출구 확인이 그렇게 나갔다). 내려받기는 쪽의 WebView 에서 UA 를 받으므로 이미 같다.
 */
object AppUserAgent {

    @Volatile
    private var cached: String? = null

    fun of(context: Context): String = cached ?: run {
        val default = runCatching { WebSettings.getDefaultUserAgent(context.applicationContext) }.getOrDefault("")
        (UserAgents.generalize(default) ?: FALLBACK).also { cached = it }
    }

    /** WebView 의 기본값에서 크롬 판을 읽지 못할 때(꼴이 바뀌었다) — 판은 오래된 것이지만 모델은 싣지 않는다. */
    private const val FALLBACK = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
}
