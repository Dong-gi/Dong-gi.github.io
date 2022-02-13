package io.github.donggi.project03;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    final String TAG = MainActivity.class.getCanonicalName();
    final int REQUEST_CODE_FOR_CAMERA_PERMISSION = 13579;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
    }

    private void checkPermission() {
        // ContextCompat.checkSelfPermission()은 sdk 23부터 도입되었지만, 이전 버전에서도 잘 작동(권한 있음으로 리턴)한다고 함.
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "이미 권한 있음");
            return;
        }

        Log.d(TAG, "권한이 없으므로 접근 권한 안내");
        new AlertDialog.Builder(MainActivity.this)
                .setMessage("이러저러한 이유로 카메라 권한이 필요합니다")
                .setPositiveButton("확인", (x, y) -> requestPermission())
                .setOnCancelListener((x) -> requestPermission())
                .create()
                .show();
    }

    private void requestPermission() {
        Log.d(TAG, "권한 요청 시도 : 권한 요청 대화 시스템 메시지는 개발자가 변경할 수 없음. 사용자의 로캐일에 따라 알아서 언어가 변경됨.");
        ActivityCompat.requestPermissions(
                MainActivity.this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CODE_FOR_CAMERA_PERMISSION
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_FOR_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "권한 감사합니다", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "권한 거부됨. 사용자가 직접 부여해야 함.");
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("권한 부족으로 불안정 할 수 있으니 권한 주세요 ㅠㅠ")
                        .setPositiveButton("설정", (x, y) ->
                                startActivity(new Intent()
                                        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", getPackageName(), null))))
                        .setNegativeButton("싫어", (x, y) -> {
                        })
                        .create()
                        .show();
            }
        }
    }
}
