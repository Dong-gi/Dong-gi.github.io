package donggi.app1;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE = "donggi.app1.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void sendMessage(View view) {
        startActivity(
                new Intent(this, DisplayMessageActivity.class)
                        .putExtra(EXTRA_MESSAGE, ((EditText)findViewById(R.id.editText)).getText().toString())
        );
    }

}
