package com.lumina.hotel.ui.customer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.lumina.hotel.R;

/**
 * SPEC — ABOUT / GIỚI THIỆU. Màn tĩnh, dữ liệu lấy từ strings.xml, KHÔNG cần CSDL.
 * Vào từ ProfileFragment ("Về Lumina Hotel"). Chỉ 2 hành động: gọi hotline
 * (ACTION_DIAL) và gửi email (ACTION_SENDTO).
 */
public class AboutActivity extends AppCompatActivity {

    // Ảnh bìa khách sạn (Unsplash) — thay bằng ảnh thật của bạn nếu có.
    private static final String COVER_URL =
            "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=1000&q=80";

    private View layoutRoot;
    private View btnBack;
    private ImageView ivCover;
    private View layoutHotline;
    private View layoutEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        layoutRoot = getLayoutInflater().inflate(R.layout.activity_about, null);
        setContentView(layoutRoot);

        btnBack = findViewById(R.id.btnBack);
        ivCover = findViewById(R.id.ivCover);
        layoutHotline = findViewById(R.id.layoutHotline);
        layoutEmail = findViewById(R.id.layoutEmail);

        setupInsets();
        loadCoverImage();
        setupClicks();
    }

    /* ================== INSETS ================== */

    private void setupInsets() {
        // Ảnh bìa tràn edge-to-edge dưới status bar; chỉ nút Back cần đẩy
        // xuống thêm đúng bằng chiều cao status bar để không bị che.
        ViewCompat.setOnApplyWindowInsetsListener(layoutRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) btnBack.getLayoutParams();
            lp.topMargin = bars.top + dp(16);
            btnBack.setLayoutParams(lp);
            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /* ================== ẢNH BÌA ================== */

    private void loadCoverImage() {
        Glide.with(this)
                .load(COVER_URL)
                .placeholder(R.color.surface_variant)
                .error(R.drawable.ic_error)
                .into(ivCover);
    }

    /* ================== CLICK ================== */

    private void setupClicks() {
        btnBack.setOnClickListener(v -> finish());

        layoutHotline.setOnClickListener(v -> {
            String phone = getString(R.string.about_hotline_number).replace(" ", "");
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.no_app_to_handle_action, Toast.LENGTH_SHORT).show();
            }
        });

        layoutEmail.setOnClickListener(v -> {
            String email = getString(R.string.about_email_value);
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.no_app_to_handle_action, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
