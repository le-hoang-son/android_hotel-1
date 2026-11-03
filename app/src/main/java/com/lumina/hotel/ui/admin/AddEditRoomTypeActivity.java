package com.lumina.hotel.ui.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.AmenityDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.model.Amenity;
import com.lumina.hotel.model.RoomType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SPEC 09 (màn phụ) — Thêm / Sửa loại phòng.
 * Không truyền EXTRA_ROOM_TYPE_ID (hoặc = 0) → chế độ THÊM; có id → chế độ SỬA.
 */
public class AddEditRoomTypeActivity extends AppCompatActivity {

    /** Truyền id loại phòng cần sửa; không truyền = chế độ thêm mới. */
    public static final String EXTRA_ROOM_TYPE_ID = "extra_room_type_id";

    private static final String KEY_AMENITIES = "state_selected_amenities";
    private static final long PREVIEW_DELAY_MS = 400;

    private View llRoot;
    private View llFormHeader;
    private View llBottomBar;
    private TextView tvFormTitle;
    private ImageButton btnBack;
    private MaterialButton btnCancel;
    private MaterialButton btnSave;
    private TextInputLayout tilName;
    private TextInputEditText edtName;
    private TextInputLayout tilArea;
    private TextInputEditText edtArea;
    private TextInputLayout tilMaxGuests;
    private TextInputEditText edtMaxGuests;
    private TextInputLayout tilPrice;
    private TextInputEditText edtPrice;
    private TextInputLayout tilTotalRooms;
    private TextInputEditText edtTotalRooms;
    private TextInputLayout tilImageUrl;
    private TextInputEditText edtImageUrl;
    private TextInputEditText edtDescription;
    private ImageView ivImagePreview;
    private ChipGroup cgAmenities;
    private SwitchMaterial swActive;

    private RoomTypeDao roomTypeDao;
    private AmenityDao amenityDao;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int editingId;                 // 0 = thêm mới
    private RoomType editingType;          // loại phòng đang sửa (đã tải xong), null nếu thêm mới
    private boolean restoredFromState;     // true khi vừa xoay màn hình → không ghi đè chữ người dùng đã nhập
    private List<Integer> restoredIds;     // tiện nghi đã tick trước khi xoay
    private boolean amenitiesLoaded = false;

    private final Runnable previewRunnable = this::loadPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_room_type);

        llRoot = findViewById(R.id.llRoot);
        llFormHeader = findViewById(R.id.llFormHeader);
        llBottomBar = findViewById(R.id.llBottomBar);
        tvFormTitle = findViewById(R.id.tvFormTitle);
        btnBack = findViewById(R.id.btnBack);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
        tilName = findViewById(R.id.tilName);
        edtName = findViewById(R.id.edtName);
        tilArea = findViewById(R.id.tilArea);
        edtArea = findViewById(R.id.edtArea);
        tilMaxGuests = findViewById(R.id.tilMaxGuests);
        edtMaxGuests = findViewById(R.id.edtMaxGuests);
        tilPrice = findViewById(R.id.tilPrice);
        edtPrice = findViewById(R.id.edtPrice);
        tilTotalRooms = findViewById(R.id.tilTotalRooms);
        edtTotalRooms = findViewById(R.id.edtTotalRooms);
        tilImageUrl = findViewById(R.id.tilImageUrl);
        edtImageUrl = findViewById(R.id.edtImageUrl);
        edtDescription = findViewById(R.id.edtDescription);
        ivImagePreview = findViewById(R.id.ivImagePreview);
        cgAmenities = findViewById(R.id.cgAmenities);
        swActive = findViewById(R.id.swActive);

        roomTypeDao = new RoomTypeDao(getApplicationContext());
        amenityDao = new AmenityDao(getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        editingId = getIntent().getIntExtra(EXTRA_ROOM_TYPE_ID, 0);
        restoredFromState = savedInstanceState != null;
        if (savedInstanceState != null) {
            restoredIds = savedInstanceState.getIntegerArrayList(KEY_AMENITIES);
        }

        tvFormTitle.setText(editingId > 0
                ? R.string.admin_room_type_form_title_edit
                : R.string.admin_room_type_form_title_add);

        setupInsets();
        setupListeners();
        loadInitialData();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (amenitiesLoaded) {
            outState.putIntegerArrayList(KEY_AMENITIES, new ArrayList<>(getSelectedAmenityIds()));
        }
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
            llBottomBar.setPadding(0, 0, 0, imeShown ? 0 : nav.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(llRoot);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> attemptSave());

        clearErrorOnType(tilName, edtName);
        clearErrorOnType(tilArea, edtArea);
        clearErrorOnType(tilMaxGuests, edtMaxGuests);
        clearErrorOnType(tilPrice, edtPrice);
        clearErrorOnType(tilTotalRooms, edtTotalRooms);
        clearErrorOnType(tilImageUrl, edtImageUrl);

        // Xem trước ảnh: đợi người dùng ngừng gõ 400ms rồi mới tải
        edtImageUrl.addTextChangedListener(new AfterTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mainHandler.removeCallbacks(previewRunnable);
                mainHandler.postDelayed(previewRunnable, PREVIEW_DELAY_MS);
            }
        });
    }

    private void loadPreview() {
        if (isDestroyed() || isFinishing()) return;
        String url = textOf(edtImageUrl);
        if (url.toLowerCase().startsWith("http")) {
            Glide.with(this).load(url).centerCrop().into(ivImagePreview);
        } else {
            Glide.with(this).clear(ivImagePreview);
            ivImagePreview.setImageDrawable(null); // hiện lại nền xám
        }
    }

    private void clearErrorOnType(final TextInputLayout til, TextInputEditText edt) {
        edt.addTextChangedListener(new AfterTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                til.setError(null);
            }
        });
    }

    // =========================================================
    // TẢI DỮ LIỆU BAN ĐẦU (chạy nền)
    // =========================================================

    private void loadInitialData() {
        executor.execute(() -> {
            final List<Amenity> amenities = new ArrayList<>();
            final List<Integer> dbIds = new ArrayList<>();
            RoomType loaded = null;
            try {
                amenities.addAll(amenityDao.getAll());
                if (editingId > 0) {
                    loaded = roomTypeDao.getById(editingId);
                    dbIds.addAll(amenityDao.getAmenityIdsOfType(editingId));
                }
            } catch (Exception ignored) {
                // loaded vẫn null → chế độ sửa sẽ báo "không tìm thấy"
            }
            final RoomType fLoaded = loaded;

            mainHandler.post(() -> {
                if (isDestroyed() || isFinishing()) return;

                if (editingId > 0 && fLoaded == null) {
                    Toast.makeText(getApplicationContext(),
                            R.string.admin_room_type_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                editingType = fLoaded;
                if (fLoaded != null && !restoredFromState) populateForm(fLoaded);

                buildAmenityChips(amenities, restoredIds != null ? restoredIds : dbIds);
            });
        });
    }

    private void populateForm(RoomType t) {
        edtName.setText(t.getTypeName());
        edtDescription.setText(t.getDescription());
        edtArea.setText(String.valueOf(t.getAreaM2()));
        edtMaxGuests.setText(String.valueOf(t.getMaxGuests()));
        edtPrice.setText(String.valueOf(t.getBasePrice()));
        edtTotalRooms.setText(String.valueOf(t.getTotalRooms()));
        edtImageUrl.setText(t.getImageUrl());
        swActive.setChecked(t.isActive());
    }

    private void buildAmenityChips(List<Amenity> amenities, List<Integer> selectedIds) {
        cgAmenities.removeAllViews();
        for (Amenity a : amenities) {
            Chip chip = (Chip) getLayoutInflater()
                    .inflate(R.layout.item_chip_amenity, cgAmenities, false);
            chip.setText(a.getAmenityName());
            chip.setTag(a.getId());
            chip.setChecked(selectedIds.contains(a.getId()));
            cgAmenities.addView(chip);
        }
        amenitiesLoaded = true;
    }

    private List<Integer> getSelectedAmenityIds() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < cgAmenities.getChildCount(); i++) {
            View child = cgAmenities.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                ids.add((Integer) child.getTag());
            }
        }
        return ids;
    }

    // =========================================================
    // LƯU
    // =========================================================

    private void attemptSave() {
        // Đang ở chế độ sửa mà dữ liệu chưa tải xong → chặn, tránh vô tình tạo bản ghi mới
        if (editingId > 0 && editingType == null) return;

        View firstInvalid = null;

        String name = textOf(edtName);
        if (name.isEmpty()) {
            tilName.setError(getString(R.string.error_field_required));
            firstInvalid = edtName;
        }

        long area = readPositive(edtArea, tilArea, 9999);
        if (area < 0 && firstInvalid == null) firstInvalid = edtArea;

        long guests = readPositive(edtMaxGuests, tilMaxGuests, 99);
        if (guests < 0 && firstInvalid == null) firstInvalid = edtMaxGuests;

        long price = readPositive(edtPrice, tilPrice, 999_999_999_999L);
        if (price < 0 && firstInvalid == null) firstInvalid = edtPrice;

        long rooms = readPositive(edtTotalRooms, tilTotalRooms, 9999);
        if (rooms < 0 && firstInvalid == null) firstInvalid = edtTotalRooms;

        String imageUrl = textOf(edtImageUrl);
        if (!imageUrl.isEmpty() && !imageUrl.toLowerCase().startsWith("http")) {
            tilImageUrl.setError(getString(R.string.admin_room_type_error_image_url));
            if (firstInvalid == null) firstInvalid = edtImageUrl;
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
            return;
        }

        final RoomType rt = (editingType != null) ? editingType : new RoomType();
        rt.setTypeName(name);
        rt.setDescription(textOf(edtDescription));
        rt.setAreaM2((int) area);
        rt.setMaxGuests((int) guests);
        rt.setBasePrice(price);
        rt.setTotalRooms((int) rooms);
        rt.setImageUrl(imageUrl);
        rt.setActive(swActive.isChecked());

        final List<Integer> selectedIds = getSelectedAmenityIds();
        final boolean isEdit = editingId > 0;

        btnSave.setEnabled(false);
        executor.execute(() -> {
            boolean ok;
            try {
                int typeId;
                if (isEdit) {
                    ok = roomTypeDao.update(rt) > 0;
                    typeId = editingId;
                } else {
                    long newId = roomTypeDao.insert(rt);
                    ok = newId > 0;
                    typeId = (int) newId;
                }
                if (ok) amenityDao.setAmenitiesForType(typeId, selectedIds);
            } catch (Exception e) {
                ok = false;
            }
            final boolean fOk = ok;

            mainHandler.post(() -> {
                if (isDestroyed()) return;
                btnSave.setEnabled(true);
                if (fOk) {
                    Toast.makeText(getApplicationContext(),
                            isEdit ? R.string.admin_room_type_save_updated
                                    : R.string.admin_room_type_save_added,
                            Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Snackbar.make(llRoot,
                            R.string.admin_room_type_save_error, Snackbar.LENGTH_LONG).show();
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

    /** Đọc số nguyên dương trong (0, max]; sai thì hiện lỗi ở ô đó và trả -1. */
    private long readPositive(TextInputEditText edt, TextInputLayout til, long max) {
        String s = textOf(edt);
        if (s.isEmpty()) {
            til.setError(getString(R.string.error_field_required));
            return -1;
        }
        try {
            long v = Long.parseLong(s);
            if (v <= 0 || v > max) {
                til.setError(getString(R.string.admin_room_type_error_positive_int));
                return -1;
            }
            return v;
        } catch (NumberFormatException e) {
            til.setError(getString(R.string.admin_room_type_error_positive_int));
            return -1;
        }
    }

    private abstract static class AfterTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
    }
}
