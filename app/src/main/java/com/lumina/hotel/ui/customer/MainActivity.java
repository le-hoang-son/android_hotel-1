package com.lumina.hotel.ui.customer;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lumina.hotel.R;
import com.lumina.hotel.model.IntentKeys;
import android.content.Intent;

/**
 * Host 4 tab khách (BLOCK NAV): Trang chủ · Tìm kiếm · Đơn của tôi · Cá nhân.
 * Nhận EXTRA_TAB_INDEX khi được mở từ màn khác (VD: BookingSuccessActivity
 * mở thẳng tab "Đơn của tôi" = 2 hoặc "Trang chủ" = 0).
 */
public class MainActivity extends AppCompatActivity
        implements MyBookingsFragment.BottomNavHost {
    private static final int[] TAB_IDS = {
            R.id.nav_home, R.id.nav_search, R.id.nav_bookings, R.id.nav_profile
    };

    private View main;
    private BottomNavigationView bnvMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        main = findViewById(R.id.main);
        bnvMain = findViewById(R.id.bnvMain);

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        int requestedIndex = getIntent().getIntExtra(IntentKeys.EXTRA_TAB_INDEX, 0);
        int safeIndex = (requestedIndex >= 0 && requestedIndex < TAB_IDS.length) ? requestedIndex : 0;

        // Đặt tab được chọn TRƯỚC khi gắn listener để tránh add fragment 2 lần
        // (setSelectedItemId có thể hoặc không tự bắn listener tuỳ trạng thái ban đầu).
        bnvMain.setSelectedItemId(TAB_IDS[safeIndex]);
        bnvMain.setOnItemSelectedListener(this::onTabSelected);

        if (savedInstanceState == null) {
            onTabSelected(bnvMain.getMenu().findItem(TAB_IDS[safeIndex]));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        int requestedIndex = intent.getIntExtra(IntentKeys.EXTRA_TAB_INDEX, -1);
        if (requestedIndex >= 0 && requestedIndex < TAB_IDS.length) {
            switchTab(requestedIndex);
        }
    }

    /** Cho Fragment con gọi để chuyển tab bằng code (VD: bấm ô tìm kiếm giả ở Trang chủ). */
    public void switchTab(int index) {
        if (index >= 0 && index < TAB_IDS.length) {
            bnvMain.setSelectedItemId(TAB_IDS[index]);
        }
    }

    @Override
    public void goToSearchTab() {
        switchTab(1); // index 1 = nav_search theo mảng TAB_IDS
    }

    private boolean onTabSelected(@NonNull MenuItem item) {
        Fragment fragment;
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            fragment = new HomeFragment();
        } else if (id == R.id.nav_search) {
            fragment = new SearchFragment();
        } else if (id == R.id.nav_bookings) {
            fragment = new MyBookingsFragment();
        } else if (id == R.id.nav_profile) {
            fragment = new ProfileFragment();
        } else {
            return false;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        return true;
    }


}
