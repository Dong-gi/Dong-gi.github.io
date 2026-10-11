package io.github.donggi.iroirobrowser.download

import android.content.Context
import android.text.format.Formatter
import io.github.donggi.iroirobrowser.R

/** 내려받기의 글 — 목록([DownloadsActivity])과 알림([DownloadService])이 함께 쓴다. */
internal object DownloadTexts {

    /** 마무리 중의 글 — 담는 것에 따라(앞 판은 소리만 녹화한 것도 '영상과 소리를 한 파일로 담는 중' 이었다). */
    fun merging(context: Context, item: DownloadCenter.Item): String = context.getString(
        when (item.finishing) {
            DownloadCenter.Finishing.AV -> R.string.download_merging
            DownloadCenter.Finishing.VIDEO -> R.string.download_merging_video
            DownloadCenter.Finishing.AUDIO -> R.string.download_merging_audio
        },
    )

    /** 받는 중인 것의 짧은 진행 — 알림의 줄마다(여럿을 받을 때). */
    fun progress(context: Context, item: DownloadCenter.Item): String = when {
        item.status == DownloadCenter.Status.QUEUED -> context.getString(R.string.download_queued)
        item.merging -> merging(context, item)
        item.retrying > 0 -> context.getString(R.string.download_retrying, item.retrying, item.error ?: "")
        item.gallery != null -> gallery(context, item.gallery)
        item.parts > 0 -> context.getString(R.string.download_parts, item.partsDone, item.parts)
        item.total > 0 -> "${percent(item)}%"
        else -> Formatter.formatShortFileSize(context, item.received)
    }

    fun percent(item: DownloadCenter.Item): Int =
        if (item.total > 0) (item.received * 100 / item.total).toInt().coerceIn(0, 100) else 0

    /** 갤러리 받기의 진행 — 'n / m개' 또는 '작품 a / b · 파일 n'([GalleryRules.progress]). */
    fun gallery(context: Context, g: DownloadCenter.Gallery): String =
        TorrentTexts.text(context, GalleryRules.progress(g.units, g.unitsDone, g.fileCount, g.filesSaved))
}
