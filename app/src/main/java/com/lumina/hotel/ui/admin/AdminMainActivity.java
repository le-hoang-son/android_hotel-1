package com.lumina.hotel.ui.admin;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lumina.hotel.R;

public class AdminMainActivity extends AppCompatActivity {

    private BottomNavigationView bnvAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_admin_main);
        bnvAdmin = findViewById(R.id.bnvAdmin);

        // Header navy của các tab tự cộng chiều cao status bar (xem setupHeaderInset ở từng Fragment),
        // nên Activity KHÔNG được padding top nữa, nếu không sẽ bị cộng đôi.
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            bnvAdmin.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        // Header navy chạy lên sát mép trên → icon thanh trạng thái phải màu sáng
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        if (savedInstanceState == null) switchTab(R.id.nav_admin_dashboard);

        bnvAdmin.setOnItemSelectedListener(item -> {
            switchTab(item.getItemId());
            return true;
        });
    }

    /** Chuyển tab từ code (vd nút "Xem tất cả" ở Dashboard): tự cập nhật luôn tab đang sáng ở bottom nav. */
    public void openTab(int itemId) {
        bnvAdmin.setSelectedItemId(itemId); // kích hoạt listener ở trên → gọi switchTab()
    }

    public void switchTab(int itemId) {
        Fragment f;
        if (itemId == R.id.nav_admin_dashboard) f = new AdminDashboardFragment();
        else if (itemId == R.id.nav_admin_room_type) f = new AdminRoomTypeFragment();
        else if (itemId == R.id.nav_admin_room) f = new AdminRoomFragment();
        else if (itemId == R.id.nav_admin_booking) f = new AdminBookingFragment();
        else f = new AdminCustomerFragment();

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.frameAdminContainer, f);
        tx.commit();
    }
}
