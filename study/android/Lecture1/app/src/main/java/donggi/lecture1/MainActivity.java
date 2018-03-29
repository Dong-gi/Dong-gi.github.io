package donggi.lecture1;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private static final String[] EXAMPLE_DESCRYPTIONS;
    private static final Class[] EXAMPLE_ACTIVITIES;

    static {
        EXAMPLE_DESCRYPTIONS = new String[] {
                "test1",
                "test2"
        };
        EXAMPLE_ACTIVITIES = new Class[] {
                MainActivity.class,
                MainActivity.class
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);
        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return EXAMPLE_ACTIVITIES.length;
            }
            @Override
            public Class getItem(int position) {
                return EXAMPLE_ACTIVITIES[position];
            }
            @Override
            public long getItemId(int position) {
                return position;
            }
            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                final Context context = parent.getContext();
                if(convertView == null) {
                    LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = inflater.inflate(R.layout.item_example_list, parent, false);
                }
                ((TextView)convertView.findViewById(R.id.textView)).setText(EXAMPLE_DESCRYPTIONS[position]);
                ((Button)convertView.findViewById(R.id.button)).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, EXAMPLE_ACTIVITIES[position]));
                    }
                });
                return convertView;
            }
        });
    }

}
