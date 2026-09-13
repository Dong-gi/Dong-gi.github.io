package io.github.donggi.iroiroviewer.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/**
 * 모든 파일 접근 권한(MANAGE_EXTERNAL_STORAGE)의 상태와 요청.
 *
 * 이 권한은 런타임 권한이 아니라 '특별 접근' 이라 requestPermissions 로 받을 수 없다.
 * 설정 화면으로 보내고 돌아왔을 때 다시 확인하는 수밖에 없다.
 */
object StorageAccess {

    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    /**
     * 이 앱의 항목이 미리 선택된 설정 화면으로 보낸다. 기기에 따라 그 화면이 없을 수
     * 있어 [Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION] 로 떨어지는 경로를 둔다.
     */
    fun requestIntents(context: Context): List<Intent> = listOf(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        ),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )
}
