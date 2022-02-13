package io.github.donggi.project02;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void sendNotification(View v) {
        final int NOTIFICATION_ID = 1111;
        final String NOTIFICATION_CHANNEL_ID = "Notification from MainActivity";

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Example Notifications", NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableVibration(false);
            notificationManager.createNotificationChannel(notificationChannel);
            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(R.drawable.ic_launcher_background).setContentTitle("Title").setContentText("Text");
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

}
