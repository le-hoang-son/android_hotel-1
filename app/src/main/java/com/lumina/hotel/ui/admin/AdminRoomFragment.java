package com.lumina.hotel.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.AdminRoomAdapter;
import com.lumina.hotel.data.DbContract;
import com.lumina.hotel.data.dao.RoomDao;
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

    private View rootView;
    private GridView gvRooms;
    private View layoutEmpty;
    private Button btnEmptyAction;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private MaterialButton btnAddRoom;
    private ImageButton btnFilter;
    private LinearLayout llAdminHeader;
    private ChipGroup cgRoomStatus;
    private Chip chipAll;
    private Chip chipAvailable;
    private Chip chipBooked;
    private Chip chipOccupied;
    private Chip chipCleaning;
    private Chip chipMaintenance;
    private ChipGroup cgActiveFilters;
    private View svActiveFilters;
    private View cvFilterBadge;
    private View pbLoading;

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
        rootView = inflater.inflate(R.layout.fragment_admin_room, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        gvRooms = view.findViewById(R.id.gvRooms);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        btnEmptyAction = layoutEmpty.findViewById(R.id.btnEmptyAction);
        tvEmptyTitle = layoutEmpty.findViewById(R.id.tvEmptyTitle);
        tvEmptyDesc = layoutEmpty.findViewById(R.id.tvEmptyDesc);
        btnAddRoom = view.findViewById(R.id.btnAddRoom);
        btnFilter = view.findViewById(R.id.btnFilter);
        llAdminHeader = view.findViewById(R.id.llAdminHeader);
        cgRoomStatus = view.findViewById(R.id.cgRoomStatus);
        chipAll = view.findViewById(R.id.chipAll);
        chipAvailable = view.findViewById(R.id.chipAvailable);
        chipBooked = view.findViewById(R.id.chipBooked);
        chipOccupied = view.findViewById(R.id.chipOccupied);
        chipCleaning = view.findViewById(R.id.chipCleaning);
        chipMaintenance = view.findViewById(R.id.chipMaintenance);
        cgActiveFilters = view.findViewById(R.id.cgActiveFilters);
        svActiveFilters = view.findViewById(R.id.svActiveFilters);
        cvFilterBadge = view.findViewById(R.id.cvFilterBadge);
        pbLoading = view.findViewById(R.id.pbLoading);

        dao = new RoomDao(requireContext().getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        setupHeaderInset();

        adapter = new AdminRoomAdapter();
        adapter.setOnItemClickListener(item -> openStatusSheet(item));
        adapter.setOnRoomLongClickListener(this);
        gvRooms.setAdapter(adapter);

        roomFilter = RoomFilter.load(requireContext());

        btnEmptyAction.setVisibility(View.GONE);

        setupFilterChips();

        btnAddRoom.setOnClickListener(v -> openForm(0));
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
        gvRooms = null;
        layoutEmpty = null;
        btnEmptyAction = null;
        tvEmptyTitle = null;
        tvEmptyDesc = null;
        btnAddRoom = null;
        btnFilter = null;
        llAdminHeader = null;
        cgRoomStatus = null;
        chipAll = null;
        chipAvailable = null;
        chipBooked = null;
        chipOccupied = null;
        chipCleaning = null;
        chipMaintenance = null;
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
        cgRoomStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return; // selectionRequired=true nên không xảy ra
            int id = checkedIds.get(0);
            if (id == chipAll.getId()) currentFilter = "all";
            else if (id == chipAvailable.getId()) currentFilter = DbContract.RoomStatus.AVAILABLE;
            else if (id == chipBooked.getId()) currentFilter = DbContract.RoomStatus.BOOKED;
            else if (id == chipOccupied.getId()) currentFilter = DbContract.RoomStatus.OCCUPIED;
            else if (id == chipCleaning.getId()) currentFilter = DbContract.RoomStatus.CLEANING;
            else if (id == chipMaintenance.getId()) currentFilter = DbContract.RoomStatus.MAINTENANCE;
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
        if (rootView != null) loadData(); // đổi loại/tầng → phải tải lại cả số đếm lẫn danh sách
    }

    /** Vẽ lại hàng chip "đang áp dụng" + chấm badge trên icon phễu. */
    private void renderActiveFilters() {
        boolean active = roomFilter.isActive();
        cgActiveFilters.removeAllViews();
        svActiveFilters.setVisibility(active ? View.VISIBLE : View.GONE);
        cvFilterBadge.setVisibility(active ? View.VISIBLE : View.GONE);
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
        roomFilter.save(requireContext());
        loadData();
    }

    // ---------------- TẢI DỮ LIỆU (chạy nền) ----------------

    /** Tải cả số đếm (cho chip) lẫn danh sách theo filter hiện tại. Gọi khi mở màn / sau khi đổi trạng thái / xóa. */
    private void loadData() {
        final int token = ++loadToken;
        final RoomFilter filter = roomFilter.copy(); // chụp bản sao để luồng nền không đọc dữ liệu đang bị sửa
        if (adapter.getCount() == 0) pbLoading.setVisibility(View.VISIBLE);

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
                if (rootView == null || token != loadToken) return;
                pbLoading.setVisibility(View.GONE);
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
                if (rootView == null || token != loadToken) return;
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
            tvEmptyTitle.setText(filtering
                    ? R.string.admin_room_filter_empty_title : R.string.admin_room_empty_title);
            tvEmptyDesc.setText(filtering
                    ? R.string.admin_room_filter_empty_desc : R.string.admin_room_empty_desc);
        }
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        gvRooms.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void applyChipCounts(Map<String, Integer> counts) {
        int all = counts.containsKey("all") ? counts.get("all") : 0;
        chipAll.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_all), all));
        chipAvailable.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_available), countOf(counts, DbContract.RoomStatus.AVAILABLE)));
        chipBooked.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_booked), countOf(counts, DbContract.RoomStatus.BOOKED)));
        chipOccupied.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_occupied), countOf(counts, DbContract.RoomStatus.OCCUPIED)));
        chipCleaning.setText(getString(R.string.admin_room_filter_format,
                getString(R.string.admin_room_filter_cleaning), countOf(counts, DbContract.RoomStatus.CLEANING)));
        chipMaintenance.setText(getString(R.string.admin_room_filter_format,
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
        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_room_status, null, false);
        dialog.setContentView(sheet);

        TextView tvSheetTitle = sheet.findViewById(R.id.tvSheetTitle);
        MaterialButton btnUpdateStatus = sheet.findViewById(R.id.btnUpdateStatus);

        tvSheetTitle.setText(getString(R.string.admin_room_status_sheet_title,
                item.getRoom().getRoomNumber()));
        checkRadioForStatus(sheet, item.getRoom().getRoomStatus());

        btnUpdateStatus.setOnClickListener(v -> {
            String newStatus = statusFromCheckedRadio(sheet);
            if (newStatus == null) return;
            dialog.dismiss();
            if (newStatus.equals(item.getRoom().getRoomStatus())) return; // không đổi gì, khỏi gọi DB
            updateRoomStatus(item.getRoom().getId(), newStatus);
        });

        dialog.show();
    }

    private void checkRadioForStatus(View sheet, String status) {
        if (status == null) return;
        if (status.equals(DbContract.RoomStatus.AVAILABLE)) ((RadioButton) sheet.findViewById(R.id.rbAvailable)).setChecked(true);
        else if (status.equals(DbContract.RoomStatus.BOOKED)) ((RadioButton) sheet.findViewById(R.id.rbBooked)).setChecked(true);
        else if (status.equals(DbContract.RoomStatus.OCCUPIED)) ((RadioButton) sheet.findViewById(R.id.rbOccupied)).setChecked(true);
        else if (status.equals(DbContract.RoomStatus.CLEANING)) ((RadioButton) sheet.findViewById(R.id.rbCleaning)).setChecked(true);
        else if (status.equals(DbContract.RoomStatus.MAINTENANCE)) ((RadioButton) sheet.findViewById(R.id.rbMaintenance)).setChecked(true);
    }

    private String statusFromCheckedRadio(View sheet) {
        RadioGroup rgRoomStatus = sheet.findViewById(R.id.rgRoomStatus);
        int id = rgRoomStatus.getCheckedRadioButtonId();
        if (id == R.id.rbAvailable) return DbContract.RoomStatus.AVAILABLE;
        if (id == R.id.rbBooked) return DbContract.RoomStatus.BOOKED;
        if (id == R.id.rbOccupied) return DbContract.RoomStatus.OCCUPIED;
        if (id == R.id.rbCleaning) return DbContract.RoomStatus.CLEANING;
        if (id == R.id.rbMaintenance) return DbContract.RoomStatus.MAINTENANCE;
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
                if (rootView == null) return;
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
                if (rootView == null) return;
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
