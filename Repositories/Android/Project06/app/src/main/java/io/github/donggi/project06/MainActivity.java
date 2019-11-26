package io.github.donggi.project06;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import io.github.donggi.project06.dao.UserDao;
import io.github.donggi.project06.dto.User;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DB db = DB.getInstance(this);
        new Thread(() -> { // 메인 스레드에서의 쿼리는 금지됨
            UserDao userDao = db.userDao();
            Log.d(TAG, "Count all : " + userDao.selectAll().size());    // 0
            User user = new User() {{
                userId = 1;
                email = "test@test.com";
            }};
            userDao.insert(user);
            Log.d(TAG, "Count all : " + userDao.selectAll().size());    // 1
            Log.d(TAG, "Select : " + userDao.selectByUserIds(1));       // [User(userId=1, email=test@test.com)]
            userDao.delete(user);
            Log.d(TAG, "Count all : " + userDao.selectAll().size());    // 0
        }).start();
    }
}
