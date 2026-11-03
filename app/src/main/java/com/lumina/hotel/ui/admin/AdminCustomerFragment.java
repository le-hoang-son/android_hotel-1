package com.lumina.hotel.ui.admin;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.CustomerAdapter;
import com.lumina.hotel.data.dao.UserDao;
import com.lumina.hotel.model.CustomerFilter;
import com.lumina.hotel.model.CustomerStat;
import com.lumina.hotel.util.AppExecutors;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SPEC 12 — Admin · Quản lý Khách hàng.
 * Danh sách khách + tìm kiếm realtime (debounce 300ms) + bộ lọc nâng cao (Phụ lục F):
 * lịch sử đặt phòng, hạng VIP, khoảng ngày đăng ký. Các nhóm kết hợp AND với từ khoá.
 */
public class AdminCustomerFragment extends Fragment
        implements CustomerFilterBottomSheet.OnFilterAppliedListener {

    private View rootView;
    private TextView tvAdminLabel;
    private ImageButton btnFilter;
    private EditText edtSearch;
    private ImageButton btnClearSearch;
    private ChipGroup cgActiveFilters;
    private View svActiveFilters;
    private View cvFilterBadge;
    private ProgressBar pbLoading;
    private View layoutEmpty;
    private TextView tvCustomerCount;
    private TextView tvEmptyMessage;
    private ListView lvCustomers;

    private UserDao userDao;
    private CustomerAdapter adapter;
    private CustomerFilter filter = new CustomerFilter();
    private int labelBaseMarginTop; // lề trên gốc (20dp) của tvAdminLabel, chưa cộng status bar

    /** Tăng mỗi lần tải; kết quả của lần tải cũ (đến muộn) bị bỏ qua. */
    private int loadSeq = 0;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_admin_customer, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvAdminLabel = view.findViewById(R.id.tvAdminLabel);
        btnFilter = view.findViewById(R.id.btnFilter);
        edtSearch = view.findViewById(R.id.edtSearch);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        cgActiveFilters = view.findViewById(R.id.cgActiveFilters);
        svActiveFilters = view.findViewById(R.id.svActiveFilters);
        cvFilterBadge = view.findViewById(R.id.cvFilterBadge);
        pbLoading = view.findViewById(R.id.pbLoading);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        tvCustomerCount = view.findViewById(R.id.tvCustomerCount);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);
        lvCustomers = view.findViewById(R.id.lvCustomers);

        userDao = new UserDao(requireContext());
        filter = CustomerFilter.load(requireContext());
        setupHeaderInset();

        adapter = new CustomerAdapter(this::showCustomerDetail);
        lvCustomers.setAdapter(adapter);

        btnFilter.setOnClickListener(v -> openFilterSheet());

        setupSearch();
        renderActiveFilters();
        loadCustomers(currentKeyword());
    }

    // ---------------- HEADER INSET ----------------

    /**
     * App edge-to-edge: Activity không còn padding top, nên cộng chiều cao status bar vào
     * lề trên của tvAdminLabel. Nền navy (viewHeaderBg) neo từ mép trên xuống nên tự phủ kín.
     */
    private void setupHeaderInset() {
        ViewGroup.MarginLayoutParams base =
                (ViewGroup.MarginLayoutParams) tvAdminLabel.getLayoutParams();
        labelBaseMarginTop = base.topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            if (rootView == null) return insets;
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) tvAdminLabel.getLayoutParams();
            lp.topMargin = labelBaseMarginTop + sb.top;
            tvAdminLabel.setLayoutParams(lp);
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    // ---------------- TÌM KIẾM ----------------

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(
                        s.length() > 0 ? View.VISIBLE : View.GONE);

                if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
                String keyword = s.toString();
                pendingSearch = () -> loadCustomers(keyword);
                searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        btnClearSearch.setOnClickListener(v -> edtSearch.setText(""));
    }

    private String currentKeyword() {
        return edtSearch != null ? edtSearch.getText().toString() : "";
    }

    // ---------------- BỘ LỌC NÂNG CAO ----------------

    private void openFilterSheet() {
        if (getChildFragmentManager().findFragmentByTag(CustomerFilterBottomSheet.TAG) != null) return;
        CustomerFilterBottomSheet.newInstance(filter)
                .show(getChildFragmentManager(), CustomerFilterBottomSheet.TAG);
    }

    /** Callback từ CustomerFilterBottomSheet (sheet đã tự lưu SharedPreferences). */
    @Override
    public void onFilterApplied(CustomerFilter newFilter) {
        filter = newFilter;
        if (rootView == null) return;
        renderActiveFilters();
        loadCustomers(currentKeyword());
    }

    /** Sau khi gỡ 1 điều kiện bằng nút x: lưu lại, vẽ lại chip rồi tải lại danh sách. */
    private void onFilterChanged() {
        filter.save(requireContext());
        renderActiveFilters();
        loadCustomers(currentKeyword());
    }

    /** Vẽ lại hàng chip "đang áp dụng" + chấm vàng trên icon phễu. */
    private void renderActiveFilters() {
        boolean active = filter.isActive();
        cgActiveFilters.removeAllViews();
        svActiveFilters.setVisibility(active ? View.VISIBLE : View.GONE);
        cvFilterBadge.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active) return;

        // Chip lịch sử đặt phòng
        if (!filter.getHistory().isEmpty()) {
            String label = getString(CustomerFilterBottomSheet.historyLabelRes(filter.getHistory()));
            addActiveChip(label, () -> {
                filter.setHistory(CustomerFilter.HISTORY_ANY);
                onFilterChanged();
            });
        }

        // Chip hạng khách
        if (!filter.getVip().isEmpty()) {
            String label = getString(CustomerFilterBottomSheet.vipLabelRes(filter.getVip()));
            addActiveChip(label, () -> {
                filter.setVip(CustomerFilter.VIP_ANY);
                onFilterChanged();
            });
        }

        // Chip khoảng ngày đăng ký (cả khoảng / chỉ Từ / chỉ Đến)
        if (filter.hasDateRange()) {
            long from = filter.getDateFrom();
            long to = filter.getDateTo();
            String label;
            if (from > 0 && to > 0) {
                label = getString(R.string.admin_booking_filter_date_range_format,
                        formatDate(from), formatDate(to));
            } else if (from > 0) {
                label = getString(R.string.admin_booking_filter_date_from_format, formatDate(from));
            } else {
                label = getString(R.string.admin_booking_filter_date_to_format, formatDate(to));
            }
            addActiveChip(label, () -> {
                filter.clearDateRange();
                onFilterChanged();
            });
        }
    }

    private void addActiveChip(String label, Runnable onRemove) {
        Chip chip = (Chip) getLayoutInflater()
                .inflate(R.layout.chip_filter_option, cgActiveFilters, false);
        chip.setId(View.generateViewId());
        chip.setCheckable(false);
        chip.setText(label);
        chip.setCloseIconVisible(true);
        chip.setCloseIconContentDescription(getString(R.string.admin_room_filter_cd_remove, label));
        chip.setOnCloseIconClickListener(v -> onRemove.run());
        cgActiveFilters.addView(chip);
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(millis));
    }

    // ---------------- TẢI DANH SÁCH ----------------

    private void loadCustomers(@Nullable String keyword) {
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        final int seq = ++loadSeq;
        final CustomerFilter f = filter.copy();
        final boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        final boolean narrowing = f.isActive() || hasKeyword;

        AppExecutors.io(() -> {
            List<CustomerStat> data = userDao.getCustomerStats(keyword, f);
            // Khi không lọc/tìm thì hiện tổng khách; ngược lại hiện số kết quả.
            int count = narrowing ? data.size() : userDao.countCustomers();

            Activity activity = getActivity();
            if (activity == null) return; // Fragment đã bị huỷ trong lúc chờ

            activity.runOnUiThread(() -> {
                if (rootView == null || seq != loadSeq) return; // view đã huỷ / có lần tải mới hơn

                pbLoading.setVisibility(View.GONE);
                tvCustomerCount.setText(
                        getString(R.string.customer_count_format, count));

                if (data.isEmpty()) {
                    if (f.isActive()) {
                        tvEmptyMessage.setText(getString(
                                R.string.admin_customer_filter_empty_format,
                                getString(R.string.admin_customer_filter_empty_title),
                                getString(R.string.admin_customer_filter_empty_desc)));
                    } else {
                        tvEmptyMessage.setText(R.string.empty_customer_search);
                    }
                    layoutEmpty.setVisibility(View.VISIBLE);
                    lvCustomers.setVisibility(View.GONE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    lvCustomers.setVisibility(View.VISIBLE);
                    adapter.setData(data);
                }
            });
        });
    }

    private void showCustomerDetail(CustomerStat stat) {
        CustomerDetailBottomSheet.newInstance(stat, () -> {
                    // Đổi VIP có thể làm khách rơi khỏi danh sách đang lọc — hành vi đúng.
                    if (rootView != null) loadCustomers(currentKeyword());
                })
                .show(getParentFragmentManager(), "customer_detail");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        rootView = null;
        tvAdminLabel = null;
        btnFilter = null;
        edtSearch = null;
        btnClearSearch = null;
        cgActiveFilters = null;
        svActiveFilters = null;
        cvFilterBadge = null;
        pbLoading = null;
        layoutEmpty = null;
        tvCustomerCount = null;
        tvEmptyMessage = null;
        lvCustomers = null;
    }
}
