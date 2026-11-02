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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.AdminRoomTypeAdapter;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.databinding.FragmentAdminRoomTypeBinding;
import com.lumina.hotel.model.RoomType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SPEC 09 — Tab "Loại phòng" của Admin: danh sách + thống kê + xóa. */
public class AdminRoomTypeFragment extends Fragment
        implements AdminRoomTypeAdapter.OnActionListener {

    private static final int DELETE_OK = 0;
    private static final int DELETE_BLOCKED = 1;
    private static final int DELETE_ERROR = 2;
    private static final int DELETE_BLOCKED_HISTORY = 3;

    private FragmentAdminRoomTypeBinding binding;
    private RoomTypeDao dao;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AdminRoomTypeAdapter adapter;

    private int headerPaddingTop;
    private int loadToken = 0; // bỏ qua kết quả của lần tải cũ

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminRoomTypeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dao = new RoomTypeDao(requireContext().getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        setupHeaderInset();

        adapter = new AdminRoomTypeAdapter(this);
        binding.lvRoomTypes.setAdapter(adapter);

        binding.layoutEmpty.tvEmptyTitle.setText(R.string.admin_room_type_empty_title);
        binding.layoutEmpty.tvEmptyDesc.setText(R.string.admin_room_type_empty_desc);
        binding.layoutEmpty.btnEmptyAction.setVisibility(View.GONE);

        binding.btnAddNew.setOnClickListener(v -> openForm(0));
    }

    /** Thêm/sửa xong quay về → tải lại danh sách. */
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

    /** App nhắm SDK 37 (edge-to-edge): cộng chiều cao status bar vào padding trên của header. */
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

    // ---------------- TẢI DỮ LIỆU (chạy nền) ----------------

    private void loadData() {
        final int token = ++loadToken;
        // Chỉ hiện vòng xoay ở lần tải đầu, tránh nháy mỗi lần quay về
        if (adapter.getCount() == 0) binding.pbLoading.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            final List<RoomType> types = new ArrayList<>();
            final int[] stats = new int[2]; // [0] = số loại, [1] = tổng số phòng
            boolean failed = false;
            try {
                types.addAll(dao.getAll());
                stats[0] = dao.countTypes();
                stats[1] = dao.sumTotalRooms();
            } catch (Exception e) {
                failed = true;
            }
            final boolean fFailed = failed;

            mainHandler.post(() -> {
                if (binding == null || token != loadToken) return;
                binding.pbLoading.setVisibility(View.GONE);
                if (fFailed) {
                    showMessage(R.string.admin_room_type_load_error);
                    return;
                }
                binding.tvStatTypesValue.setText(String.valueOf(stats[0]));
                binding.tvStatRoomsValue.setText(String.valueOf(stats[1]));
                adapter.setData(types);

                boolean empty = types.isEmpty();
                binding.layoutEmpty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.lvRoomTypes.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    // ---------------- THÊM / SỬA ----------------

    /** roomTypeId = 0 → thêm mới; khác 0 → sửa loại phòng đó. */
    private void openForm(int roomTypeId) {
        Intent intent = new Intent(requireContext(), AddEditRoomTypeActivity.class);
        if (roomTypeId > 0) {
            intent.putExtra(AddEditRoomTypeActivity.EXTRA_ROOM_TYPE_ID, roomTypeId);
        }
        startActivity(intent);
    }

    @Override
    public void onEdit(RoomType roomType) {
        openForm(roomType.getId());
    }

    // ---------------- XÓA ----------------

    @Override
    public void onDelete(RoomType roomType) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_room_type_delete_title)
                .setMessage(R.string.admin_room_type_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete,
                        (d, which) -> deleteRoomType(roomType.getId()))
                .show();
    }

    private void deleteRoomType(final int typeId) {
        executor.execute(() -> {
            int result;
            try {
                if (dao.hasActiveBookings(typeId)) {
                    result = DELETE_BLOCKED; // đang có đơn pending/confirmed/checked_in
                } else if (dao.hasAnyBookings(typeId)) {
                    result = DELETE_BLOCKED_HISTORY; // chỉ còn lịch sử đơn → không cho xóa (CASCADE)
                } else {
                    result = dao.delete(typeId) > 0 ? DELETE_OK : DELETE_ERROR;
                }
            } catch (Exception e) {
                result = DELETE_ERROR;
            }
            final int fResult = result;

            mainHandler.post(() -> {
                if (binding == null) return;
                if (fResult == DELETE_OK) {
                    showMessage(R.string.admin_room_type_delete_success);
                    loadData();
                } else if (fResult == DELETE_BLOCKED) {
                    showMessage(R.string.admin_room_type_delete_blocked);
                } else if (fResult == DELETE_BLOCKED_HISTORY) {
                    showMessage(R.string.admin_room_type_delete_blocked_history);
                } else {
                    showMessage(R.string.admin_room_type_delete_error);
                }
            });
        });
    }
}
