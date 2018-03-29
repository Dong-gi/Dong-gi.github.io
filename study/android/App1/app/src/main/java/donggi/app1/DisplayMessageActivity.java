package donggi.app1;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class DisplayMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_message);

        ((TextView)findViewById(R.id.textView)).setText(getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE));
    }
}
