package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.torrent.FileStatus
import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus
import io.github.donggi.iroirobrowser.torrent.meta.TorrentFile
import io.github.donggi.iroirobrowser.torrent.tracker.TrackerStatus

/** 시험의 재료 — 엔진의 값 객체를 손으로 짓는다(공개 생성자). */
internal object TorrentFixtures {

    const val ID = "0123456789abcdef0123456789abcdef01234567"
    const val OTHER = "fedcba9876543210fedcba9876543210fedcba98"

    fun file(
        index: Int,
        path: String,
        length: Long = 100,
        doneBytes: Long = 0,
        selected: Boolean = true,
        state: String = "queued",
        released: Boolean = false,
    ) = FileStatus(index, path, length, doneBytes, selected, state, released)

    fun status(
        id: String = ID,
        name: String = "Name",
        state: TorrentState = TorrentState.DOWNLOADING,
        error: TorrentError? = null,
        wantedBytes: Long = 1000,
        doneBytes: Long = 500,
        downloadedBytes: Long = 600,
        downloadRate: Long = 2048,
        connectedPeers: Int = 5,
        knownPeers: Int = 40,
        seeds: Int = 3,
        webSeeds: Int = 0,
        activeFile: Int = -1,
        files: List<FileStatus> = emptyList(),
        trackers: List<TrackerStatus> = emptyList(),
        peerSources: Map<String, Int> = emptyMap(),
        mseAttempts: Int = 0,
        mseSucceeded: Int = 0,
        plainFallbacks: Int = 0,
        hashFailures: Int = 0,
    ) = TorrentStatus(
        id = id,
        name = name,
        state = state,
        error = error,
        errorDetail = null,
        wantedBytes = wantedBytes,
        doneBytes = doneBytes,
        downloadedBytes = downloadedBytes,
        downloadRate = downloadRate,
        connectedPeers = connectedPeers,
        knownPeers = knownPeers,
        seeds = seeds,
        webSeeds = webSeeds,
        activeFile = activeFile,
        files = files,
        trackers = trackers,
        peerSources = peerSources,
        mseAttempts = mseAttempts,
        mseSucceeded = mseSucceeded,
        plainFallbacks = plainFallbacks,
        hashFailures = hashFailures,
        addedAtMs = 0L,
    )

    fun torrentFile(
        index: Int,
        path: List<String>,
        pad: Boolean = false,
        symlink: Boolean = false,
        padLike: Boolean = false,
        length: Long = 100,
    ) = TorrentFile(index, path, length, offset = index * length, isPad = pad, isSymlink = symlink, isPadLike = padLike, urlPath = path)
}
