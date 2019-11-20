package io.github.donggi.project04;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class Main3Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        findViewById(R.id.button2).setOnClickListener(new ButtonOnClickListener());
        findViewById(R.id.button3).setOnClickListener(v -> Toast.makeText(Main3Activity.this, "Button 3", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startActivity(new Intent(Main3Activity.this, Main4Activity.class));
    }


    public void button1_Click(View view) {
        Toast.makeText(this, "Button 1", Toast.LENGTH_SHORT).show();
    }

    private static class ButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Toast.makeText(v.getContext(), "Button 2", Toast.LENGTH_SHORT).show();
        }
    }
}
