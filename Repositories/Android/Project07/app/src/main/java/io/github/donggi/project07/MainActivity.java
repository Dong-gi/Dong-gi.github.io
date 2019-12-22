package io.github.donggi.project07;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import io.github.donggi.project07.example.AccelerometerExample;
import io.github.donggi.project07.example.ListAllSensorsExample;
import io.github.donggi.project07.example.LocationExample;
import io.github.donggi.project07.example.ProximityAlertExample;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new ListAllSensorsExample(this);
        new AccelerometerExample(this);
        new LocationExample(this);
        new ProximityAlertExample(this);
    }

}
