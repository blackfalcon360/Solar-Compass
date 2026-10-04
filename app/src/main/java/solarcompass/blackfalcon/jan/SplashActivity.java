package solarcompass.blackfalcon.jan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Launching screen: falcon emoji + "Brought to you By: Black Falcon". */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = () -> {
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);

        TextView falcon = new TextView(this);
        falcon.setText("\uD83E\uDD85"); // eagle/falcon emoji
        falcon.setTextSize(110);
        falcon.setGravity(Gravity.CENTER);
        column.addView(falcon);

        TextView brought = new TextView(this);
        brought.setText("Brought to you");
        brought.setTextColor(0xFF9AA0A6);
        brought.setTextSize(16);
        brought.setGravity(Gravity.CENTER);
        brought.setPadding(0, dp(12), 0, dp(4));
        column.addView(brought);

        TextView by = new TextView(this);
        by.setText("By: Black Falcon");
        by.setTextColor(0xFFC9A227);
        by.setTextSize(28);
        by.setTypeface(Typeface.DEFAULT_BOLD);
        by.setGravity(Gravity.CENTER);
        column.addView(by);

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        column.setAlpha(0f);
        column.animate().alpha(1f).setDuration(900).start();
        handler.postDelayed(next, 2200);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(next);
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
