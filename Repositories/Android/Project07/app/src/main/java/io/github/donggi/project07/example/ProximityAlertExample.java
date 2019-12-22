package io.github.donggi.project07.example;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

public class ProximityAlertExample extends BroadcastReceiver {
    private static final String TAG = ProximityAlertExample.class.getCanonicalName();

    public ProximityAlertExample(Activity activity) {
        LocationManager manager = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
        PermissionChecker.getInstance(activity).requestPermission("위치 권한 주세요", Manifest.permission.ACCESS_FINE_LOCATION);
        if(!PermissionChecker.getInstance(activity).checkPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            return;
        activity.registerReceiver(this, new IntentFilter(TAG));
        PendingIntent proximityIntent = PendingIntent.getBroadcast(activity, 0, new Intent(TAG), 0);
        manager.addProximityAlert(37.483804,126.882368, 1, -1, proximityIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, String.format("Received : %s", intent));
        boolean isEntering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false);

        if(isEntering)
            Log.d(TAG, "목표 지점에 접근중입니다..");
        else
            Log.d(TAG, "목표 지점에서 벗어납니다..");
    }
    /*
    12-11 18:25:09.070 Received : Intent { act=io.github.donggi.project07.example.ProximityAlertExample flg=0x10 (has extras) }
    12-11 18:25:09.072 목표 지점에 접근중입니다..
     */
}
