package io.github.donggi.project04;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

public class Main4Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startActivity(new Intent(Main4Activity.this, MainActivity.class));
    }
}
