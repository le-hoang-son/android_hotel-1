package com.lumina.hotel.ui.customer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextThemeWrapper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.snackbar.Snackbar;

import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.AmenityDao;
import com.lumina.hotel.model.Amenity;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;


import android.content.Intent;
import com.lumina.hotel.model.IntentKeys;
/**
 * SPEC 04 — Tìm phòng.
 * Chọn ngày nhận/trả, số khách, loại phòng, bộ lọc nâng cao (giá + tiện nghi),
 * lưu tiêu chí lần tìm gần nhất vào SharedPreferences, bấm "Tìm kiếm" → RoomListActivity.
 */
public class SearchFragment extends Fragment {

    private static final String PREFS_NAME = "lumina_search_prefs";
    private static final String KEY_CHECK_IN = "check_in";
    private static final String KEY_CHECK_OUT = "check_out";
    private static final String KEY_GUESTS = "guests";
    private static final String KEY_TYPE_FILTER = "type_filter";
    private static final String KEY_PRICE_MIN = "price_min";
    private static final String KEY_PRICE_MAX = "price_max";
    private static final String KEY_AMENITY_IDS = "amenity_ids"; // lưu dạng "1,2,3"

    private static final int MIN_GUESTS = 1;
    private static final int MAX_GUESTS = 10;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;
    private static final long DEFAULT_PRICE_MIN = 1_000_000;
    private static final long DEFAULT_PRICE_MAX = 5_000_000;


    private View rootView;
    private View layoutCheckIn;
    private View layoutCheckOut;
    private View btnGuestMinus;
    private View btnGuestPlus;
    private View layoutAdvanced;
    private RangeSlider sliderPrice;
    private ChipGroup cgRoomType;
    private Chip chipAll;
    private View tvResetFilter;
    private View btnSearch;
    private TextView tvCheckInDate;
    private TextView tvCheckInWeekday;
    private TextView tvCheckOutDate;
    private TextView tvCheckOutWeekday;
    private TextView tvNightCount;
    private TextView tvGuestCount;
    private View layoutAdvancedContent;
    private View ivAdvancedArrow;
    private TextView tvPriceValue;
    private ChipGroup cgAmenity;
    private View layoutFilterStatus;
    private TextView tvFilterStatus;

    private AmenityDao amenityDao;
    private SharedPreferences prefs;

    private long checkIn;
    private long checkOut;
    private int numGuests = 2;
    private boolean advancedExpanded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_search, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutCheckIn = view.findViewById(R.id.layoutCheckIn);
        layoutCheckOut = view.findViewById(R.id.layoutCheckOut);
        btnGuestMinus = view.findViewById(R.id.btnGuestMinus);
        btnGuestPlus = view.findViewById(R.id.btnGuestPlus);
        layoutAdvanced = view.findViewById(R.id.layoutAdvanced);
        sliderPrice = view.findViewById(R.id.sliderPrice);
        cgRoomType = view.findViewById(R.id.cgRoomType);
        chipAll = view.findViewById(R.id.chipAll);
        tvResetFilter = view.findViewById(R.id.tvResetFilter);
        btnSearch = view.findViewById(R.id.btnSearch);
        tvCheckInDate = view.findViewById(R.id.tvCheckInDate);
        tvCheckInWeekday = view.findViewById(R.id.tvCheckInWeekday);
        tvCheckOutDate = view.findViewById(R.id.tvCheckOutDate);
        tvCheckOutWeekday = view.findViewById(R.id.tvCheckOutWeekday);
        tvNightCount = view.findViewById(R.id.tvNightCount);
        tvGuestCount = view.findViewById(R.id.tvGuestCount);
        layoutAdvancedContent = view.findViewById(R.id.layoutAdvancedContent);
        ivAdvancedArrow = view.findViewById(R.id.ivAdvancedArrow);
        tvPriceValue = view.findViewById(R.id.tvPriceValue);
        cgAmenity = view.findViewById(R.id.cgAmenity);
        layoutFilterStatus = view.findViewById(R.id.layoutFilterStatus);
        tvFilterStatus = view.findViewById(R.id.tvFilterStatus);

        amenityDao = new AmenityDao(requireContext());
        prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);

        setDefaultOrRestoreCriteria();
        updateDateViews();
        updateGuestViews();
        loadAmenityChips();
        updatePriceLabel();

        layoutCheckIn.setOnClickListener(v -> openDateRangePicker());
        layoutCheckOut.setOnClickListener(v -> openDateRangePicker());

        btnGuestMinus.setOnClickListener(v -> {
            if (numGuests > MIN_GUESTS) {
                numGuests--;
                updateGuestViews();
            }
        });
        btnGuestPlus.setOnClickListener(v -> {
            if (numGuests < MAX_GUESTS) {
                numGuests++;
                updateGuestViews();
            }
        });

        layoutAdvanced.setOnClickListener(v -> toggleAdvanced());

        sliderPrice.addOnChangeListener((slider, value, fromUser) -> {
            updatePriceLabel();
            updateFilterStatusViews();
        });
        cgRoomType.setOnCheckedStateChangeListener((group, checkedIds) -> updateFilterStatusViews());
        tvResetFilter.setOnClickListener(v -> resetFilters());

        btnSearch.setOnClickListener(v -> onSearchClicked());

        updateFilterStatusViews();
    }

    // ===================== NGÀY =====================

    private void openDateRangePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.search_date_range_label)
                .setCalendarConstraints(constraints)
                .setSelection(Pair.create(toUtcMillis(checkIn), toUtcMillis(checkOut)))
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            long newCheckIn = fromUtcMillis(selection.first);
            long newCheckOut = fromUtcMillis(selection.second);
            if (DateUtils.nightsBetween(newCheckIn, newCheckOut) < 1) {
                Snackbar.make(rootView, R.string.search_error_date_order, Snackbar.LENGTH_SHORT).show();
                return;
            }
            checkIn = newCheckIn;
            checkOut = newCheckOut;
            updateDateViews();
        });

        picker.show(getParentFragmentManager(), "date_range_picker");
    }

    /** MaterialDatePicker làm việc theo mốc UTC — quy đổi sang đúng ngày lịch ở múi giờ máy. */
    private long toUtcMillis(long localStartOfDay) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localStartOfDay);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private long fromUtcMillis(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        return DateUtils.atStartOfDay(
                new java.util.GregorianCalendar(
                        utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH)
                ).getTimeInMillis());
    }

    private void updateDateViews() {
        tvCheckInDate.setText(DateUtils.formatFull(checkIn));
        tvCheckInWeekday.setText(DateUtils.weekdayVi(checkIn));
        tvCheckOutDate.setText(DateUtils.formatFull(checkOut));
        tvCheckOutWeekday.setText(DateUtils.weekdayVi(checkOut));
        int nights = DateUtils.nightsBetween(checkIn, checkOut);
        tvNightCount.setText(getString(R.string.night_count_format, nights));
    }

    // ===================== SỐ KHÁCH =====================

    private void updateGuestViews() {
        tvGuestCount.setText(String.valueOf(numGuests));
        btnGuestMinus.setEnabled(numGuests > MIN_GUESTS);
        btnGuestMinus.setAlpha(numGuests > MIN_GUESTS ? 1f : 0.4f);
        btnGuestPlus.setEnabled(numGuests < MAX_GUESTS);
        btnGuestPlus.setAlpha(numGuests < MAX_GUESTS ? 1f : 0.4f);
    }

    // ===================== BỘ LỌC NÂNG CAO =====================

    private void toggleAdvanced() {
        advancedExpanded = !advancedExpanded;
        layoutAdvancedContent.setVisibility(advancedExpanded ? View.VISIBLE : View.GONE);
        ivAdvancedArrow.animate().rotation(advancedExpanded ? 180f : 0f).setDuration(200).start();
    }

    private void updatePriceLabel() {
        List<Float> values = sliderPrice.getValues();
        long min = values.get(0).longValue();
        long max = values.get(1).longValue();
        tvPriceValue.setText(getString(R.string.search_price_value_format,
                PriceUtils.formatShort(min), PriceUtils.formatShort(max)));
    }

    private void loadAmenityChips() {
        cgAmenity.removeAllViews();
        AppExecutors.io(() -> {
            List<Amenity> amenities = amenityDao.getAll();
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                for (Amenity a : amenities) {
                    Chip chip = new Chip(new ContextThemeWrapper(requireContext(), R.style.Widget_Lumina_Chip_Filter));
                    chip.setCheckable(true);
                    chip.setText(a.getAmenityName());
                    chip.setTag(a.getId());
                    chip.setOnCheckedChangeListener((buttonView, isChecked) -> updateFilterStatusViews());
                    cgAmenity.addView(chip);
                }
                restoreCheckedAmenities();
            });
        });
    }

    // ===================== LOẠI PHÒNG =====================

    private String getSelectedTypeFilter() {
        int checkedId = cgRoomType.getCheckedChipId();
        if (checkedId == R.id.chipAll || checkedId == View.NO_ID) return "all";
        Chip chip = rootView.findViewById(checkedId);
        return chip != null ? chip.getText().toString() : "all";
    }

    private void selectTypeFilter(String typeFilter) {
        if ("all".equals(typeFilter)) {
            chipAll.setChecked(true);
            return;
        }
        for (int i = 0; i < cgRoomType.getChildCount(); i++) {
            View child = cgRoomType.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).getText().toString().equals(typeFilter)) {
                ((Chip) child).setChecked(true);
                return;
            }
        }
        chipAll.setChecked(true);
    }

    // ===================== TÌM KIẾM =====================

    private void onSearchClicked() {
        List<Float> priceValues = sliderPrice.getValues();
        long priceMin = priceValues.get(0).longValue();
        long priceMax = priceValues.get(1).longValue();
        String typeFilter = getSelectedTypeFilter();

        List<Integer> amenityIds = new ArrayList<>();
        for (int i = 0; i < cgAmenity.getChildCount(); i++) {
            View child = cgAmenity.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                amenityIds.add((Integer) child.getTag());
            }
        }

        saveCriteria(typeFilter, priceMin, priceMax, amenityIds);

        Intent intent = new Intent(requireContext(), RoomListActivity.class);
        intent.putExtra(IntentKeys.EXTRA_CHECK_IN, checkIn);
        intent.putExtra(IntentKeys.EXTRA_CHECK_OUT, checkOut);
        intent.putExtra(IntentKeys.EXTRA_NUM_GUESTS, numGuests);
        intent.putExtra(IntentKeys.EXTRA_TYPE_FILTER, typeFilter);
        intent.putExtra(IntentKeys.EXTRA_PRICE_MIN, priceMin);
        intent.putExtra(IntentKeys.EXTRA_PRICE_MAX, priceMax);
        int[] ids = new int[amenityIds.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = amenityIds.get(i);
        intent.putExtra(IntentKeys.EXTRA_AMENITY_IDS, ids);
        startActivity(intent);
    }

    private void showNotReadySnackbar() {
        Snackbar.make(rootView, R.string.feature_not_ready, Snackbar.LENGTH_SHORT).show();
    }

    // ===================== SHARED PREFERENCES =====================

    private void setDefaultOrRestoreCriteria() {
        long defaultCheckIn = DateUtils.atStartOfDay(System.currentTimeMillis());
        long defaultCheckOut = defaultCheckIn + ONE_DAY_MS;

        checkIn = prefs.getLong(KEY_CHECK_IN, defaultCheckIn);
        checkOut = prefs.getLong(KEY_CHECK_OUT, defaultCheckOut);
        // Nếu tiêu chí cũ đã ở quá khứ (mở lại app sau nhiều ngày) → quay về mặc định hôm nay.
        if (checkIn < DateUtils.atStartOfDay(System.currentTimeMillis())) {
            checkIn = defaultCheckIn;
            checkOut = defaultCheckOut;
        }
        numGuests = prefs.getInt(KEY_GUESTS, 2);

        selectTypeFilter(prefs.getString(KEY_TYPE_FILTER, "all"));

        long savedMin = prefs.getLong(KEY_PRICE_MIN, -1);
        long savedMax = prefs.getLong(KEY_PRICE_MAX, -1);
        if (savedMin >= 0 && savedMax >= 0) {
            sliderPrice.setValues((float) savedMin, (float) savedMax);
        }
    }

    private void restoreCheckedAmenities() {
        String saved = prefs.getString(KEY_AMENITY_IDS, "");
        if (saved.isEmpty()) return;
        List<String> savedIds = java.util.Arrays.asList(saved.split(","));
        for (int i = 0; i < cgAmenity.getChildCount(); i++) {
            View child = cgAmenity.getChildAt(i);
            if (child instanceof Chip && savedIds.contains(String.valueOf(child.getTag()))) {
                ((Chip) child).setChecked(true);
            }
        }
    }

    private void saveCriteria(String typeFilter, long priceMin, long priceMax, List<Integer> amenityIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < amenityIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(amenityIds.get(i));
        }
        prefs.edit()
                .putLong(KEY_CHECK_IN, checkIn)
                .putLong(KEY_CHECK_OUT, checkOut)
                .putInt(KEY_GUESTS, numGuests)
                .putString(KEY_TYPE_FILTER, typeFilter)
                .putLong(KEY_PRICE_MIN, priceMin)
                .putLong(KEY_PRICE_MAX, priceMax)
                .putString(KEY_AMENITY_IDS, sb.toString())
                .apply();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
        layoutCheckIn = null;
        layoutCheckOut = null;
        btnGuestMinus = null;
        btnGuestPlus = null;
        layoutAdvanced = null;
        sliderPrice = null;
        cgRoomType = null;
        chipAll = null;
        tvResetFilter = null;
        btnSearch = null;
        tvCheckInDate = null;
        tvCheckInWeekday = null;
        tvCheckOutDate = null;
        tvCheckOutWeekday = null;
        tvNightCount = null;
        tvGuestCount = null;
        layoutAdvancedContent = null;
        ivAdvancedArrow = null;
        tvPriceValue = null;
        cgAmenity = null;
        layoutFilterStatus = null;
        tvFilterStatus = null;
    }

    private void updateFilterStatusViews() {
        int count = 0;
        if (!"all".equals(getSelectedTypeFilter())) count++;

        List<Float> priceValues = sliderPrice.getValues();
        long min = priceValues.get(0).longValue();
        long max = priceValues.get(1).longValue();
        if (min != DEFAULT_PRICE_MIN || max != DEFAULT_PRICE_MAX) {
            count++;
        }

        int amenityCount = 0;
        for (int i = 0; i < cgAmenity.getChildCount(); i++) {
            View child = cgAmenity.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) amenityCount++;
        }
        if (amenityCount > 0) count++;

        if (count == 0) {
            layoutFilterStatus.setVisibility(View.GONE);
        } else {
            layoutFilterStatus.setVisibility(View.VISIBLE);
            tvFilterStatus.setText(getString(R.string.filter_status_count_format, count));
        }
    }

    private void resetFilters() {
        chipAll.setChecked(true);
        sliderPrice.setValues((float) DEFAULT_PRICE_MIN, (float) DEFAULT_PRICE_MAX);
        for (int i = 0; i < cgAmenity.getChildCount(); i++) {
            View child = cgAmenity.getChildAt(i);
            if (child instanceof Chip) ((Chip) child).setChecked(false);
        }
        updatePriceLabel();
        updateFilterStatusViews();
    }
}
