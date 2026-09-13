package io.github.donggi.iroiroviewer.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.ui.image.ExifReader
import io.github.donggi.iroiroviewer.ui.image.ExifSummary
import java.io.File

/**
 * 사진 정보.
 *
 * **위치와 일련번호는 기본으로 접어 둔다.** 이 앱은 개인용이지만 화면을 남에게 보여 주는
 * 일은 흔하고, 좌표는 집 주소이기도 하다. 보고 싶으면 한 번 더 누르면 된다 —
 * 그 한 번이 '실수로 보이는 것' 과 '보기로 한 것' 을 가른다.
 *
 * 지도 앱으로 보내는 단추를 두지 않는다. 좌표를 다른 앱에 넘기는 가장 쉬운 길이다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifSheet(file: File, onDismiss: () -> Unit) {
    val summary by produceState<ExifSummary?>(null, file.absolutePath) {
        value = ExifReader.read(file)
    }
    var revealSensitive by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            InfoRow(stringResource(R.string.image_exif_size), Format.size(file.length()))
            InfoRow(stringResource(R.string.image_exif_modified), Format.timestamp(file.lastModified()))

            val s = summary
            if (s != null) {
                if (s.pixelWidth > 0 && s.pixelHeight > 0) {
                    InfoRow(
                        stringResource(R.string.image_exif_pixels),
                        stringResource(R.string.image_exif_pixels_value, s.pixelWidth, s.pixelHeight),
                    )
                }
                s.takenAt?.let { InfoRow(stringResource(R.string.image_exif_taken), it) }
                listOfNotNull(s.cameraMake, s.cameraModel).takeIf { it.isNotEmpty() }?.let {
                    InfoRow(stringResource(R.string.image_exif_camera), it.joinToString(" "))
                }
                s.lens?.let { InfoRow(stringResource(R.string.image_exif_lens), it) }
                s.exposure?.let { InfoRow(stringResource(R.string.image_exif_exposure), it) }
                s.aperture?.let { InfoRow(stringResource(R.string.image_exif_aperture), it) }
                s.iso?.let { InfoRow(stringResource(R.string.image_exif_iso), it) }
                s.focalLength?.let { InfoRow(stringResource(R.string.image_exif_focal), it) }

                val hasSensitive = s.gps != null || s.bodySerial != null || s.owner != null
                if (hasSensitive) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    if (!revealSensitive) {
                        Text(
                            text = stringResource(R.string.image_exif_reveal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { revealSensitive = true }
                                .padding(vertical = 8.dp),
                        )
                    } else {
                        s.gps?.let { (lat, lon) ->
                            InfoRow(
                                stringResource(R.string.image_exif_gps),
                                stringResource(R.string.image_exif_gps_value, lat, lon),
                            )
                        }
                        s.bodySerial?.let { InfoRow(stringResource(R.string.image_exif_serial), it) }
                        s.owner?.let { InfoRow(stringResource(R.string.image_exif_owner), it) }
                    }
                }
            }

            InfoRow(stringResource(R.string.image_exif_path), file.parent.orEmpty())
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
