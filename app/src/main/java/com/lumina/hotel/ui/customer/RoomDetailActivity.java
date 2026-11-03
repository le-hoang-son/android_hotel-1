package com.lumina.hotel.ui.customer;

import android.os.Bundle;
import androidx.core.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.FavoriteDao;
import com.lumina.hotel.data.dao.RecentViewDao;
import com.lumina.hotel.data.dao.ReviewDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.model.Amenity;
import com.lumina.hotel.model.IntentKeys;
import com.lumina.hotel.model.Review;
import com.lumina.hotel.model.RoomType;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.SessionManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import android.text.Layout;
import android.text.TextUtils;
import android.content.Intent;

public class RoomDetailActivity extends AppCompatActivity {

    private View rootView;

    private TextView tvRoomName;
    private TextView tvTypeBadge;
    private TextView tvRating;
    private TextView tvReviewCount;
    private TextView tvAreaFloor;
    private TextView tvPrice;
    private TextView tvBottomPrice;
    private TextView tvDescription;
    private TextView tvMaxGuest;
    private TextView btnExpandDesc;
    private ViewPager2 vpImages;
    private LinearLayout layoutDotIndicator;
    private GridLayout glAmenities;
    private TextView tvAvgRating;
    private TextView tvTotalReviews;
    private LinearLayout layoutStars;
    private ProgressBar pbStar5;
    private TextView tvStar5Count;
    private ProgressBar pbStar4;
    private TextView tvStar4Count;
    private ProgressBar pbStar3;
    private TextView tvStar3Count;
    private ProgressBar pbStar2;
    private TextView tvStar2Count;
    private ProgressBar pbStar1;
    private TextView tvStar1Count;
    private LinearLayout llReviews;
    private ImageButton btnFavorite;
    private ImageButton btnBack;
    private MaterialButton btnBookNow;
    private TextView tvInactiveNote;
    private TextView btnSeeAllReviews;
    private NestedScrollView svContent;
    private View layoutTopBar;
    private View layoutBottomBar;

    private RoomTypeDao roomTypeDao;
    private ReviewDao reviewDao;
    private FavoriteDao favoriteDao;
    private RecentViewDao recentViewDao;
    private BookingDao bookingDao;
    private SessionManager sessionManager;

    private int typeId;
    private long checkIn;   // 0 nếu chưa chọn
    private long checkOut;  // 0 nếu chưa chọn
    private int numGuests;

    private RoomType roomType;
    private boolean isFavorite;
    private boolean descExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_detail);
        rootView = findViewById(android.R.id.content);

        tvRoomName = findViewById(R.id.tvRoomName);
        tvTypeBadge = findViewById(R.id.tvTypeBadge);
        tvRating = findViewById(R.id.tvRating);
        tvReviewCount = findViewById(R.id.tvReviewCount);
        tvAreaFloor = findViewById(R.id.tvAreaFloor);
        tvPrice = findViewById(R.id.tvPrice);
        tvBottomPrice = findViewById(R.id.tvBottomPrice);
        tvDescription = findViewById(R.id.tvDescription);
        tvMaxGuest = findViewById(R.id.tvMaxGuest);
        btnExpandDesc = findViewById(R.id.btnExpandDesc);
        vpImages = findViewById(R.id.vpImages);
        layoutDotIndicator = findViewById(R.id.layoutDotIndicator);
        glAmenities = findViewById(R.id.glAmenities);
        tvAvgRating = findViewById(R.id.tvAvgRating);
        tvTotalReviews = findViewById(R.id.tvTotalReviews);
        layoutStars = findViewById(R.id.layoutStars);
        pbStar5 = findViewById(R.id.pbStar5);
        tvStar5Count = findViewById(R.id.tvStar5Count);
        pbStar4 = findViewById(R.id.pbStar4);
        tvStar4Count = findViewById(R.id.tvStar4Count);
        pbStar3 = findViewById(R.id.pbStar3);
        tvStar3Count = findViewById(R.id.tvStar3Count);
        pbStar2 = findViewById(R.id.pbStar2);
        tvStar2Count = findViewById(R.id.tvStar2Count);
        pbStar1 = findViewById(R.id.pbStar1);
        tvStar1Count = findViewById(R.id.tvStar1Count);
        llReviews = findViewById(R.id.llReviews);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnBack = findViewById(R.id.btnBack);
        btnBookNow = findViewById(R.id.btnBookNow);
        tvInactiveNote = findViewById(R.id.tvInactiveNote);
        btnSeeAllReviews = findViewById(R.id.btnSeeAllReviews);
        svContent = findViewById(R.id.svContent);
        layoutTopBar = findViewById(R.id.layoutTopBar);
        layoutBottomBar = findViewById(R.id.layoutBottomBar);

        typeId = getIntent().getIntExtra(IntentKeys.EXTRA_ROOM_TYPE_ID, -1);
        if (typeId <= 0) {
            Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        checkIn = getIntent().getLongExtra(IntentKeys.EXTRA_CHECK_IN, 0L);
        checkOut = getIntent().getLongExtra(IntentKeys.EXTRA_CHECK_OUT, 0L);
        numGuests = getIntent().getIntExtra(IntentKeys.EXTRA_NUM_GUESTS, 0);

        roomTypeDao = new RoomTypeDao(this);
        reviewDao = new ReviewDao(this);
        favoriteDao = new FavoriteDao(this);
        recentViewDao = new RecentViewDao(this);
        bookingDao = new BookingDao(this);
        sessionManager = new SessionManager(this);

        setupInsets();
        setupClicks();
        loadData();
    }

    /* ================== INSETS ================== */

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            layoutTopBar.setPadding(
                    layoutTopBar.getPaddingLeft(),
                    bars.top + dp(8),
                    layoutTopBar.getPaddingRight(),
                    layoutTopBar.getPaddingBottom());

            layoutBottomBar.setPadding(
                    layoutBottomBar.getPaddingLeft(),
                    layoutBottomBar.getPaddingTop(),
                    layoutBottomBar.getPaddingRight(),
                    bars.bottom > 0 ? bars.bottom : layoutBottomBar.getPaddingBottom());

            svContent.setPadding(
                    svContent.getPaddingLeft(),
                    svContent.getPaddingTop(),
                    svContent.getPaddingRight(),
                    dp(88) + bars.bottom);

            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /* ================== TẢI DỮ LIỆU ================== */

    private void loadData() {
        AppExecutors.io(() -> {
            RoomType t = roomTypeDao.getById(typeId);
            if (t == null) {
                AppExecutors.runOnUiSafe(this, () -> {
                    Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            List<Amenity> amenities = roomTypeDao.getAmenitiesOfType(typeId);
            List<Review> reviews = reviewDao.getByRoomType(typeId, 2);
            int[] starCounts = reviewDao.countByStar(typeId); // [0]=5 sao ... [4]=1 sao

            int userId = sessionManager.getUserId();
            boolean fav = sessionManager.isLoggedIn() && favoriteDao.isFavorite(userId, typeId);

            if (sessionManager.isLoggedIn()) {
                recentViewDao.upsert(userId, typeId);
            }

            int overlapping = -1;
            if (checkIn > 0 && checkOut > 0) {
                overlapping = bookingDao.getOverlappingCount(typeId, checkIn, checkOut);
            }
            int overlappingFinal = overlapping;

            AppExecutors.runOnUiSafe(this, () -> {
                roomType = t;
                isFavorite = fav;
                bindRoomInfo(t);
                bindAmenities(amenities);
                bindReviewSummary(t, starCounts);
                bindReviewList(reviews);
                // Chỉ hiện "Xem tất cả" khi còn đánh giá chưa hiển thị (đang chỉ show 2 cái mới nhất).
                btnSeeAllReviews.setVisibility(t.getReviewCount() > 2 ? View.VISIBLE : View.GONE);
                bindFavoriteIcon();
                bindBookButton(t, overlappingFinal);
                setupImages(t);
            });
        });
    }

    /* ================== KHỐI THÔNG TIN PHÒNG ================== */

    private void bindRoomInfo(RoomType t) {
        tvRoomName.setText(t.getTypeName());

        String firstWord = t.getTypeName() == null ? "" : t.getTypeName().trim().split(" ")[0];
        tvTypeBadge.setText(firstWord.toUpperCase(Locale.getDefault()));

        tvRating.setText(String.format(Locale.getDefault(), "%.1f", t.getAvgRating()));
        tvReviewCount.setText(
                getString(R.string.room_detail_review_count_format, t.getReviewCount()));

        tvAreaFloor.setText(getString(R.string.room_detail_area_guests,
                t.getAreaM2(), t.getMaxGuests()));

        tvPrice.setText(PriceUtils.formatVnd(t.getBasePrice()));
        tvBottomPrice.setText(PriceUtils.formatShort(t.getBasePrice()));

        tvDescription.setText(t.getDescription());
        tvMaxGuest.setText(getString(R.string.room_detail_max_guest_format, t.getMaxGuests()));

        setupDescriptionExpand();
    }

    private void setupDescriptionExpand() {
        // Mặc định gập 3 dòng và có dấu "…" (nếu chưa mở rộng)
        tvDescription.setEllipsize(TextUtils.TruncateAt.END);
        tvDescription.setMaxLines(descExpanded ? Integer.MAX_VALUE : 3);
        btnExpandDesc.setText(descExpanded
                ? R.string.room_detail_collapse : R.string.room_detail_expand);

        tvDescription.post(() -> {
            Layout layout = tvDescription.getLayout();
            boolean truncated = layout != null
                    && layout.getLineCount() > 0
                    && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
            btnExpandDesc.setVisibility(truncated || descExpanded ? View.VISIBLE : View.GONE);
        });

        btnExpandDesc.setOnClickListener(v -> {
            descExpanded = !descExpanded;
            tvDescription.setMaxLines(descExpanded ? Integer.MAX_VALUE : 3);
            btnExpandDesc.setText(descExpanded
                    ? R.string.room_detail_collapse : R.string.room_detail_expand);
        });
    }

    /* ================== ẢNH BÌA (1 ảnh — sẵn sàng mở rộng nhiều ảnh sau) ================== */

    private void setupImages(RoomType t) {
        List<String> images = new ArrayList<>();
        if (t.getImageUrl() != null && !t.getImageUrl().isEmpty()) {
            images.add(t.getImageUrl());
        }
        vpImages.setAdapter(new ImagePagerAdapter(images));
        layoutDotIndicator.setVisibility(images.size() > 1 ? View.VISIBLE : View.GONE);
        if (images.size() > 1) setupDots(images.size());
    }

    private void setupDots(int count) {
        layoutDotIndicator.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            int size = dp(6);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    i == 0 ? dp(20) : size, size);
            lp.setMarginStart(dp(3));
            lp.setMarginEnd(dp(3));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0 ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            layoutDotIndicator.addView(dot);
        }
        vpImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < layoutDotIndicator.getChildCount(); i++) {
                    View dot = layoutDotIndicator.getChildAt(i);
                    dot.setBackgroundResource(i == position
                            ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
                }
            }
        });
    }

    private static class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.VH> {
        private final List<String> images;

        ImagePagerAdapter(List<String> images) { this.images = images; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Glide.with(holder.imageView)
                    .load(images.get(position))
                    .placeholder(R.color.surface_variant)
                    .error(R.drawable.ic_error)
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() { return images.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView imageView;
            VH(@NonNull View itemView) {
                super(itemView);
                imageView = (ImageView) itemView;
            }
        }
    }

    /* ================== TIỆN NGHI ================== */

    private void bindAmenities(List<Amenity> amenities) {
        glAmenities.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Amenity a : amenities) {
            View item = inflater.inflate(R.layout.item_amenity, glAmenities, false);
            TextView tvAmenityName = item.findViewById(R.id.tvAmenityName);
            ImageView ivAmenityIcon = item.findViewById(R.id.ivAmenityIcon);
            tvAmenityName.setText(a.getAmenityName());
            ivAmenityIcon.setImageResource(resolveAmenityIcon(a.getIconKey()));

            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            item.setLayoutParams(lp);

            glAmenities.addView(item);
        }
    }

    private int resolveAmenityIcon(String iconKey) {
        if (iconKey == null || iconKey.trim().isEmpty()) return R.drawable.ic_check_circle;
        int resId = getResources().getIdentifier(
                "ic_" + iconKey.trim().toLowerCase(Locale.ROOT), "drawable", getPackageName());
        return resId != 0 ? resId : R.drawable.ic_check_circle;
    }

    /* ================== ĐÁNH GIÁ ================== */

    private void bindReviewSummary(RoomType t, int[] starCounts) {
        tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", t.getAvgRating()));
        tvTotalReviews.setText(
                getString(R.string.room_detail_total_reviews_format, t.getReviewCount()));

        layoutStars.removeAllViews();
        int rounded = Math.round(t.getAvgRating());
        for (int i = 0; i < 5; i++) {
            ImageView star = new ImageView(this);
            int size = dp(14);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(size, size);
            if (i > 0) lp.setMarginStart(dp(2));
            star.setLayoutParams(lp);
            boolean filled = i < rounded;
            star.setImageResource(filled ? R.drawable.ic_star_filled : R.drawable.ic_star);
            star.setColorFilter(getColor(R.color.gold));
            star.setAlpha(filled ? 1f : 0.25f);
            layoutStars.addView(star);
        }

        int total = t.getReviewCount();
        bindStarBar(pbStar5, tvStar5Count, starCounts[0], total);
        bindStarBar(pbStar4, tvStar4Count, starCounts[1], total);
        bindStarBar(pbStar3, tvStar3Count, starCounts[2], total);
        bindStarBar(pbStar2, tvStar2Count, starCounts[3], total);
        bindStarBar(pbStar1, tvStar1Count, starCounts[4], total);
    }

    private void bindStarBar(android.widget.ProgressBar bar, android.widget.TextView countView,
                             int count, int total) {
        int percent = total == 0 ? 0 : Math.round(count * 100f / total);
        bar.setProgress(percent);
        countView.setText(String.valueOf(count));
    }

    /** Vẽ danh sách đánh giá vào LinearLayout (thay cho RecyclerView). */
    private void bindReviewList(List<Review> reviews) {
        llReviews.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Review item : reviews) {
            View b = inflater.inflate(R.layout.item_review, llReviews, false);

            TextView tvReviewerName = b.findViewById(R.id.tvReviewerName);
            TextView tvAvatar = b.findViewById(R.id.tvAvatar);
            TextView tvReviewDate = b.findViewById(R.id.tvReviewDate);
            TextView tvReviewComment = b.findViewById(R.id.tvReviewComment);

            String name = item.getReviewerName() == null ? "" : item.getReviewerName();
            tvReviewerName.setText(name);
            tvAvatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT));
            tvReviewDate.setText(DateUtils.formatMonthYear(item.getCreatedAt()));
            tvReviewComment.setText(item.getComment());

            ImageView[] stars = {
                    b.findViewById(R.id.ivReviewStar1),
                    b.findViewById(R.id.ivReviewStar2),
                    b.findViewById(R.id.ivReviewStar3),
                    b.findViewById(R.id.ivReviewStar4),
                    b.findViewById(R.id.ivReviewStar5)
            };
            int rating = item.getRating();
            for (int i = 0; i < stars.length; i++) {
                boolean filled = i < rating;
                stars[i].setImageResource(filled ? R.drawable.ic_star_filled : R.drawable.ic_star);
                stars[i].setAlpha(filled ? 1f : 0.25f);
            }

            llReviews.addView(b);
        }
    }

    /** Nút "Xem tất cả": nạp hết đánh giá của loại phòng này vào danh sách ngay trong trang. */
    private void expandAllReviews() {
        if (roomType == null) return;
        final int typeId = roomType.getId();
        btnSeeAllReviews.setEnabled(false); // chặn bấm liên tục khi đang tải
        AppExecutors.io(() -> {
            List<Review> all = reviewDao.getAllByRoomType(typeId);
            AppExecutors.runOnUiSafe(this, () -> {
                bindReviewList(all);
                btnSeeAllReviews.setVisibility(View.GONE);
            });
        });
    }

    /* ================== YÊU THÍCH ================== */

    private void bindFavoriteIcon() {
        btnFavorite.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
    }

    private void toggleFavorite() {
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        int userId = sessionManager.getUserId();
        AppExecutors.io(() -> {
            boolean nowFavorite = favoriteDao.toggle(userId, typeId);
            AppExecutors.runOnUiSafe(this, () -> {
                isFavorite = nowFavorite;
                bindFavoriteIcon();
                btnFavorite.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120)
                        .withEndAction(() -> btnFavorite.animate()
                                .scaleX(1f).scaleY(1f).setDuration(120).start())
                        .start();
            });
        });
    }

    /* ================== NÚT ĐẶT PHÒNG ================== */

    private void bindBookButton(RoomType t, int overlapping) {
        if (!t.isActive()) {
            btnBookNow.setEnabled(false);
            btnBookNow.setAlpha(0.4f);
            tvInactiveNote.setVisibility(View.VISIBLE);
            return;
        }
        if (overlapping >= 0 && overlapping >= t.getTotalRooms()) {
            btnBookNow.setEnabled(false);
            btnBookNow.setAlpha(0.4f);
            tvInactiveNote.setVisibility(View.VISIBLE);
            tvInactiveNote.setText(R.string.room_detail_sold_out_note);
            return;
        }
        btnBookNow.setEnabled(true);
        btnBookNow.setAlpha(1f);
        tvInactiveNote.setVisibility(View.GONE);
    }

    private void onBookNowClicked() {
        if (checkIn > 0 && checkOut > 0) {
            openPayment();
            return;
        }
        openDateRangePicker();
    }

    private void openDateRangePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.room_detail_label_checkin)
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            long in = utcSelectionToLocalStartOfDay(selection.first);
            long out = utcSelectionToLocalStartOfDay(selection.second);
            if (out <= in) {
                // Cùng ngày = 0 đêm → không mở thanh toán, và không giữ ngày sai trong biến
                checkIn = 0;
                checkOut = 0;
                Snackbar.make(rootView, R.string.room_detail_min_one_night,
                                Snackbar.LENGTH_LONG)
                        .setAnchorView(layoutBottomBar).show();
                return;
            }
            checkIn = in;
            checkOut = out;
            openPayment();
        });
        picker.show(getSupportFragmentManager(), "date_range_picker");
    }

    private void openPayment() {
        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra(IntentKeys.EXTRA_ROOM_TYPE_ID, typeId);
        intent.putExtra(IntentKeys.EXTRA_CHECK_IN, checkIn);
        intent.putExtra(IntentKeys.EXTRA_CHECK_OUT, checkOut);
        intent.putExtra(IntentKeys.EXTRA_NUM_GUESTS, numGuests > 0 ? numGuests : 1);
        startActivity(intent);
    }

    private long utcSelectionToLocalStartOfDay(long utcMillis) {
        Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcCal.setTimeInMillis(utcMillis);
        return DateUtils.dateOf(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH) + 1,
                utcCal.get(Calendar.DAY_OF_MONTH));
    }

    /* ================== CLICK CHUNG ================== */

    private void setupClicks() {
        btnBack.setOnClickListener(v -> finish());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnBookNow.setOnClickListener(v -> onBookNowClicked());
        btnSeeAllReviews.setOnClickListener(v -> expandAllReviews());
    }
}
