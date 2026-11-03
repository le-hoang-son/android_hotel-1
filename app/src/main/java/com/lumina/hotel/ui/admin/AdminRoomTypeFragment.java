package com.lumina.hotel.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lumina.hotel.R;
import com.lumina.hotel.adapter.AdminRoomTypeAdapter;
import com.lumina.hotel.data.dao.RoomTypeDao;
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

    private View rootView;
    private View llAdminHeader;
    private ListView lvRoomTypes;
    private TextView tvStatTypesValue;
    private TextView tvStatRoomsValue;
    private ProgressBar pbLoading;
    private View layoutEmpty;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private View btnEmptyAction;
    private MaterialButton btnAddNew;

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
        rootView = inflater.inflate(R.layout.fragment_admin_room_type, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        llAdminHeader = view.findViewById(R.id.llAdminHeader);
        lvRoomTypes = view.findViewById(R.id.lvRoomTypes);
        tvStatTypesValue = view.findViewById(R.id.tvStatTypesValue);
        tvStatRoomsValue = view.findViewById(R.id.tvStatRoomsValue);
        pbLoading = view.findViewById(R.id.pbLoading);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        tvEmptyTitle = layoutEmpty.findViewById(R.id.tvEmptyTitle);
        tvEmptyDesc = layoutEmpty.findViewById(R.id.tvEmptyDesc);
        btnEmptyAction = layoutEmpty.findViewById(R.id.btnEmptyAction);
        btnAddNew = view.findViewById(R.id.btnAddNew);

        dao = new RoomTypeDao(requireContext().getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        setupHeaderInset();

        adapter = new AdminRoomTypeAdapter(this);
        lvRoomTypes.setAdapter(adapter);

        tvEmptyTitle.setText(R.string.admin_room_type_empty_title);
        tvEmptyDesc.setText(R.string.admin_room_type_empty_desc);
        btnEmptyAction.setVisibility(View.GONE);

        btnAddNew.setOnClickListener(v -> openForm(0));
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
        rootView = null;
        llAdminHeader = null;
        lvRoomTypes = null;
        tvStatTypesValue = null;
        tvStatRoomsValue = null;
        pbLoading = null;
        layoutEmpty = null;
        tvEmptyTitle = null;
        tvEmptyDesc = null;
        btnEmptyAction = null;
        btnAddNew = null;
    }

    // ---------------- GIAO DIỆN ----------------

    /** App nhắm SDK 37 (edge-to-edge): cộng chiều cao status bar vào padding trên của header. */
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

    // ---------------- TẢI DỮ LIỆU (chạy nền) ----------------

    private void loadData() {
        final int token = ++loadToken;
        // Chỉ hiện vòng xoay ở lần tải đầu, tránh nháy mỗi lần quay về
        if (adapter.getCount() == 0) pbLoading.setVisibility(View.VISIBLE);

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
                if (rootView == null || token != loadToken) return;
                pbLoading.setVisibility(View.GONE);
                if (fFailed) {
                    showMessage(R.string.admin_room_type_load_error);
                    return;
                }
                tvStatTypesValue.setText(String.valueOf(stats[0]));
                tvStatRoomsValue.setText(String.valueOf(stats[1]));
                adapter.setData(types);

                boolean empty = types.isEmpty();
                layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                lvRoomTypes.setVisibility(empty ? View.GONE : View.VISIBLE);
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
                if (rootView == null) return;
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
