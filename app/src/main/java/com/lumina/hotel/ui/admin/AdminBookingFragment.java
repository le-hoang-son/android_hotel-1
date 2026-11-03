package com.lumina.hotel.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.AdminBookingAdapter;
import com.lumina.hotel.data.DbContract;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.RoomDao;
import com.lumina.hotel.model.BookingWithDetail;
import com.lumina.hotel.model.BookingFilter;
import com.lumina.hotel.model.IntentKeys;
import com.lumina.hotel.model.Room;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SPEC 11 — Tab "Đơn đặt phòng" của Admin: danh sách + chip lọc + chuỗi chuyển trạng thái. */
public class AdminBookingFragment extends Fragment implements AdminBookingAdapter.OnBookingActionListener, BookingFilterBottomSheet.OnFilterAppliedListener {

    private View rootView;
    private ListView lvBookings;
    private View layoutEmpty;
    private Button btnEmptyAction;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private ImageButton btnFilter;
    private LinearLayout llAdminHeader;
    private ChipGroup cgBookingStatus;
    private Chip chipAll;
    private Chip chipPending;
    private Chip chipConfirmed;
    private Chip chipCheckedIn;
    private Chip chipCompleted;
    private Chip chipCancelled;
    private ChipGroup cgActiveFilters;
    private View svActiveFilters;
    private View cvFilterBadge;
    private View pbLoading;

    private BookingDao bookingDao;
    private RoomDao roomDao;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AdminBookingAdapter adapter;

    private int headerPaddingTop;
    private int loadToken = 0;
    private String currentFilter = "all"; // "all" | pending | confirmed | checked_in | completed | cancelled
    private BookingFilter bookingFilter = new BookingFilter(); // bộ lọc nâng cao (phương thức + ngày nhận phòng + khách), nạp lại trong onViewCreated

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_admin_booking, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        lvBookings = view.findViewById(R.id.lvBookings);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        btnEmptyAction = layoutEmpty.findViewById(R.id.btnEmptyAction);
        tvEmptyTitle = layoutEmpty.findViewById(R.id.tvEmptyTitle);
        tvEmptyDesc = layoutEmpty.findViewById(R.id.tvEmptyDesc);
        btnFilter = view.findViewById(R.id.btnFilter);
        llAdminHeader = view.findViewById(R.id.llAdminHeader);
        cgBookingStatus = view.findViewById(R.id.cgBookingStatus);
        chipAll = view.findViewById(R.id.chipAll);
        chipPending = view.findViewById(R.id.chipPending);
        chipConfirmed = view.findViewById(R.id.chipConfirmed);
        chipCheckedIn = view.findViewById(R.id.chipCheckedIn);
        chipCompleted = view.findViewById(R.id.chipCompleted);
        chipCancelled = view.findViewById(R.id.chipCancelled);
        cgActiveFilters = view.findViewById(R.id.cgActiveFilters);
        svActiveFilters = view.findViewById(R.id.svActiveFilters);
        cvFilterBadge = view.findViewById(R.id.cvFilterBadge);
        pbLoading = view.findViewById(R.id.pbLoading);

        bookingDao = new BookingDao(requireContext().getApplicationContext());
        roomDao = new RoomDao(requireContext().getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        setupHeaderInset();

        adapter = new AdminBookingAdapter();
        // Bấm card (không trúng nút hành động) -> xem chi tiết đơn (chỉ xem).
        adapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(requireContext(), AdminBookingDetailActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOKING_ID, item.getBooking().getId());
            startActivity(intent);
        });
        adapter.setOnBookingActionListener(this);
        lvBookings.setAdapter(adapter);

        btnEmptyAction.setVisibility(View.GONE);

        bookingFilter = BookingFilter.load(requireContext());
        setupFilterChips();
        btnFilter.setOnClickListener(v -> openFilterSheet());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
        if (executor != null) executor.shutdownNow();
        rootView = null;
        lvBookings = null;
        layoutEmpty = null;
        btnEmptyAction = null;
        tvEmptyTitle = null;
        tvEmptyDesc = null;
        btnFilter = null;
        llAdminHeader = null;
        cgBookingStatus = null;
        chipAll = null;
        chipPending = null;
        chipConfirmed = null;
        chipCheckedIn = null;
        chipCompleted = null;
        chipCancelled = null;
        cgActiveFilters = null;
        svActiveFilters = null;
        cvFilterBadge = null;
        pbLoading = null;
    }

    // ---------------- GIAO DIỆN ----------------

    private void setupHeaderInset() {
        headerPaddingTop = llAdminHeader.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(llAdminHeader, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), headerPaddingTop + sb.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(llAdminHeader);
    }

    private void showMessage(int stringRes) {
        if (rootView != null) Snackbar.make(rootView, stringRes, Snackbar.LENGTH_LONG).show();
    }

    private void setupFilterChips() {
        cgBookingStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return; // selectionRequired=true nên không xảy ra
            int id = checkedIds.get(0);
            if (id == chipAll.getId()) currentFilter = "all";
            else if (id == chipPending.getId()) currentFilter = DbContract.BookingStatus.PENDING;
            else if (id == chipConfirmed.getId()) currentFilter = DbContract.BookingStatus.CONFIRMED;
            else if (id == chipCheckedIn.getId()) currentFilter = DbContract.BookingStatus.CHECKED_IN;
            else if (id == chipCompleted.getId()) currentFilter = DbContract.BookingStatus.COMPLETED;
            else if (id == chipCancelled.getId()) currentFilter = DbContract.BookingStatus.CANCELLED;
            loadBookings(); // chỉ tải lại danh sách, giữ nguyên số đếm chip
        });
    }

    // ---------------- BỘ LỌC NÂNG CAO (phương thức thanh toán + ngày nhận phòng + khách) ----------------

    private void openFilterSheet() {
        if (getChildFragmentManager().findFragmentByTag(BookingFilterBottomSheet.TAG) != null) return;
        BookingFilterBottomSheet.newInstance(bookingFilter)
                .show(getChildFragmentManager(), BookingFilterBottomSheet.TAG);
    }

    /** Callback từ BookingFilterBottomSheet (sheet đã tự lưu SharedPreferences). */
    @Override
    public void onFilterApplied(BookingFilter filter) {
        bookingFilter = filter;
        if (rootView != null) loadData(); // đổi bộ lọc nâng cao → phải tải lại cả số đếm lẫn danh sách
    }

    /** Vẽ lại hàng chip "đang áp dụng" + chấm badge trên icon phễu. */
    private void renderActiveFilters() {
        boolean active = bookingFilter.isActive();
        cgActiveFilters.removeAllViews();
        svActiveFilters.setVisibility(active ? View.VISIBLE : View.GONE);
        cvFilterBadge.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active) return;

        // Chip phương thức thanh toán — theo đúng thứ tự cash, vnpay, momo, card
        for (String method : BookingFilter.ALL_METHODS) {
            if (!bookingFilter.getMethods().contains(method)) continue;
            String label = getString(BookingFilterBottomSheet.methodLabelRes(method));
            addActiveChip(label, () -> {
                bookingFilter.getMethods().remove(method);
                onFilterChanged();
            });
        }

        // Chip khoảng ngày nhận phòng (cả khoảng / chỉ Từ / chỉ Đến)
        if (bookingFilter.hasDateRange()) {
            long from = bookingFilter.getDateFrom();
            long to = bookingFilter.getDateTo();
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
                bookingFilter.clearDateRange();
                onFilterChanged();
            });
        }

        // Chip từ khoá khách
        String keyword = bookingFilter.getKeyword();
        if (keyword != null && !keyword.isEmpty()) {
            addActiveChip(getString(R.string.admin_booking_filter_chip_keyword, keyword), () -> {
                bookingFilter.setKeyword("");
                onFilterChanged();
            });
        }
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(millis));
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

    /** Sau khi gỡ 1 điều kiện bằng nút x: lưu lại rồi tải lại số đếm + danh sách. */
    private void onFilterChanged() {
        bookingFilter.save(requireContext());
        loadData();
    }

    // ---------------- TẢI DỮ LIỆU (chạy nền) ----------------

    /** Tải cả số đếm (cho chip) lẫn danh sách theo filter hiện tại. Gọi khi mở màn / sau khi đổi trạng thái / đổi bộ lọc nâng cao. */
    private void loadData() {
        final int token = ++loadToken;
        final BookingFilter filter = bookingFilter.copy(); // chụp bản sao để luồng nền không đọc dữ liệu đang bị sửa
        if (adapter.getCount() == 0) pbLoading.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            Map<String, Integer> counts = null;
            List<BookingWithDetail> bookings = new ArrayList<>();
            boolean failed = false;
            try {
                bookingDao.cancelNoShows(); // dọn đơn confirmed quá ngày nhận phòng (khách không đến)
                counts = bookingDao.countByStatus(filter);
                bookings.addAll(bookingDao.getAllWithDetail(currentFilter, filter));
            } catch (Exception e) {
                failed = true;
            }
            final Map<String, Integer> fCounts = counts;
            final boolean fFailed = failed;

            mainHandler.post(() -> {
                if (rootView == null || token != loadToken) return;
                pbLoading.setVisibility(View.GONE);
                if (fFailed) {
                    showMessage(R.string.admin_booking_load_error);
                    return;
                }
                renderActiveFilters();
                applyChipCounts(fCounts);
                submitBookings(bookings);
            });
        });
    }

    /** Chỉ tải lại danh sách khi đổi chip trạng thái (bộ lọc nâng cao giữ nguyên, số đếm không đổi). */
    private void loadBookings() {
        final int token = ++loadToken;
        final BookingFilter filter = bookingFilter.copy();
        executor.execute(() -> {
            final List<BookingWithDetail> bookings = new ArrayList<>();
            boolean failed = false;
            try {
                bookings.addAll(bookingDao.getAllWithDetail(currentFilter, filter));
            } catch (Exception e) {
                failed = true;
            }
            final boolean fFailed = failed;
            mainHandler.post(() -> {
                if (rootView == null || token != loadToken) return;
                if (fFailed) {
                    showMessage(R.string.admin_booking_load_error);
                    return;
                }
                submitBookings(bookings);
            });
        });
    }

    private void submitBookings(List<BookingWithDetail> bookings) {
        adapter.setData(bookings);
        boolean empty = bookings.isEmpty();
        if (empty) {
            boolean filtering = bookingFilter.isActive();
            tvEmptyTitle.setText(filtering
                    ? R.string.admin_booking_filter_empty_title : R.string.admin_booking_empty_title);
            tvEmptyDesc.setText(filtering
                    ? R.string.admin_booking_filter_empty_desc : R.string.admin_booking_empty_desc);
        }
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        lvBookings.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void applyChipCounts(Map<String, Integer> counts) {
        int all = counts.containsKey("all") ? counts.get("all") : 0;
        chipAll.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_all), all));
        chipPending.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_pending), countOf(counts, DbContract.BookingStatus.PENDING)));
        chipConfirmed.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_confirmed), countOf(counts, DbContract.BookingStatus.CONFIRMED)));
        chipCheckedIn.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_checked_in), countOf(counts, DbContract.BookingStatus.CHECKED_IN)));
        chipCompleted.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_completed), countOf(counts, DbContract.BookingStatus.COMPLETED)));
        chipCancelled.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_cancelled), countOf(counts, DbContract.BookingStatus.CANCELLED)));
    }

    private int countOf(Map<String, Integer> counts, String key) {
        Integer v = counts.get(key);
        return v == null ? 0 : v;
    }

    // ---------------- XÁC NHẬN (pending -> confirmed) ----------------

    @Override
    public void onConfirm(@NonNull BookingWithDetail item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_booking_confirm_title)
                .setMessage(getString(R.string.admin_booking_confirm_message, item.getBooking().getBookingCode()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm,
                        (d, w) -> doConfirm(item.getBooking().getId()))
                .show();
    }

    private void doConfirm(int bookingId) {
        executor.execute(() -> {
            boolean ok;
            try {
                ok = bookingDao.confirmBooking(bookingId) > 0;
            } catch (Exception e) {
                ok = false;
            }
            final boolean fOk = ok;
            mainHandler.post(() -> {
                if (rootView == null) return;
                showMessage(fOk ? R.string.admin_booking_confirm_success : R.string.admin_booking_confirm_error);
                loadData(); // luôn tải lại: thất bại thường do đơn đã đổi trạng thái, cần cập nhật dòng cũ
            });
        });
    }

    // ---------------- TỪ CHỐI (pending -> cancelled, ghi lý do vào note) ----------------

    @Override
    public void onReject(@NonNull BookingWithDetail item) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setHint(R.string.admin_booking_reject_hint);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(requireContext());
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_booking_reject_title)
                .setView(container)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (d, w) -> {
                    String reason = input.getText() != null ? input.getText().toString().trim() : "";
                    doReject(item.getBooking().getId(), reason);
                })
                .show();
    }

    private void doReject(int bookingId, String reason) {
        executor.execute(() -> {
            boolean ok;
            try {
                ok = bookingDao.rejectBooking(bookingId, reason) > 0;
            } catch (Exception e) {
                ok = false;
            }
            final boolean fOk = ok;
            mainHandler.post(() -> {
                if (rootView == null) return;
                showMessage(fOk ? R.string.admin_booking_reject_success : R.string.admin_booking_reject_error);
                loadData();
            });
        });
    }

    // ---------------- CHECK-IN (confirmed -> checked_in, BẮT BUỘC gán phòng) ----------------
    // ---------------- CHECK-OUT (checked_in -> completed) ------------------------------------

    /** Nút hành động dùng chung cho 2 trạng thái, Fragment tự phân biệt qua booking_status hiện tại. */
    @Override
    public void onActionClick(@NonNull BookingWithDetail item) {
        String status = item.getBooking().getBookingStatus();
        if (DbContract.BookingStatus.CONFIRMED.equals(status)) {
            startCheckIn(item);
        } else if (DbContract.BookingStatus.CHECKED_IN.equals(status)) {
            confirmCheckOut(item);
        }
    }

    private void startCheckIn(BookingWithDetail item) {
        final int typeId = item.getBooking().getRoomTypeId();
        executor.execute(() -> {
            List<Room> rooms = null;
            boolean failed = false;
            try {
                rooms = roomDao.getAvailableRoomsOfType(typeId);
            } catch (Exception e) {
                failed = true;
            }
            final List<Room> fRooms = rooms;
            final boolean fFailed = failed;
            mainHandler.post(() -> {
                if (rootView == null) return;
                if (fFailed) {
                    showMessage(R.string.admin_booking_checkin_error);
                    return;
                }
                if (fRooms == null || fRooms.isEmpty()) {
                    showMessage(R.string.admin_booking_checkin_empty);
                    return;
                }
                showRoomPicker(item, fRooms);
            });
        });
    }

    private void showRoomPicker(BookingWithDetail item, List<Room> rooms) {
        String[] labels = new String[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            labels[i] = getString(R.string.admin_room_meta, rooms.get(i).getRoomNumber(), rooms.get(i).getFloor());
        }
        final int[] selected = { 0 };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_booking_checkin_title)
                .setSingleChoiceItems(labels, 0, (d, which) -> selected[0] = which)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (d, w) ->
                        doCheckIn(item.getBooking().getId(), rooms.get(selected[0]).getId()))
                .show();
    }

    private void doCheckIn(int bookingId, int roomId) {
        executor.execute(() -> {
            int result;
            try {
                result = bookingDao.checkIn(bookingId, roomId);
            } catch (Exception e) {
                result = BookingDao.CHECKIN_FAILED;
            }
            final int fResult = result;
            mainHandler.post(() -> {
                if (rootView == null) return;
                if (fResult == BookingDao.CHECKIN_OK) {
                    showMessage(R.string.admin_booking_checkin_success);
                } else if (fResult == BookingDao.CHECKIN_TOO_EARLY) {
                    showMessage(R.string.admin_booking_checkin_too_early);
                } else {
                    showMessage(R.string.admin_booking_checkin_error);
                }
                loadData();
            });
        });
    }

    private void confirmCheckOut(BookingWithDetail item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_booking_checkout_title)
                .setMessage(getString(R.string.admin_booking_checkout_message, item.getBooking().getBookingCode()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (d, w) ->
                        doCheckOut(item.getBooking().getId(), item.getBooking().getRoomId()))
                .show();
    }

    private void doCheckOut(int bookingId, int roomId) {
        executor.execute(() -> {
            boolean ok;
            try {
                ok = bookingDao.checkOut(bookingId, roomId);
            } catch (Exception e) {
                ok = false;
            }
            final boolean fOk = ok;
            mainHandler.post(() -> {
                if (rootView == null) return;
                showMessage(fOk ? R.string.admin_booking_checkout_success : R.string.admin_booking_checkout_error);
                loadData();
            });
        });
    }
}
