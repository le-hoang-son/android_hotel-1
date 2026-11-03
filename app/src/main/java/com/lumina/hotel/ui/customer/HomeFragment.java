package com.lumina.hotel.ui.customer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.RecentViewDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.data.dao.PromotionDao;
import com.lumina.hotel.model.Promotion;

import java.text.SimpleDateFormat;
import com.lumina.hotel.model.RoomType;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.SessionManager;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.content.Intent;
import com.lumina.hotel.model.IntentKeys;
/** SPEC 03 — Trang chủ. */
public class HomeFragment extends Fragment {

    private View rootView;
    private SwipeRefreshLayout srlHome;
    private TextView tvGreeting;
    private TextView tvUserName;
    private TextView ivAvatar;
    private View layoutSearchBox;
    private View btnSeeAllFeatured;
    private View btnSeeAllRecent;
    private TextView tvPromoLabel;
    private TextView tvPromoTitle;
    private TextView tvPromoExpire;
    private View cvPromo;
    private LinearLayout containerFeatured;
    private LinearLayout containerRecent;
    private View layoutRecentSection;

    private RoomTypeDao roomTypeDao;
    private RecentViewDao recentViewDao;
    private SessionManager sessionManager;
    private PromotionDao promotionDao;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        srlHome = view.findViewById(R.id.srlHome);
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvUserName = view.findViewById(R.id.tvUserName);
        ivAvatar = view.findViewById(R.id.ivAvatar);
        layoutSearchBox = view.findViewById(R.id.layoutSearchBox);
        btnSeeAllFeatured = view.findViewById(R.id.btnSeeAllFeatured);
        btnSeeAllRecent = view.findViewById(R.id.btnSeeAllRecent);
        tvPromoLabel = view.findViewById(R.id.tvPromoLabel);
        tvPromoTitle = view.findViewById(R.id.tvPromoTitle);
        tvPromoExpire = view.findViewById(R.id.tvPromoExpire);
        cvPromo = view.findViewById(R.id.cvPromo);
        containerFeatured = view.findViewById(R.id.containerFeatured);
        containerRecent = view.findViewById(R.id.containerRecent);
        layoutRecentSection = view.findViewById(R.id.layoutRecentSection);

        roomTypeDao = new RoomTypeDao(requireContext());
        recentViewDao = new RecentViewDao(requireContext());
        sessionManager = new SessionManager(requireContext());
        promotionDao = new PromotionDao(requireContext());

        setupHeader();
        setupClickListeners();

        srlHome.setOnRefreshListener(this::loadAllData);
        loadAllData();
    }

    private void setupHeader() {
        tvGreeting.setText(getGreetingByHour());
        String fullName = sessionManager.getFullName();
        tvUserName.setText(fullName + " ✦");
        ivAvatar.setText(getInitial(fullName));
    }

    private String getGreetingByHour() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 11) return getString(R.string.greeting_morning);
        if (hour < 17) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }

    private String getInitial(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private void setupClickListeners() {
        layoutSearchBox.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchTab(1); // tab Tìm kiếm
            }
        });
        btnSeeAllFeatured.setOnClickListener(v -> showNotReadySnackbar());
        btnSeeAllRecent.setOnClickListener(v -> showNotReadySnackbar());
    }

    private void openRoomDetail(RoomType item) {
        Intent intent = new Intent(requireContext(), RoomDetailActivity.class);
        intent.putExtra(IntentKeys.EXTRA_ROOM_TYPE_ID, item.getId());
        startActivity(intent);
    }

    private void showNotReadySnackbar() {
        Snackbar.make(rootView, R.string.feature_not_ready, Snackbar.LENGTH_SHORT).show();
    }

    private void loadAllData() {
        loadFeatured();
        loadRecent();
        loadPromo();
    }

    private void loadFeatured() {
        AppExecutors.io(() -> {
            List<RoomType> data = roomTypeDao.getFeatured(6);
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                bindRoomCards(containerFeatured, data);
                srlHome.setRefreshing(false);
            });
        });
    }

    private void loadRecent() {
        int userId = sessionManager.getUserId();
        AppExecutors.io(() -> {
            List<RoomType> data = recentViewDao.getRecent(userId, 8);
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                bindRoomCards(containerRecent, data);
                layoutRecentSection.setVisibility(
                        data.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    /** Tạo các card phòng và gắn vào container ngang (thay cho RecyclerView). */
    private void bindRoomCards(LinearLayout container, List<RoomType> data) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (RoomType item : data) {
            View card = inflater.inflate(R.layout.item_room_type_card_vertical, container, false);

            ImageView ivRoomImage = card.findViewById(R.id.ivRoomImage);
            TextView tvTypeName = card.findViewById(R.id.tvTypeName);
            TextView tvMeta = card.findViewById(R.id.tvMeta);
            TextView tvRating = card.findViewById(R.id.tvRating);
            TextView tvReviewCount = card.findViewById(R.id.tvReviewCount);
            TextView tvPrice = card.findViewById(R.id.tvPrice);
            TextView tvOldPrice = card.findViewById(R.id.tvOldPrice);
            TextView tvBadge = card.findViewById(R.id.tvBadge);

            Glide.with(card)
                    .load(item.getImageUrl())
                    .centerCrop()
                    .into(ivRoomImage);

            tvTypeName.setText(item.getTypeName());
            tvMeta.setText(getString(R.string.room_meta_format,
                    item.getAreaM2(), item.getMaxGuests()));
            tvRating.setText(String.format(Locale.getDefault(), "%.1f", item.getAvgRating()));
            tvReviewCount.setText(getString(R.string.review_count_format,
                    item.getReviewCount()));
            tvPrice.setText(PriceUtils.formatVnd(item.getBasePrice()));
            tvOldPrice.setVisibility(View.GONE);

            boolean isHot = item.getAvgRating() >= 4.9f;
            tvBadge.setText(R.string.badge_hot);
            tvBadge.setVisibility(isHot ? View.VISIBLE : View.GONE);

            card.setOnClickListener(v -> openRoomDetail(item));
            container.addView(card);
        }
    }

    private void loadPromo() {
        AppExecutors.io(() -> {
            Promotion promo = promotionDao.getCurrentBanner(System.currentTimeMillis());
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                if (promo == null) {
                    cvPromo.setVisibility(View.GONE);
                    return;
                }
                cvPromo.setVisibility(View.VISIBLE);
                tvPromoLabel.setText(
                        getString(R.string.promo_label_format, promo.getDiscountPercent()));
                tvPromoTitle.setText(promo.getTitle());
                String expireStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(new java.util.Date(promo.getEndDate()));
                tvPromoExpire.setText(
                        getString(R.string.promo_expire_format, expireStr));
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Khách có thể vừa xem chi tiết 1 phòng xong quay lại → làm mới "Đã xem gần đây".
        loadRecent();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
        srlHome = null;
        tvGreeting = null;
        tvUserName = null;
        ivAvatar = null;
        layoutSearchBox = null;
        btnSeeAllFeatured = null;
        btnSeeAllRecent = null;
        tvPromoLabel = null;
        tvPromoTitle = null;
        tvPromoExpire = null;
        cvPromo = null;
        containerFeatured = null;
        containerRecent = null;
        layoutRecentSection = null;
    }
}
