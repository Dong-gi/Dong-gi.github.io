package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.io.PathRules
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.ui.KindBadge
import io.github.donggi.iroiroviewer.ui.ThumbnailStore

/**
 * 목록 한 줄.
 *
 * 긴 이름은 **가운데를 줄인다.** 끝을 자르면 확장자가 사라져서 무슨 파일인지 알 수
 * 없게 되는데, 파일 관리자에서 그것은 치명적이다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 지금 썸네일을 새로 만들어도 되는가. 스크롤 중에는 false 다. */
    allowLoad: Boolean = true,
    /** 읽던 쪽 배지. 만화·문서에 이어보기 기록이 있을 때만 있다. */
    badge: ReadingBadge? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // 고른 줄은 배경으로 표시한다. 체크박스를 따로 두면 목록이 좁아지고,
            // 안드로이드 파일 관리자들의 관행도 배경 강조다.
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else androidx.compose.ui.graphics.Color.Transparent
            )
            // 배경색만으로는 화면 낭독기가 아무것도 읽지 못하고, 색을 구분하기
            // 어려운 사람에게도 표시가 없는 것과 같다. 삭제·이동이 걸린 상태다.
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // 목록에서도 사진은 그림으로 보인다. 이름만으로 사진을 고르는 것은
            // 파일 관리자에서 가장 답답한 순간이다.
            Thumb(entry = entry, size = 40.dp, allowLoad = allowLoad)
            if (selected) SelectedMark(Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitleOf(entry, badge),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 읽는 중이면 가는 막대. 글자('12/30쪽')만으로는 한눈에 얼마나 왔는지 읽히지 않는다. 다 읽은 것은
            // 막대 대신 글자('다 읽음')가 말한다 — 가득 찬 막대 열 개가 늘어서면 목록이 무거워 보인다.
            if (badge != null && !badge.finished) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { badge.fraction },
                    modifier = Modifier.fillMaxWidth(0.5f).height(3.dp),
                )
            }
        }
    }
}

/** 격자 한 칸. 썸네일은 6단계에서 들어온다 — 지금은 같은 배지를 크게 쓴다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 지금 썸네일을 새로 만들어도 되는가. 스크롤 중에는 false 다. */
    allowLoad: Boolean = true,
    /** 읽던 쪽 배지. 만화·문서에 이어보기 기록이 있을 때만 있다. */
    badge: ReadingBadge? = null,
) {
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .semantics { this.selected = selected }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            // **자리를 먼저 잡고 내용만 바꾼다.** 썸네일이 도착할 때 상자 크기가 변하면
            // 격자 전체가 한 번 다시 흐르고, 스크롤 중이면 그것이 그대로 끊김으로 보인다.
            Thumb(
                entry = entry,
                size = 56.dp,
                allowLoad = allowLoad,
            )
            if (selected) SelectedMark(Modifier.align(Alignment.BottomEnd))
        }
        if (badge != null && !badge.finished) {
            // 그림 바로 아래, 그림 폭만큼. 칸의 글자와 겹치지 않게 그림에 붙인다.
            LinearProgressIndicator(
                progress = { badge.fraction },
                modifier = Modifier.width(56.dp).height(3.dp),
            )
        }
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.Center,
        )
        if (badge != null) {
            // 막대만 두면 색으로만 말하는 셈이다(화면 낭독기와 색을 가리기 어려운 사람에게는 없는 것과 같다).
            Text(
                text = badgeText(badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

/**
 * 둘째 줄. 폴더에는 크기를 적지 않는다 — `st_size` 는 디렉터리 엔트리가 차지하는
 * 바이트일 뿐 안에 든 것의 합이 아니어서, 적으면 거짓말이 된다.
 */
@Composable
private fun subtitleOf(entry: FileEntry, reading: ReadingBadge?): String {
    val time = Format.timestamp(entry.lastModified)
    val badges = buildList {
        if (entry.isLocked) add(stringResource(R.string.browser_locked_badge))
        if (entry.isSymlink) add(stringResource(R.string.browser_symlink_badge))
        if (reading != null) add(badgeText(reading))
    }
    val head = if (entry.isDirectory) "" else Format.size(entry.size) + " · "
    val tail = if (badges.isEmpty()) "" else " · " + badges.joinToString(" · ")
    return head + time + tail
}

/**
 * 읽던 쪽 배지의 글자. '12/30쪽'·'3/12장'·'37%'·'다 읽음'. 단위는 문서 뷰어의 이어보기 안내와 같은 갈래다
 * (그 문구는 `feature:docview` 에 있어 여기서 볼 수 없다 — feature 끼리는 서로를 보지 않는다).
 */
@Composable
fun badgeText(badge: ReadingBadge): String = when {
    badge.finished -> stringResource(R.string.browser_badge_finished)
    // 백분율 문구는 인자가 하나다. 쪽 문구와 한 호출에 태우면 인자 수가 문구와 어긋난다 — 문구를 `when` 으로
    // 고르는 자리라 lint 의 형식 검사도 닿지 않는다.
    badge.unit == BadgeUnit.PERCENT -> stringResource(R.string.browser_badge_percent, badge.current)
    else -> stringResource(
        when (badge.unit) {
            BadgeUnit.CHAPTER -> R.string.browser_badge_chapter
            BadgeUnit.SHEET -> R.string.browser_badge_sheet
            BadgeUnit.SLIDE -> R.string.browser_badge_slide
            BadgeUnit.PART -> R.string.browser_badge_part
            // 백분율은 위에서 갈랐다. `else` 로 두지 않는 것은 단위가 늘 때 컴파일러가 이 자리를 가리키게 하려는 것이다.
            BadgeUnit.PAGE, BadgeUnit.PERCENT -> R.string.browser_badge_page
        },
        badge.current,
        badge.total,
    )
}

@Composable
fun kindLabel(kind: FileKind): String = stringResource(
    when (kind) {
        FileKind.FOLDER -> R.string.browser_kind_folder
        FileKind.IMAGE -> R.string.browser_kind_image
        FileKind.AUDIO -> R.string.browser_kind_audio
        FileKind.VIDEO -> R.string.browser_kind_video
        FileKind.TEXT -> R.string.browser_kind_text
        FileKind.CODE -> R.string.browser_kind_code
        FileKind.PDF -> R.string.browser_kind_pdf
        FileKind.EBOOK -> R.string.browser_kind_ebook
        FileKind.ARCHIVE -> R.string.browser_kind_archive
        FileKind.COMIC -> R.string.browser_kind_comic
        FileKind.DOCUMENT -> R.string.browser_kind_document
        FileKind.SHEET -> R.string.browser_kind_sheet
        FileKind.SLIDE -> R.string.browser_kind_slide
        FileKind.HWP -> R.string.browser_kind_hwp
        FileKind.APK -> R.string.browser_kind_apk
        FileKind.FONT -> R.string.browser_kind_font
        FileKind.OTHER -> R.string.browser_kind_other
    }
)

/**
 * 골랐다는 **둘째 단서**. 배경색 하나로만 알리면 색을 구분하기 어려운 사람에게는
 * 표시가 없는 것과 같고, 그 상태에서 누르는 것이 삭제다.
 */
@Composable
private fun SelectedMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * 격자 칸의 그림.
 *
 * 사진·동영상이면 썸네일, 아니면 종류 배지다. **상자 크기는 둘 다 같다** — 도착 전후로
 * 레이아웃이 움직이지 않아야 스크롤이 끊기지 않는다.
 *
 * ## 스크롤 중에는 새로 만들지 않는다
 *
 * 썸네일 디스패처는 선입선출이라, 빠르게 훑는 동안 요청을 계속 넣으면 **이미 지나간 칸**이
 * 줄 앞을 차지해 정작 멈춘 자리의 칸이 늦게 온다. 취소를 잘 거는 것보다 애초에 줄을
 * 세우지 않는 편이 싸다. 메모리에 이미 있는 것은 스크롤 중에도 그대로 쓴다(기다림이 없다).
 */
@Composable
private fun Thumb(entry: FileEntry, size: Dp, allowLoad: Boolean) {
    // **만화는 확장자가 만화라고 적힌 것만이다.** `FileKind.ARCHIVE` 전체로 넓히면
    // 사용자가 연 적도 없는 압축 파일 속 개인 사진이 320px JPEG 으로 앱 저장소에
    // 영속된다(`ThumbnailStore.Kind.COMIC` 의 같은 주석 참고).
    val showThumb = entry.kind == FileKind.IMAGE || entry.kind == FileKind.VIDEO ||
        entry.kind == FileKind.COMIC
    if (!showThumb) {
        KindBadge(
            kind = entry.kind,
            extension = MimeResolver.extensionOf(entry.name),
            size = size,
            locked = entry.isLocked,
        )
        return
    }

    val context = LocalContext.current
    val key = remember(entry.path, entry.size, entry.lastModified) {
        ThumbnailStore.keyOf(entry.path, entry.size, entry.lastModified)
    }
    // 메모리에 있으면 **첫 프레임에** 그린다. 있는 것을 비동기로 기다리면 되돌아
    // 스크롤할 때 칸이 한 번 비었다 찬다.
    val bitmap by produceState(ThumbnailStore.peek(key), key, allowLoad) {
        if (value == null && allowLoad) {
            value = ThumbnailStore.get(
                context = context,
                path = entry.path,
                key = key,
                kind = when (entry.kind) {
                    FileKind.VIDEO -> ThumbnailStore.Kind.VIDEO
                    FileKind.COMIC -> ThumbnailStore.Kind.COMIC
                    else -> ThumbnailStore.Kind.IMAGE
                },
                // `.nomedia` 폴더와 휴지통에서는 디스크에 남기지 않는다. core:ui 는
                // core:io 를 볼 수 없어 그 판정을 못 하므로 여기서 넘긴다.
                persist = !PathRules.isNoMediaOrTrash(entry.path),
            )
        }
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)),
            )
            // 동영상은 그림만으로 사진과 구별되지 않는다. 재생 표시를 겹친다.
            if (entry.kind == FileKind.VIDEO) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        } else {
            KindBadge(
                kind = entry.kind,
                extension = MimeResolver.extensionOf(entry.name),
                size = size,
                locked = entry.isLocked,
            )
        }
    }
}
