package io.github.donggi.project07.example;

import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionChecker {

    private static final Object LOCK = new Object();
    private static PermissionChecker checker;
    private final Activity activity;

    private PermissionChecker(Activity activity) {
        this.activity = activity;
    }

    public static PermissionChecker getInstance(Activity activity) {
        if (checker != null)
            return checker;
        synchronized (LOCK) {
            checker = new PermissionChecker(activity);
        }
        return checker;
    }

    public boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermission(String dialogMessage, String permission) {
        if (checkPermission(permission))
            return;
        new AlertDialog.Builder(activity)
                .setMessage(dialogMessage)
                .setPositiveButton("확인", (x, y) -> requestPermission(permission))
                .setOnCancelListener((x) -> requestPermission(permission))
                .create()
                .show();
    }

    private void requestPermission(String permission) {
        final int REQUEST_CODE = (short) permission.hashCode();
        ActivityCompat.requestPermissions(
                activity,
                new String[]{permission},
                REQUEST_CODE
        );
    }
}
