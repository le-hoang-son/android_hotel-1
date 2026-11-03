package com.lumina.hotel.ui.admin;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.lumina.hotel.R;
import com.lumina.hotel.model.CustomerFilter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Phụ lục F — bottom sheet bộ lọc nâng cao của Admin · Khách hàng. */
public class CustomerFilterBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "CustomerFilterBottomSheet";
    private static final String ARG_FILTER = "arg_filter";
    private static final String TAG_DATE_PICKER = "customer_filter_date_picker";

    public interface OnFilterAppliedListener {
        void onFilterApplied(CustomerFilter filter);
    }

    private View rootView;
    private ChipGroup cgFilterHistory;
    private ChipGroup cgFilterVip;
    private MaterialButton btnFilterDate;
    private MaterialButton btnFilterDateClear;
    private MaterialButton btnFilterReset;
    private MaterialButton btnFilterApply;

    private CustomerFilter working;

    public static CustomerFilterBottomSheet newInstance(CustomerFilter current) {
        CustomerFilterBottomSheet sheet = new CustomerFilterBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FILTER, current.serialize());
        sheet.setArguments(args);
        return sheet;
    }

    /** Nhãn của giá trị lịch sử đặt phòng (Fragment cũng dùng cho chip "đang áp dụng"). */
    @StringRes
    public static int historyLabelRes(String history) {
        if (CustomerFilter.HISTORY_NONE.equals(history)) {
            return R.string.admin_customer_filter_history_none;
        }
        return R.string.admin_customer_filter_history_has;
    }

    /** Nhãn của giá trị hạng khách (Fragment cũng dùng cho chip "đang áp dụng"). */
    @StringRes
    public static int vipLabelRes(String vip) {
        if (CustomerFilter.VIP_NO.equals(vip)) {
            return R.string.admin_customer_filter_vip_no;
        }
        return R.string.admin_customer_filter_vip_yes;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.bottom_sheet_filter_customer, container, false);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cgFilterHistory = view.findViewById(R.id.cgFilterHistory);
        cgFilterVip = view.findViewById(R.id.cgFilterVip);
        btnFilterDate = view.findViewById(R.id.btnFilterDate);
        btnFilterDateClear = view.findViewById(R.id.btnFilterDateClear);
        btnFilterReset = view.findViewById(R.id.btnFilterReset);
        btnFilterApply = view.findViewById(R.id.btnFilterApply);

        String raw = getArguments() != null ? getArguments().getString(ARG_FILTER, "") : "";
        working = CustomerFilter.parse(raw);

        buildChoiceChips(cgFilterHistory,
                new String[]{CustomerFilter.HISTORY_HAS, CustomerFilter.HISTORY_NONE},
                new int[]{R.string.admin_customer_filter_history_has,
                        R.string.admin_customer_filter_history_none},
                working.getHistory());
        buildChoiceChips(cgFilterVip,
                new String[]{CustomerFilter.VIP_YES, CustomerFilter.VIP_NO},
                new int[]{R.string.admin_customer_filter_vip_yes,
                        R.string.admin_customer_filter_vip_no},
                working.getVip());
        updateDateButton();

        btnFilterDate.setOnClickListener(v -> openDatePicker());
        btnFilterDateClear.setOnClickListener(v -> {
            working.clearDateRange();
            updateDateButton();
        });
        btnFilterReset.setOnClickListener(v -> resetInputs());
        btnFilterApply.setOnClickListener(v -> applyAndClose());
    }

    // ---------------- CHIP CHỌN MỘT ----------------

    private void buildChoiceChips(ChipGroup group, String[] values, int[] labels, String selected) {
        group.removeAllViews();
        for (int i = 0; i < values.length; i++) {
            Chip chip = (Chip) getLayoutInflater()
                    .inflate(R.layout.chip_filter_option, group, false);
            chip.setId(View.generateViewId());
            chip.setTag(values[i]);
            chip.setText(labels[i]);
            chip.setChecked(values[i].equals(selected));
            group.addView(chip);
        }
    }

    /** Giá trị (tag) của chip đang chọn; chuỗi rỗng nếu không chọn chip nào. */
    private String checkedValue(ChipGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            if (chip.isChecked()) return (String) chip.getTag();
        }
        return "";
    }

    // ---------------- KHOẢNG NGÀY ĐĂNG KÝ ----------------

    private void openDatePicker() {
        MaterialDatePicker.Builder<Pair<Long, Long>> builder =
                MaterialDatePicker.Builder.dateRangePicker();
        builder.setTitleText(R.string.dash_pick_range);
        if (working.getDateFrom() > 0 && working.getDateTo() > 0) {
            builder.setSelection(new Pair<>(
                    localToUtcMidnight(working.getDateFrom()),
                    localToUtcMidnight(working.getDateTo())));
        }
        MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            working.setDateRange(utcToLocalMidnight(selection.first),
                    utcToLocalMidnight(selection.second));
            if (rootView != null) updateDateButton();
        });
        picker.show(getChildFragmentManager(), TAG_DATE_PICKER);
    }

    private void updateDateButton() {
        long from = working.getDateFrom();
        long to = working.getDateTo();
        String text;
        if (from > 0 && to > 0) {
            text = getString(R.string.admin_booking_filter_date_range_format,
                    formatDate(from), formatDate(to));
        } else if (from > 0) {
            text = getString(R.string.admin_booking_filter_date_from_format, formatDate(from));
        } else if (to > 0) {
            text = getString(R.string.admin_booking_filter_date_to_format, formatDate(to));
        } else {
            text = getString(R.string.dash_pick_range);
        }
        btnFilterDate.setText(text);
        btnFilterDateClear.setVisibility(working.hasDateRange() ? View.VISIBLE : View.GONE);
    }

    private static String formatDate(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(millis));
    }

    /** MaterialDatePicker trả 00:00 UTC → quy đổi sang 00:00 giờ địa phương theo ngày-tháng-năm. */
    private static long utcToLocalMidnight(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return local.getTimeInMillis();
    }

    private static long localToUtcMidnight(long localMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return utc.getTimeInMillis();
    }

    // ---------------- ĐẶT LẠI / ÁP DỤNG ----------------

    /** Chỉ xoá lựa chọn trên giao diện — chưa lưu cho tới khi bấm "Áp dụng". */
    private void resetInputs() {
        cgFilterHistory.clearCheck();
        cgFilterVip.clearCheck();
        working.clearDateRange();
        updateDateButton();
    }

    private void applyAndClose() {
        CustomerFilter result = new CustomerFilter();
        result.setHistory(checkedValue(cgFilterHistory));
        result.setVip(checkedValue(cgFilterVip));
        result.setDateRange(working.getDateFrom(), working.getDateTo());

        result.save(requireContext());

        Fragment parent = getParentFragment();
        if (parent instanceof OnFilterAppliedListener) {
            ((OnFilterAppliedListener) parent).onFilterApplied(result);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
        cgFilterHistory = null;
        cgFilterVip = null;
        btnFilterDate = null;
        btnFilterDateClear = null;
        btnFilterReset = null;
        btnFilterApply = null;
    }
}
