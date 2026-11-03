package com.lumina.hotel.ui.admin;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.lumina.hotel.R;
import com.lumina.hotel.model.BookingFilter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Phụ lục F — bottom sheet bộ lọc nâng cao của Admin · Đơn đặt phòng. */
public class BookingFilterBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "BookingFilterBottomSheet";
    private static final String ARG_FILTER = "arg_filter";
    private static final String TAG_DATE_PICKER = "booking_filter_date_picker";

    public interface OnFilterAppliedListener {
        void onFilterApplied(BookingFilter filter);
    }

    private View rootView;
    private TextInputEditText edtFilterKeyword;
    private ChipGroup cgFilterMethod;
    private MaterialButton btnFilterDate;
    private MaterialButton btnFilterDateClear;
    private MaterialButton btnFilterReset;
    private MaterialButton btnFilterApply;
    private BookingFilter working;

    public static BookingFilterBottomSheet newInstance(BookingFilter current) {
        BookingFilterBottomSheet sheet = new BookingFilterBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FILTER, current.serialize());
        sheet.setArguments(args);
        return sheet;
    }

    /** Nhãn hiển thị của từng phương thức thanh toán (Fragment cũng dùng cho chip "đang áp dụng"). */
    public static int methodLabelRes(String method) {
        if (BookingFilter.METHOD_CASH.equals(method)) return R.string.payment_method_cash;
        if (BookingFilter.METHOD_VNPAY.equals(method)) return R.string.payment_method_vnpay;
        if (BookingFilter.METHOD_MOMO.equals(method)) return R.string.payment_method_momo;
        return R.string.payment_method_card;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.bottom_sheet_filter_booking, container, false);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        // Bàn phím không che nút "Áp dụng": cửa sổ co lại + sheet mở rộng hết cỡ
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        edtFilterKeyword = view.findViewById(R.id.edtFilterKeyword);
        cgFilterMethod = view.findViewById(R.id.cgFilterMethod);
        btnFilterDate = view.findViewById(R.id.btnFilterDate);
        btnFilterDateClear = view.findViewById(R.id.btnFilterDateClear);
        btnFilterReset = view.findViewById(R.id.btnFilterReset);
        btnFilterApply = view.findViewById(R.id.btnFilterApply);

        String raw = getArguments() != null ? getArguments().getString(ARG_FILTER, "") : "";
        working = BookingFilter.parse(raw);

        edtFilterKeyword.setText(working.getKeyword());
        buildMethodChips();
        updateDateButton();

        btnFilterDate.setOnClickListener(v -> openDatePicker());
        btnFilterDateClear.setOnClickListener(v -> {
            working.clearDateRange();
            updateDateButton();
        });
        btnFilterReset.setOnClickListener(v -> resetInputs());
        btnFilterApply.setOnClickListener(v -> applyAndClose());
    }

    // ---------------- PHƯƠNG THỨC THANH TOÁN ----------------

    private void buildMethodChips() {
        cgFilterMethod.removeAllViews();
        for (String method : BookingFilter.ALL_METHODS) {
            Chip chip = (Chip) getLayoutInflater()
                    .inflate(R.layout.chip_filter_option, cgFilterMethod, false);
            chip.setId(View.generateViewId());
            chip.setTag(method);
            chip.setText(methodLabelRes(method));
            chip.setChecked(working.getMethods().contains(method));
            cgFilterMethod.addView(chip);
        }
    }

    // ---------------- KHOẢNG NGÀY NHẬN PHÒNG ----------------

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

    /** MaterialDatePicker trả 00:00 UTC; DB lưu 00:00 giờ địa phương → quy đổi theo ngày-tháng-năm. */
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
        cgFilterMethod.clearCheck();
        edtFilterKeyword.setText("");
        working.clearDateRange();
        updateDateButton();
    }

    private void applyAndClose() {
        BookingFilter result = BookingFilter.parse("");

        for (int i = 0; i < cgFilterMethod.getChildCount(); i++) {
            Chip chip = (Chip) cgFilterMethod.getChildAt(i);
            if (chip.isChecked()) result.getMethods().add((String) chip.getTag());
        }
        CharSequence kw = edtFilterKeyword.getText();
        result.setKeyword(kw == null ? "" : kw.toString());
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
        edtFilterKeyword = null;
        cgFilterMethod = null;
        btnFilterDate = null;
        btnFilterDateClear = null;
        btnFilterReset = null;
        btnFilterApply = null;
    }
}
