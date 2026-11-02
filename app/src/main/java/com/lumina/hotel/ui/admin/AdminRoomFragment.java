package com.lumina.hotel.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.AdminRoomAdapter;
import com.lumina.hotel.data.DbContract;
import com.lumina.hotel.data.dao.RoomDao;
import com.lumina.hotel.databinding.BottomSheetRoomStatusBinding;
import com.lumina.hotel.databinding.FragmentAdminRoomBinding;
import com.lumina.hotel.model.Room;
import com.lumina.hotel.model.RoomFilter;
import com.lumina.hotel.model.RoomType;
import com.lumina.hotel.model.RoomWithType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SPEC 10 — Tab "Phòng" của Admin: lưới 2 cột + lọc trạng thái + đổi trạng thái + xóa. */
public class AdminRoomFragment extends Fragment
        implements AdminRoomAdapter.OnRoomLongClickListener, RoomFilterBottomSheet.OnFilterAppliedListener {

    private static final int DELETE_OK = 0;
    private static final int DELETE_BLOCKED = 1;
    private static final int DELETE_ERROR = 2;

    private FragmentAdminRoomBinding binding;
    private RoomDao dao;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AdminRoomAdapter adapter;

    private int headerPaddingTop;
    private int loadToken = 0;
    private String currentFilter = "all"; // "all" | available | booked | occupied | cleaning | maintenance
    private RoomFilter roomFilter = new RoomFilter(); // bộ lọc nâng cao (loại phòng + tầng), nạp lại trong onViewCreated
    private Map<Integer, String> typeNames = new HashMap<>(); // id loại phòng -> tên, dùng cho chip "đang áp dụng"


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminRoomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dao = new RoomDao(requireContext().getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        setupHeaderInset();

        adapter = new AdminRoomAdapter();
        adapter.setOnItemClickListener(item -> openStatusSheet(item));
        adapter.setOnRoomLongClickListener(this);
        binding.gvRooms.setAdapter(adapter);

        roomFilter = RoomFilter.load(requireContext());

        binding.layoutEmpty.btnEmptyAction.setVisibility(View.GONE);

        setupFilterChips();

        binding.btnAddRoom.setOnClickListener(v -> openForm(0));
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
        binding.cgRoomStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return; // selectionRequired=true nên không xảy ra
            int id = checkedIds.get(0);
            if (id == binding.chipAll.getId()) currentFilter = "all";
            else if (id == binding.chipAvailable.getId()) currentFilter = DbContract.RoomStatus.AVAILABLE;
            else if (id == binding.chipBooked.getId()) currentFilter = DbContract.RoomStatus.BOOKED;
            else if (id == binding.chipOccupied.getId()) currentFilter = DbContract.RoomStatus.OCCUPIED;
            else if (id == binding.chipCleaning.getId()) currentFilter = DbContract.RoomStatus.CLEANING;
            else if (id == binding.chipMaintenance.getId()) currentFilter = DbContract.RoomStatus.MAINTENANCE;
            loadRooms(); // chỉ tải lại danh sách, giữ nguyên số đếm chip
        });
    }

    // ---------------- BỘ LỌC NÂNG CAO (loại phòng + tầng) ----------------

    private void openFilterSheet() {
        if (getChildFragmentManager().findFragmentByTag(RoomFilterBottomSheet.TAG) != null) return;
        RoomFilterBottomSheet.newInstance(roomFilter)
                .show(getChildFragmentManager(), RoomFilterBottomSheet.TAG);
    }

    /** Callback từ RoomFilterBottomSheet (sheet đã tự lưu SharedPreferences). */
    @Override
    public void onFilterApplied(RoomFilter filter) {
        roomFilter = filter;
        if (binding != null) loadData(); // đổi loại/tầng → phải tải lại cả số đếm lẫn danh sách
    }

    /** Vẽ lại hàng chip "đang áp dụng" + chấm badge trên icon phễu. */
    private void renderActiveFilters() {
        boolean active = roomFilter.isActive();
        binding.cgActiveFilters.removeAllViews();
        binding.svActiveFilters.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.cvFilterBadge.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active) return;

        for (Integer typeId : new TreeSet<>(roomFilter.getTypeIds())) {
            String name = typeNames.get(typeId);
            String label = name != null ? name : String.valueOf(typeId);
            addActiveChip(label, () -> {
                roomFilter.getTypeIds().remove(typeId);
                onFilterChanged();
            });
        }
        for (Integer floor : new TreeSet<>(roomFilter.getFloors())) {
            String label = getString(R.string.admin_room_filter_floor_format, floor);
            addActiveChip(label, () -> {
                roomFilter.getFloors().remove(floor);
                onFilterChanged();
            });
        }
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
        roomFilter.save(requireContext());
        loadData();
    }

    // ---------------- TẢI DỮ LIỆU (chạy nền) ----------------

    /** Tải cả số đếm (cho chip) lẫn danh sách theo filter hiện tại. Gọi khi mở màn / sau khi đổi trạng thái / xóa. */
    private void loadData() {
        final int token = ++loadToken;
        final RoomFilter filter = roomFilter.copy(); // chụp bản sao để luồng nền không đọc dữ liệu đang bị sửa
        if (adapter.getCount() == 0) binding.pbLoading.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            Map<String, Integer> counts = null;
            final Map<Integer, String> names = new HashMap<>();
            List<RoomWithType> rooms = new ArrayList<>();
            boolean failed = false;
            try {
                counts = dao.countByStatus(filter);
                rooms.addAll(dao.getRoomsWithType(currentFilter, filter));
                for (RoomType t : dao.getTypeOptions()) names.put(t.getId(), t.getTypeName());
            } catch (Exception e) {
                failed = true;
            }
            final Map<String, Integer> fCounts = counts;
            final boolean fFailed = failed;

            mainHandler.post(() -> {
                if (binding == null || token != loadToken) return;
                binding.pbLoading.setVisibility(View.GONE);
                if (fFailed) {
                    showMessage(R.string.admin_room_load_error);
                    return;
                }
                typeNames = names;
                renderActiveFilters();
                applyChipCounts(fCounts);
                submitRooms(rooms);
            });
        });
    }

    /** Chỉ tải lại danh sách khi đổi chip trạng thái (bộ lọc loại/tầng giữ nguyên, số đếm không đổi). */
    private void loadRooms() {
        final int token = ++loadToken;
        final RoomFilter filter = roomFilter.copy();
        executor.execute(() -> {
            final List<RoomWithType> rooms = new ArrayList<>();
            boolean failed = false;
            try {
                rooms.addAll(dao.getRoomsWithType(currentFilter, filter));
            } catch (Exception e) {
                failed = true;
            }
            final boolean fFailed = failed;
            mainHandler.post(() -> {
                if (binding == null || token != loadToken) return;
                if (fFailed) {
                    showMessage(R.string.admin_room_load_error);
                    return;
                }
                submitRooms(rooms);
            });
        });
    }

    private void submitRooms(List<RoomWithType> rooms) {
        adapter.setData(rooms);
        boolean empty = rooms.isEmpty();
        if (empty) {
            boolean filtering = roomFilter.isActive();
            binding.layoutEmpty.tvEmptyTitle.setText(filtering
                    ? R.string.admin_room_filter_empty_title : R.string.admin_room_empty_title);
            binding.layoutEmpty.tvEmptyDesc.setText(filtering
                    ? R.string.admin_room_filter_empty_desc : R.string.admin_room_empty_desc);
        }
        binding.layoutEmpty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.gvRooms.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void applyChipCounts(Map<String, Integer> counts) {
        int all = counts.containsKey("all") ? counts.get("all") : 0;
        binding.chipAll.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_all), all));
        binding.chipAvailable.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_available), countOf(counts, DbContract.RoomStatus.AVAILABLE)));
        binding.chipBooked.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_booked), countOf(counts, DbContract.RoomStatus.BOOKED)));
        binding.chipOccupied.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_occupied), countOf(counts, DbContract.RoomStatus.OCCUPIED)));
        binding.chipCleaning.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_cleaning), countOf(counts, DbContract.RoomStatus.CLEANING)));
        binding.chipMaintenance.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_maintenance), countOf(counts, DbContract.RoomStatus.MAINTENANCE)));
    }

    private int countOf(Map<String, Integer> counts, String key) {
        Integer v = counts.get(key);
        return v == null ? 0 : v;
    }

    // ---------------- THÊM / SỬA ----------------

    /** roomId = 0 → thêm mới; khác 0 → sửa. */
    private void openForm(int roomId) {
        Intent intent = new Intent(requireContext(), AddEditRoomActivity.class);
        if (roomId > 0) {
            intent.putExtra(AddEditRoomActivity.EXTRA_ROOM_ID, roomId);
        }
        startActivity(intent);
    }

    // ---------------- ĐỔI TRẠNG THÁI (BottomSheet) ----------------

    private void openStatusSheet(RoomWithType item) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        BottomSheetRoomStatusBinding sheet = BottomSheetRoomStatusBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheet.getRoot());

        sheet.tvSheetTitle.setText(getString(R.string.admin_room_status_sheet_title,
                item.getRoom().getRoomNumber()));
        checkRadioForStatus(sheet, item.getRoom().getRoomStatus());

        sheet.btnUpdateStatus.setOnClickListener(v -> {
            String newStatus = statusFromCheckedRadio(sheet);
            if (newStatus == null) return;
            dialog.dismiss();
            if (newStatus.equals(item.getRoom().getRoomStatus())) return; // không đổi gì, khỏi gọi DB
            updateRoomStatus(item.getRoom().getId(), newStatus);
        });

        dialog.show();
    }

    private void checkRadioForStatus(BottomSheetRoomStatusBinding sheet, String status) {
        if (status == null) return;
        if (status.equals(DbContract.RoomStatus.AVAILABLE)) sheet.rbAvailable.setChecked(true);
        else if (status.equals(DbContract.RoomStatus.BOOKED)) sheet.rbBooked.setChecked(true);
        else if (status.equals(DbContract.RoomStatus.OCCUPIED)) sheet.rbOccupied.setChecked(true);
        else if (status.equals(DbContract.RoomStatus.CLEANING)) sheet.rbCleaning.setChecked(true);
        else if (status.equals(DbContract.RoomStatus.MAINTENANCE)) sheet.rbMaintenance.setChecked(true);
    }

    private String statusFromCheckedRadio(BottomSheetRoomStatusBinding sheet) {
        int id = sheet.rgRoomStatus.getCheckedRadioButtonId();
        if (id == sheet.rbAvailable.getId()) return DbContract.RoomStatus.AVAILABLE;
        if (id == sheet.rbBooked.getId()) return DbContract.RoomStatus.BOOKED;
        if (id == sheet.rbOccupied.getId()) return DbContract.RoomStatus.OCCUPIED;
        if (id == sheet.rbCleaning.getId()) return DbContract.RoomStatus.CLEANING;
        if (id == sheet.rbMaintenance.getId()) return DbContract.RoomStatus.MAINTENANCE;
        return null;
    }

    private void updateRoomStatus(int roomId, String newStatus) {
        executor.execute(() -> {
            boolean ok;
            try {
                ok = dao.updateStatus(roomId, newStatus) > 0;
            } catch (Exception e) {
                ok = false;
            }
            final boolean fOk = ok;
            mainHandler.post(() -> {
                if (binding == null) return;
                showMessage(fOk ? R.string.admin_room_status_update_success
                        : R.string.admin_room_status_update_error);
                if (fOk) loadData(); // đổi trạng thái làm thay đổi số đếm chip
            });
        });
    }

    // ---------------- XÓA (long-press) ----------------

    @Override
    public void onLongClick(@NonNull RoomWithType item) {
        final int roomId = item.getRoom().getId();
        final String[] actions = {
                getString(R.string.action_edit),
                getString(R.string.action_delete)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getRoom().getRoomNumber())
                .setItems(actions, (d, which) -> {
                    if (which == 0) openForm(roomId);
                    else confirmDelete(roomId);
                })
                .show();
    }

    private void confirmDelete(final int roomId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_room_delete_title)
                .setMessage(R.string.admin_room_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete,
                        (d, which) -> deleteRoom(roomId))
                .show();
    }

    private void deleteRoom(final int roomId) {
        executor.execute(() -> {
            int result;
            try {
                // Kiểm tra TRẠNG THÁI MỚI NHẤT từ DB ngay trước khi xóa (không dùng dữ liệu list đã cũ)
                Room fresh = dao.getById(roomId);
                String status = fresh != null ? fresh.getRoomStatus() : null;
                if (DbContract.RoomStatus.OCCUPIED.equals(status) || DbContract.RoomStatus.BOOKED.equals(status)) {
                    result = DELETE_BLOCKED;
                } else {
                    result = dao.delete(roomId) > 0 ? DELETE_OK : DELETE_ERROR;
                }
            } catch (Exception e) {
                result = DELETE_ERROR;
            }
            final int fResult = result;

            mainHandler.post(() -> {
                if (binding == null) return;
                if (fResult == DELETE_OK) {
                    showMessage(R.string.admin_room_delete_success);
                    loadData();
                } else if (fResult == DELETE_BLOCKED) {
                    showMessage(R.string.admin_room_delete_blocked);
                } else {
                    showMessage(R.string.admin_room_delete_error);
                }
            });
        });
    }
}
