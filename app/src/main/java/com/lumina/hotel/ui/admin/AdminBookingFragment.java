package com.lumina.hotel.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.AdminBookingAdapter;
import com.lumina.hotel.data.DbContract;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.RoomDao;
import com.lumina.hotel.databinding.FragmentAdminBookingBinding;
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

    private FragmentAdminBookingBinding binding;
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
        binding = FragmentAdminBookingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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
        binding.lvBookings.setAdapter(adapter);

        binding.layoutEmpty.btnEmptyAction.setVisibility(View.GONE);

        bookingFilter = BookingFilter.load(requireContext());
        setupFilterChips();
        binding.btnFilter.setOnClickListener(v -> openFilterSheet());
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
        binding = null;
    }

    // ---------------- GIAO DIỆN ----------------

    private void setupHeaderInset() {
        headerPaddingTop = binding.llAdminHeader.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.llAdminHeader, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), headerPaddingTop + sb.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.llAdminHeader);
    }

    private void showMessage(int stringRes) {
        if (binding != null) Snackbar.make(binding.getRoot(), stringRes, Snackbar.LENGTH_LONG).show();
    }

    private void setupFilterChips() {
        binding.cgBookingStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return; // selectionRequired=true nên không xảy ra
            int id = checkedIds.get(0);
            if (id == binding.chipAll.getId()) currentFilter = "all";
            else if (id == binding.chipPending.getId()) currentFilter = DbContract.BookingStatus.PENDING;
            else if (id == binding.chipConfirmed.getId()) currentFilter = DbContract.BookingStatus.CONFIRMED;
            else if (id == binding.chipCheckedIn.getId()) currentFilter = DbContract.BookingStatus.CHECKED_IN;
            else if (id == binding.chipCompleted.getId()) currentFilter = DbContract.BookingStatus.COMPLETED;
            else if (id == binding.chipCancelled.getId()) currentFilter = DbContract.BookingStatus.CANCELLED;
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
        if (binding != null) loadData(); // đổi bộ lọc nâng cao → phải tải lại cả số đếm lẫn danh sách
    }

    /** Vẽ lại hàng chip "đang áp dụng" + chấm badge trên icon phễu. */
    private void renderActiveFilters() {
        boolean active = bookingFilter.isActive();
        binding.cgActiveFilters.removeAllViews();
        binding.svActiveFilters.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.cvFilterBadge.setVisibility(active ? View.VISIBLE : View.GONE);
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
                .inflate(R.layout.chip_filter_option, binding.cgActiveFilters, false);
        chip.setId(View.generateViewId());
        chip.setCheckable(false);
        chip.setText(label);
        chip.setCloseIconVisible(true);
        chip.setCloseIconContentDescription(getString(R.string.admin_room_filter_cd_remove, label));
        chip.setOnCloseIconClickListener(v -> onRemove.run());
        binding.cgActiveFilters.addView(chip);
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
        if (adapter.getCount() == 0) binding.pbLoading.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            Map<String, Integer> counts = null;
            List<BookingWithDetail> bookings = new ArrayList<>();
            boolean failed = false;
            try {
                counts = bookingDao.countByStatus(filter);
                bookings.addAll(bookingDao.getAllWithDetail(currentFilter, filter));
            } catch (Exception e) {
                failed = true;
            }
            final Map<String, Integer> fCounts = counts;
            final boolean fFailed = failed;

            mainHandler.post(() -> {
                if (binding == null || token != loadToken) return;
                binding.pbLoading.setVisibility(View.GONE);
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
                if (binding == null || token != loadToken) return;
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
            binding.layoutEmpty.tvEmptyTitle.setText(filtering
                    ? R.string.admin_booking_filter_empty_title : R.string.admin_booking_empty_title);
            binding.layoutEmpty.tvEmptyDesc.setText(filtering
                    ? R.string.admin_booking_filter_empty_desc : R.string.admin_booking_empty_desc);
        }
        binding.layoutEmpty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.lvBookings.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void applyChipCounts(Map<String, Integer> counts) {
        int all = counts.containsKey("all") ? counts.get("all") : 0;
        binding.chipAll.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_all), all));
        binding.chipPending.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_pending), countOf(counts, DbContract.BookingStatus.PENDING)));
        binding.chipConfirmed.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_confirmed), countOf(counts, DbContract.BookingStatus.CONFIRMED)));
        binding.chipCheckedIn.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_checked_in), countOf(counts, DbContract.BookingStatus.CHECKED_IN)));
        binding.chipCompleted.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_booking_filter_completed), countOf(counts, DbContract.BookingStatus.COMPLETED)));
        binding.chipCancelled.setText(getString(R.string.admin_room_filter_format,
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
                if (binding == null) return;
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
                if (binding == null) return;
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
                if (binding == null) return;
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
            boolean ok;
            try {
                ok = bookingDao.checkIn(bookingId, roomId);
            } catch (Exception e) {
                ok = false;
            }
            final boolean fOk = ok;
            mainHandler.post(() -> {
                if (binding == null) return;
                showMessage(fOk ? R.string.admin_booking_checkin_success : R.string.admin_booking_checkin_error);
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
                if (binding == null) return;
                showMessage(fOk ? R.string.admin_booking_checkout_success : R.string.admin_booking_checkout_error);
                loadData();
            });
        });
    }
}
