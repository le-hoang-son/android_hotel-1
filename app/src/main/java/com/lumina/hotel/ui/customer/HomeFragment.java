package com.lumina.hotel.ui.customer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.RecentViewDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.data.dao.PromotionDao;
import com.lumina.hotel.model.Promotion;

import java.text.SimpleDateFormat;
import com.lumina.hotel.databinding.FragmentHomeBinding;
import com.lumina.hotel.databinding.ItemRoomTypeCardVerticalBinding;
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

    private FragmentHomeBinding binding;
    private RoomTypeDao roomTypeDao;
    private RecentViewDao recentViewDao;
    private SessionManager sessionManager;
    private PromotionDao promotionDao;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        roomTypeDao = new RoomTypeDao(requireContext());
        recentViewDao = new RecentViewDao(requireContext());
        sessionManager = new SessionManager(requireContext());
        promotionDao = new PromotionDao(requireContext());

        setupHeader();
        setupClickListeners();

        binding.srlHome.setOnRefreshListener(this::loadAllData);
        loadAllData();
    }

    private void setupHeader() {
        binding.tvGreeting.setText(getGreetingByHour());
        String fullName = sessionManager.getFullName();
        binding.tvUserName.setText(fullName + " ✦");
        binding.ivAvatar.setText(getInitial(fullName));
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
        binding.layoutSearchBox.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchTab(1); // tab Tìm kiếm
            }
        });
        binding.btnSeeAllFeatured.setOnClickListener(v -> showNotReadySnackbar());
        binding.btnSeeAllRecent.setOnClickListener(v -> showNotReadySnackbar());
    }

    private void openRoomDetail(RoomType item) {
        Intent intent = new Intent(requireContext(), RoomDetailActivity.class);
        intent.putExtra(IntentKeys.EXTRA_ROOM_TYPE_ID, item.getId());
        startActivity(intent);
    }

    private void showNotReadySnackbar() {
        Snackbar.make(binding.getRoot(), R.string.feature_not_ready, Snackbar.LENGTH_SHORT).show();
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
                bindRoomCards(binding.containerFeatured, data);
                binding.srlHome.setRefreshing(false);
            });
        });
    }

    private void loadRecent() {
        int userId = sessionManager.getUserId();
        AppExecutors.io(() -> {
            List<RoomType> data = recentViewDao.getRecent(userId, 8);
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                bindRoomCards(binding.containerRecent, data);
                binding.layoutRecentSection.setVisibility(
                        data.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    /** Tạo các card phòng và gắn vào container ngang (thay cho RecyclerView). */
    private void bindRoomCards(LinearLayout container, List<RoomType> data) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (RoomType item : data) {
            ItemRoomTypeCardVerticalBinding b =
                    ItemRoomTypeCardVerticalBinding.inflate(inflater, container, false);

            Glide.with(b.getRoot())
                    .load(item.getImageUrl())
                    .centerCrop()
                    .into(b.ivRoomImage);

            b.tvTypeName.setText(item.getTypeName());
            b.tvMeta.setText(getString(R.string.room_meta_format,
                    item.getAreaM2(), item.getMaxGuests()));
            b.tvRating.setText(String.format(Locale.getDefault(), "%.1f", item.getAvgRating()));
            b.tvReviewCount.setText(getString(R.string.review_count_format,
                    item.getReviewCount()));
            b.tvPrice.setText(PriceUtils.formatVnd(item.getBasePrice()));
            b.tvOldPrice.setVisibility(View.GONE);

            boolean isHot = item.getAvgRating() >= 4.9f;
            b.tvBadge.setText(R.string.badge_hot);
            b.tvBadge.setVisibility(isHot ? View.VISIBLE : View.GONE);

            b.getRoot().setOnClickListener(v -> openRoomDetail(item));
            container.addView(b.getRoot());
        }
    }

    private void loadPromo() {
        AppExecutors.io(() -> {
            Promotion promo = promotionDao.getCurrentBanner(System.currentTimeMillis());
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                if (promo == null) {
                    binding.cvPromo.setVisibility(View.GONE);
                    return;
                }
                binding.cvPromo.setVisibility(View.VISIBLE);
                binding.tvPromoLabel.setText(
                        getString(R.string.promo_label_format, promo.getDiscountPercent()));
                binding.tvPromoTitle.setText(promo.getTitle());
                String expireStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(new java.util.Date(promo.getEndDate()));
                binding.tvPromoExpire.setText(
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
        binding = null;
    }
}