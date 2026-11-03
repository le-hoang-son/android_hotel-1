package com.lumina.hotel.ui.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lumina.hotel.R;
import com.lumina.hotel.data.DbContract;
import com.lumina.hotel.data.dao.RoomDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.model.Room;
import com.lumina.hotel.model.RoomType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SPEC 10 (màn phụ) — Thêm / Sửa phòng vật lý.
 * Không truyền EXTRA_ROOM_ID (hoặc = 0) → chế độ THÊM; có id → chế độ SỬA.
 */
public class AddEditRoomActivity extends AppCompatActivity {

    /** Truyền id phòng cần sửa; không truyền = chế độ thêm mới. */
    public static final String EXTRA_ROOM_ID = "extra_room_id";

    /** Thứ tự cố định 5 trạng thái phòng, dùng chung cho Spinner + tra cứu ngược. */
    private static final String[] STATUS_VALUES = {
            DbContract.RoomStatus.AVAILABLE,
            DbContract.RoomStatus.BOOKED,
            DbContract.RoomStatus.OCCUPIED,
            DbContract.RoomStatus.CLEANING,
            DbContract.RoomStatus.MAINTENANCE
    };

    private LinearLayout llRoot;
    private LinearLayout llFormHeader;
    private LinearLayout llBottomBar;
    private TextView tvFormTitle;
    private ImageButton btnBack;
    private MaterialButton btnCancel;
    private MaterialButton btnSave;
    private TextInputLayout tilRoomNumber;
    private TextInputEditText edtRoomNumber;
    private TextInputLayout tilFloor;
    private TextInputEditText edtFloor;
    private TextInputEditText edtNote;
    private Spinner spRoomType;
    private Spinner spStatus;

    private RoomDao roomDao;
    private RoomTypeDao roomTypeDao;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int editingId;              // 0 = thêm mới
    private Room editingRoom;           // phòng đang sửa (đã tải xong), null nếu thêm mới
    private List<RoomType> roomTypes = new ArrayList<>(); // danh sách loại phòng đang hoạt động, song song với vị trí Spinner

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_room);

        llRoot = findViewById(R.id.llRoot);
        llFormHeader = findViewById(R.id.llFormHeader);
        llBottomBar = findViewById(R.id.llBottomBar);
        tvFormTitle = findViewById(R.id.tvFormTitle);
        btnBack = findViewById(R.id.btnBack);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
        tilRoomNumber = findViewById(R.id.tilRoomNumber);
        edtRoomNumber = findViewById(R.id.edtRoomNumber);
        tilFloor = findViewById(R.id.tilFloor);
        edtFloor = findViewById(R.id.edtFloor);
        edtNote = findViewById(R.id.edtNote);
        spRoomType = findViewById(R.id.spRoomType);
        spStatus = findViewById(R.id.spStatus);

        roomDao = new RoomDao(getApplicationContext());
        roomTypeDao = new RoomTypeDao(getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        editingId = getIntent().getIntExtra(EXTRA_ROOM_ID, 0);

        tvFormTitle.setText(editingId > 0
                ? R.string.admin_room_form_title_edit
                : R.string.admin_room_form_title_add);

        setupInsets();
        setupStatusSpinner();
        setupListeners();
        loadInitialData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (executor != null) executor.shutdownNow();
    }

    // =========================================================
    // GIAO DIỆN
    // =========================================================

    /** SDK 37 (edge-to-edge): cộng status bar vào header, thanh điều hướng/bàn phím vào thanh nút dưới. */
    private void setupInsets() {
        final int headerBaseTop = llFormHeader.getPaddingTop();
        final int barL = llBottomBar.getPaddingLeft();
        final int barT = llBottomBar.getPaddingTop();
        final int barR = llBottomBar.getPaddingRight();
        final int barB = llBottomBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(llRoot, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            llFormHeader.setPadding(llFormHeader.getPaddingLeft(),
                    headerBaseTop + sb.top,
                    llFormHeader.getPaddingRight(),
                    llFormHeader.getPaddingBottom());

            boolean imeShown = ime.bottom > 0;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    imeShown ? ime.bottom : 0);
            // Giữ nguyên padding 16dp gốc của thanh nút, chỉ cộng thêm nav bar khi bàn phím đang ẩn
            llBottomBar.setPadding(barL, barT, barR, barB + (imeShown ? 0 : nav.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(llRoot);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> attemptSave());

        clearErrorOnType(tilRoomNumber, edtRoomNumber);
        clearErrorOnType(tilFloor, edtFloor);
    }

    private void clearErrorOnType(final TextInputLayout til, TextInputEditText edt) {
        edt.addTextChangedListener(new AfterTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                til.setError(null);
            }
        });
    }

    /** Spinner Trạng thái không phụ thuộc CSDL, dựng ngay ở onCreate; mặc định "Trống" khi thêm mới. */
    private void setupStatusSpinner() {
        String[] labels = {
                getString(R.string.room_status_available),
                getString(R.string.room_status_booked),
                getString(R.string.room_status_occupied),
                getString(R.string.room_status_cleaning),
                getString(R.string.room_status_maintenance)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStatus.setAdapter(adapter);
        spStatus.setSelection(0); // "available" — chỉ có ý nghĩa ở chế độ thêm mới, chế độ sửa sẽ ghi đè ở populateForm()
    }

    // =========================================================
    // TẢI DỮ LIỆU BAN ĐẦU (chạy nền)
    // =========================================================

    private void loadInitialData() {
        executor.execute(() -> {
            final List<RoomType> types = new ArrayList<>();
            Room loaded = null;
            try {
                types.addAll(roomTypeDao.getActiveTypes());
                if (editingId > 0) {
                    loaded = roomDao.getById(editingId);
                }
            } catch (Exception ignored) {
                // loaded vẫn null → chế độ sửa sẽ báo "không tìm thấy"
            }
            final Room fLoaded = loaded;

            mainHandler.post(() -> {
                if (isDestroyed() || isFinishing()) return;

                if (editingId > 0 && fLoaded == null) {
                    Toast.makeText(getApplicationContext(),
                            R.string.admin_room_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                roomTypes = types;
                setupTypeSpinner();

                editingRoom = fLoaded;
                if (fLoaded != null) populateForm(fLoaded);
            });
        });
    }

    /** Spinner Loại phòng phụ thuộc dữ liệu tải từ DB nên dựng sau khi có danh sách. */
    private void setupTypeSpinner() {
        List<String> names = new ArrayList<>();
        for (RoomType t : roomTypes) names.add(t.getTypeName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRoomType.setAdapter(adapter);
    }

    private void populateForm(Room r) {
        edtRoomNumber.setText(r.getRoomNumber());
        edtFloor.setText(String.valueOf(r.getFloor()));
        edtNote.setText(r.getNote());

        for (int i = 0; i < roomTypes.size(); i++) {
            if (roomTypes.get(i).getId() == r.getRoomTypeId()) {
                spRoomType.setSelection(i);
                break;
            }
        }
        for (int i = 0; i < STATUS_VALUES.length; i++) {
            if (STATUS_VALUES[i].equals(r.getRoomStatus())) {
                spStatus.setSelection(i);
                break;
            }
        }
    }

    // =========================================================
    // LƯU
    // =========================================================

    private void attemptSave() {
        // Đang ở chế độ sửa mà dữ liệu chưa tải xong → chặn, tránh vô tình tạo bản ghi mới
        if (editingId > 0 && editingRoom == null) return;

        View firstInvalid = null;

        final String roomNumber = textOf(edtRoomNumber).toUpperCase(java.util.Locale.ROOT);
        if (roomNumber.isEmpty()) {
            tilRoomNumber.setError(getString(R.string.error_field_required));
            firstInvalid = edtRoomNumber;
        }

        int floor = -1;
        String floorStr = textOf(edtFloor);
        if (floorStr.isEmpty()) {
            tilFloor.setError(getString(R.string.error_field_required));
            if (firstInvalid == null) firstInvalid = edtFloor;
        } else {
            try {
                floor = Integer.parseInt(floorStr);
                if (floor < 1 || floor > 20) {
                    tilFloor.setError(getString(R.string.admin_room_error_floor_range));
                    floor = -1;
                    if (firstInvalid == null) firstInvalid = edtFloor;
                }
            } catch (NumberFormatException e) {
                tilFloor.setError(getString(R.string.admin_room_error_floor_range));
                if (firstInvalid == null) firstInvalid = edtFloor;
            }
        }

        if (roomTypes.isEmpty()) {
            // Không có loại phòng nào đang hoạt động → không thể chọn, chặn lưu
            Snackbar.make(llRoot, R.string.admin_room_type_empty_title, Snackbar.LENGTH_LONG).show();
            return;
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
            return;
        }

        final String note = textOf(edtNote);
        final int typeId = roomTypes.get(spRoomType.getSelectedItemPosition()).getId();
        final String status = STATUS_VALUES[spStatus.getSelectedItemPosition()];
        final int fFloor = floor;
        final boolean isEdit = editingId > 0;
        final int excludeId = isEdit ? editingId : 0;

        btnSave.setEnabled(false);
        executor.execute(() -> {
            boolean numberExists = roomDao.isRoomNumberExists(roomNumber, excludeId);
            if (numberExists) {
                mainHandler.post(() -> {
                    if (isDestroyed()) return;
                    btnSave.setEnabled(true);
                    tilRoomNumber.setError(getString(R.string.admin_room_error_number_exists));
                    edtRoomNumber.requestFocus();
                });
                return;
            }

            final Room r = (editingRoom != null) ? editingRoom : new Room();
            r.setRoomNumber(roomNumber);
            r.setFloor(fFloor);
            r.setRoomTypeId(typeId);
            r.setRoomStatus(status);
            r.setNote(note);

            boolean ok;
            try {
                if (isEdit) {
                    ok = roomDao.update(r) > 0;
                } else {
                    ok = roomDao.insert(r) > 0;
                }
            } catch (Exception e) {
                ok = false;
            }
            final boolean fOk = ok;

            mainHandler.post(() -> {
                if (isDestroyed()) return;
                btnSave.setEnabled(true);
                if (fOk) {
                    Toast.makeText(getApplicationContext(),
                            isEdit ? R.string.admin_room_save_updated
                                    : R.string.admin_room_save_added,
                            Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Snackbar.make(llRoot,
                            R.string.admin_room_save_error, Snackbar.LENGTH_LONG).show();
                }
            });
        });
    }

    // =========================================================
    // TIỆN ÍCH
    // =========================================================

    private String textOf(TextInputEditText edt) {
        return edt.getText() == null ? "" : edt.getText().toString().trim();
    }

    private abstract static class AfterTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
    }
}
