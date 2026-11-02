package com.lumina.hotel.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.RoomListAdapter;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.databinding.ActivityRoomListBinding;
import com.lumina.hotel.databinding.BottomSheetSortBinding;
import com.lumina.hotel.model.Amenity;
import com.lumina.hotel.model.IntentKeys;
import com.lumina.hotel.model.RoomType;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RoomListActivity extends AppCompatActivity {

    private ActivityRoomListBinding binding;
    private RoomTypeDao roomTypeDao;
    private BookingDao bookingDao;
    private RoomListAdapter adapter;

    private long checkIn, checkOut;
    private int numGuests;
    private String typeFilter;
    private long priceMin, priceMax;
    private int[] amenityIds;

    /** Kết quả thô sau searchAndFilter, chưa áp bộ lọc nhanh/sắp xếp. */
    private List<RoomType> allRooms = new ArrayList<>();
    private Set<Integer> soldOutIds = new HashSet<>();
    /** typeId -> tên tiện nghi của loại đó, dùng cho chip "Có bữa sáng". */
    private Map<Integer, List<Amenity>> amenityCache = new HashMap<>();

    private enum SortMode { PRICE_ASC, PRICE_DESC, RATING, POPULAR }
    private SortMode currentSort = SortMode.PRICE_ASC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoomListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.layoutHeader.setPadding(0, bars.top, 0, 0);
            binding.lvRoomList.setPadding(
                    getResources().getDimensionPixelSize(R.dimen.space_20),
                    0,
                    getResources().getDimensionPixelSize(R.dimen.space_20),
                    getResources().getDimensionPixelSize(R.dimen.space_24) + bars.bottom);
            return windowInsets;
        });

        roomTypeDao = new RoomTypeDao(this);
        bookingDao = new BookingDao(this);

        readExtras();
        setupToolbar();
        setupListView();
        setupSortChip();
        setupQuickFilters();

        loadRooms();
    }

    private void readExtras() {
        Intent i = getIntent();
        long now = DateUtils.atStartOfDay(System.currentTimeMillis());
        checkIn = i.getLongExtra(IntentKeys.EXTRA_CHECK_IN, now);
        checkOut = i.getLongExtra(IntentKeys.EXTRA_CHECK_OUT, now + DateUtils.ONE_DAY);
        numGuests = i.getIntExtra(IntentKeys.EXTRA_NUM_GUESTS, 2);
        typeFilter = i.getStringExtra(IntentKeys.EXTRA_TYPE_FILTER);
        priceMin = i.getLongExtra(IntentKeys.EXTRA_PRICE_MIN, 0L);
        priceMax = i.getLongExtra(IntentKeys.EXTRA_PRICE_MAX, 0L);
        amenityIds = i.getIntArrayExtra(IntentKeys.EXTRA_AMENITY_IDS);
        if (amenityIds == null) amenityIds = new int[0];
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnEditSearch.setOnClickListener(v -> finish()); // quay lại màn Tìm kiếm

        int nights = DateUtils.nightsBetween(checkIn, checkOut);
        binding.tvDateSummary.setText(getString(R.string.room_list_date_summary_format,
                DateUtils.formatDayMonth(checkIn), DateUtils.formatDayMonth(checkOut), numGuests));
        binding.tvResultSummary.setText(getString(R.string.room_list_result_summary_format,
                nights, 0)); // cập nhật số phòng thật sau khi tải xong
    }

    private void setupListView() {
        adapter = new RoomListAdapter();
        adapter.setOnItemClickListener((item, position) -> {
            Intent intent = new Intent(this, RoomDetailActivity.class);
            intent.putExtra(IntentKeys.EXTRA_ROOM_TYPE_ID, item.getId());
            intent.putExtra(IntentKeys.EXTRA_CHECK_IN, checkIn);
            intent.putExtra(IntentKeys.EXTRA_CHECK_OUT, checkOut);
            intent.putExtra(IntentKeys.EXTRA_NUM_GUESTS, numGuests);
            startActivity(intent);
        });
        binding.lvRoomList.setAdapter(adapter);
    }

    private void setupSortChip() {
        binding.chipSort.setOnClickListener(v -> {
            BottomSheetDialog dialog = new BottomSheetDialog(this);
            BottomSheetSortBinding sheet = BottomSheetSortBinding.inflate(getLayoutInflater());
            dialog.setContentView(sheet.getRoot());

            sheet.rowPriceAsc.setOnClickListener(x -> chooseSort(SortMode.PRICE_ASC, R.string.sort_price_asc, dialog));
            sheet.rowPriceDesc.setOnClickListener(x -> chooseSort(SortMode.PRICE_DESC, R.string.sort_price_desc, dialog));
            sheet.rowRating.setOnClickListener(x -> chooseSort(SortMode.RATING, R.string.sort_rating, dialog));
            sheet.rowPopular.setOnClickListener(x -> chooseSort(SortMode.POPULAR, R.string.sort_popular, dialog));
            dialog.show();
        });
    }

    private void chooseSort(SortMode mode, int labelRes, BottomSheetDialog dialog) {
        currentSort = mode;
        binding.chipSort.setText(labelRes);
        dialog.dismiss();
        applyFilterAndSort();
    }

    private void setupQuickFilters() {
        View.OnClickListener reapply = v -> applyFilterAndSort();
        binding.chipBreakfast.setOnClickListener(reapply);
        binding.chipAvailable.setOnClickListener(reapply);
    }

    private void loadRooms() {
        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.getRoot().setVisibility(View.GONE);

        AppExecutors.io(() -> {
            String type = (typeFilter == null || typeFilter.equals("all")) ? null : typeFilter;

            List<Integer> amenityIdList = new ArrayList<>();
            for (int id : amenityIds) amenityIdList.add(id);

            // sortBy chỉ dùng để có thứ tự ban đầu hợp lý; sắp xếp lại theo chip
            // Sắp xếp được xử lý client-side trong applyFilterAndSort() (đúng SPEC 05:
            // "không query lại CSDL" khi đổi sắp xếp/lọc nhanh).
            List<RoomType> data = roomTypeDao.searchAndFilter(
                    null, type, priceMin, priceMax, numGuests, amenityIdList, "price_asc");

            Set<Integer> soldOut = new HashSet<>();
            Map<Integer, List<Amenity>> amenities = new HashMap<>();
            for (RoomType rt : data) {
                int overlapping = bookingDao.getOverlappingCount(rt.getId(), checkIn, checkOut);
                if (overlapping >= rt.getTotalRooms()) {
                    soldOut.add(rt.getId());
                }
                amenities.put(rt.getId(), roomTypeDao.getAmenitiesOfType(rt.getId()));
            }

            AppExecutors.runOnUiSafe(this, () -> {
                binding.pbLoading.setVisibility(View.GONE);
                allRooms = data;
                soldOutIds = soldOut;
                amenityCache = amenities;
                adapter.setSoldOutIds(soldOutIds);
                applyFilterAndSort();
            });
        });
    }

    /** Lọc nhanh + sắp xếp trên danh sách đã tải — KHÔNG query lại CSDL. */
    private void applyFilterAndSort() {
        List<RoomType> result = new ArrayList<>(allRooms);

        if (binding.chipAvailable.isChecked()) {
            result.removeIf(rt -> soldOutIds.contains(rt.getId()));
        }
        if (binding.chipBreakfast.isChecked()) {
            result.removeIf(rt -> !hasAmenity(rt.getId(), getString(R.string.amenity_breakfast)));
        }
        // chipFreeCancel: chính sách hủy miễn phí 48h áp dụng chung cho mọi phòng
        // (không có cột riêng trong CSDL) — chip đang mang tính hiển thị, chưa lọc thật.
        // Báo lại nếu cần thêm cột has_free_cancel vào room_types để lọc đúng nghĩa.

        switch (currentSort) {
            case PRICE_DESC:
                result.sort((a, b) -> Long.compare(b.getBasePrice(), a.getBasePrice()));
                break;
            case RATING:
                result.sort((a, b) -> Float.compare(b.getAvgRating(), a.getAvgRating()));
                break;
            case POPULAR:
                result.sort((a, b) -> Integer.compare(b.getReviewCount(), a.getReviewCount()));
                break;
            case PRICE_ASC:
            default:
                result.sort((a, b) -> Long.compare(a.getBasePrice(), b.getBasePrice()));
                break;
        }

        adapter.setData(result);
        updateSummaries(result.size());

        if (result.isEmpty()) {
            binding.layoutEmpty.getRoot().setVisibility(View.VISIBLE);
            // ivEmpty/tvEmptyTitle/tvEmptyDesc/btnEmptyAction lấy từ layout_empty_state (PROMPT #3)
            ((android.widget.TextView) binding.layoutEmpty.getRoot()
                    .findViewById(R.id.tvEmptyTitle)).setText(R.string.empty_search_title);
            ((android.widget.TextView) binding.layoutEmpty.getRoot()
                    .findViewById(R.id.tvEmptyDesc)).setText(R.string.empty_search_desc);
            View btnAction = binding.layoutEmpty.getRoot().findViewById(R.id.btnEmptyAction);
            if (btnAction instanceof android.widget.Button) {
                ((android.widget.Button) btnAction).setText(R.string.empty_search_action);
            }
            btnAction.setOnClickListener(v -> finish());
        } else {
            binding.layoutEmpty.getRoot().setVisibility(View.GONE);
        }
    }

    private boolean hasAmenity(int typeId, String amenityName) {
        List<Amenity> list = amenityCache.get(typeId);
        if (list == null) return false;
        for (Amenity a : list) {
            if (a.getAmenityName().equalsIgnoreCase(amenityName)) return true;
        }
        return false;
    }

    private void updateSummaries(int count) {
        int nights = DateUtils.nightsBetween(checkIn, checkOut);
        binding.tvResultCount.setText(getString(R.string.room_list_result_count, count));
        binding.tvResultSummary.setText(getString(R.string.room_list_result_summary_format, nights, count));
    }
}