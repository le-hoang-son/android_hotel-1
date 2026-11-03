package com.lumina.hotel.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.ui.admin.AdminMainActivity;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.SessionManager;

/**
 * SPEC 00 — Màn khởi động.
 * - Mở CSDL trên luồng nền (lần đầu chạy, DatabaseHelper.onCreate sẽ tự seed dữ liệu mẫu).
 * - Chờ đủ 1800ms rồi điều hướng theo phiên đăng nhập.
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long SPLASH_DELAY_MS = 1800;
    private static final long ANIM_DURATION_MS = 600;
    private static final float ANIM_OFFSET_DP = 12f;

    private LinearLayout llBrand;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nền gradient tràn dưới status bar/nav bar, biểu tượng hệ thống màu sáng
        int transparent = ContextCompat.getColor(this, R.color.transparent);
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(transparent),
                SystemBarStyle.dark(transparent));

        setContentView(R.layout.activity_splash);
        llBrand = findViewById(R.id.llBrand);

        blockBackButton();
        playIntroAnimation();
        prepareDataThenNavigate();
    }

    /** Chặn nút Back trong lúc Splash. */
    private void blockBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // cố ý để trống
            }
        });
    }

    /** Logo + chữ: fade-in và dịch lên 12dp trong 600ms. */
    private void playIntroAnimation() {
        float offsetPx = ANIM_OFFSET_DP * getResources().getDisplayMetrics().density;

        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        TranslateAnimation slide = new TranslateAnimation(0f, 0f, offsetPx, 0f);

        AnimationSet set = new AnimationSet(true);
        set.addAnimation(fade);
        set.addAnimation(slide);
        set.setDuration(ANIM_DURATION_MS);

        llBrand.startAnimation(set);
    }

    /**
     * Mở CSDL trên luồng nền (kích hoạt seed nếu là lần chạy đầu),
     * rồi chờ cho đủ 1800ms tính từ lúc bắt đầu trước khi chuyển màn.
     */
    private void prepareDataThenNavigate() {
        final long start = SystemClock.elapsedRealtime();

        AppExecutors.io(() -> {
            try {
                long count = new RoomTypeDao(this).countTypes();
                Log.d(TAG, "Số loại phòng trong CSDL: " + count);
            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi mở/seed CSDL", e);
            }

            long elapsed = SystemClock.elapsedRealtime() - start;
            long remaining = Math.max(0, SPLASH_DELAY_MS - elapsed);

            AppExecutors.runOnUiSafe(this,
                    () -> handler.postDelayed(this::navigateNext, remaining));
        });
    }

    /** Chưa đăng nhập → Login · admin → AdminMain · còn lại → Main. */
    private void navigateNext() {
        SessionManager session = new SessionManager(this);

        Intent intent;
        if (session.getUserId() == SessionManager.NO_USER) {
            intent = new Intent(this, LoginActivity.class);
        } else if (session.isAdmin()) {
            intent = new Intent(this, AdminMainActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
