package com.lumina.hotel.ui.customer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.UserDao;
import com.lumina.hotel.model.User;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * SPEC 08 — Cá nhân.
 * Hiển thị/sửa hồ sơ (họ tên, SĐT, ngày sinh), đổi avatar (chọn ảnh từ thư viện,
 * copy vào bộ nhớ trong), đổi mật khẩu (dialog), đăng xuất.
 */
public class ProfileFragment extends Fragment {

    private View rootView;
    private View btnChangeAvatar;
    private TextView tvFullName;
    private TextView tvMemberSince;
    private ImageView ivAvatar;
    private TextView tvAvatarInitial;
    private TextInputLayout tilFullName;
    private TextInputEditText edtFullName;
    private TextInputEditText edtEmail;
    private TextInputLayout tilPhone;
    private TextInputEditText edtPhone;
    private TextInputLayout tilDob;
    private TextInputEditText edtDob;
    private MaterialButton btnSaveProfile;
    private MaterialButton btnChangePassword;
    private MaterialButton btnAboutHotel;
    private MaterialButton btnLogout;
    private ProgressBar pbLoading;

    private UserDao userDao;
    private SessionManager sessionManager;
    private User currentUser;
    private long dob;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_profile, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnChangeAvatar = view.findViewById(R.id.btnChangeAvatar);
        tvFullName = view.findViewById(R.id.tvFullName);
        tvMemberSince = view.findViewById(R.id.tvMemberSince);
        ivAvatar = view.findViewById(R.id.ivAvatar);
        tvAvatarInitial = view.findViewById(R.id.tvAvatarInitial);
        tilFullName = view.findViewById(R.id.tilFullName);
        edtFullName = view.findViewById(R.id.edtFullName);
        edtEmail = view.findViewById(R.id.edtEmail);
        tilPhone = view.findViewById(R.id.tilPhone);
        edtPhone = view.findViewById(R.id.edtPhone);
        tilDob = view.findViewById(R.id.tilDob);
        edtDob = view.findViewById(R.id.edtDob);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnAboutHotel = view.findViewById(R.id.btnAboutHotel);
        btnLogout = view.findViewById(R.id.btnLogout);
        pbLoading = view.findViewById(R.id.pbLoading);

        userDao = new UserDao(requireContext());
        sessionManager = new SessionManager(requireContext());

        setupListeners();
        loadUserData();
    }

    private void setupListeners() {
        btnChangeAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        edtDob.setOnClickListener(v -> openDobPicker());
        btnSaveProfile.setOnClickListener(v -> onSaveClicked());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnAboutHotel.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AboutActivity.class)));
        btnLogout.setOnClickListener(v -> showLogoutConfirm());
    }

    // ===================== TẢI DỮ LIỆU =====================

    private void loadUserData() {
        int userId = sessionManager.getUserId();
        AppExecutors.io(() -> {
            User user = userDao.getById(userId);
            AppExecutors.runOnUiSafe(getActivity(), () -> bindUser(user));
        });
    }

    private void bindUser(User user) {
        if (user == null || rootView == null) return;
        currentUser = user;

        tvFullName.setText(user.getFullName());
        tvMemberSince.setText(
                getString(R.string.profile_member_since, DateUtils.formatMonthYear(user.getCreatedAt())));

        edtFullName.setText(user.getFullName());
        edtEmail.setText(user.getEmail());
        edtPhone.setText(user.getPhone());

        bindDob(user.getDob());
        bindAvatar(user);
    }

    private void bindDob(long dobValue) {
        dob = dobValue;
        edtDob.setText(dob > 0 ? DateUtils.formatFull(dob) : "");
    }

    private void bindAvatar(User user) {
        String avatarUrl = user.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            tvAvatarInitial.setVisibility(View.VISIBLE);
            tvAvatarInitial.setText(user.getInitial());
            ivAvatar.setImageDrawable(null);
        } else {
            tvAvatarInitial.setVisibility(View.GONE);
            Glide.with(this)
                    .load(new File(avatarUrl))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .error(R.drawable.ic_error)
                    .into(ivAvatar);
        }
    }

    // ===================== NGÀY SINH =====================

    private void openDobPicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.profile_label_dob)
                .setCalendarConstraints(constraints)
                .setSelection(toUtcMillis(dob > 0 ? dob : DateUtils.today()))
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            dob = fromUtcMillis(selection);
            edtDob.setText(DateUtils.formatFull(dob));
            tilDob.setError(null);
        });

        picker.show(getParentFragmentManager(), "dob_picker");
    }

    /** MaterialDatePicker làm việc theo mốc UTC — quy đổi sang đúng ngày lịch ở múi giờ máy. */
    private long toUtcMillis(long localStartOfDay) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localStartOfDay);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private long fromUtcMillis(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        return DateUtils.atStartOfDay(
                new GregorianCalendar(
                        utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH)
                ).getTimeInMillis());
    }

    // ===================== LƯU HỒ SƠ =====================

    private void onSaveClicked() {
        if (currentUser == null) return;

        String fullName = edtFullName.getText() == null ? "" :
                edtFullName.getText().toString().trim();
        String phone = edtPhone.getText() == null ? "" :
                edtPhone.getText().toString().trim();

        boolean valid = true;

        if (fullName.isEmpty()) {
            tilFullName.setError(getString(R.string.profile_error_fullname_empty));
            valid = false;
        } else {
            tilFullName.setError(null);
        }

        if (!phone.matches("\\d{10}")) {
            tilPhone.setError(getString(R.string.profile_error_phone_invalid));
            valid = false;
        } else {
            tilPhone.setError(null);
        }

        if (dob > 0 && dob >= DateUtils.today()) {
            tilDob.setError(getString(R.string.profile_error_dob_future));
            valid = false;
        } else {
            tilDob.setError(null);
        }

        if (!valid) return;

        int userId = sessionManager.getUserId();
        long dobToSave = dob;
        setLoading(true);

        AppExecutors.io(() -> {
            userDao.updateProfile(userId, fullName, phone, dobToSave);
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                setLoading(false);
                sessionManager.updateName(fullName);
                currentUser.setFullName(fullName);
                currentUser.setPhone(phone);
                currentUser.setDob(dobToSave);
                tvFullName.setText(fullName);
                Snackbar.make(rootView, R.string.profile_snackbar_update_success, Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    private void setLoading(boolean loading) {
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveProfile.setEnabled(!loading);
    }

    // ===================== ĐỔI AVATAR =====================

    private void onImagePicked(Uri uri) {
        if (uri == null || rootView == null) return;

        Context appContext = requireContext().getApplicationContext();
        int userId = sessionManager.getUserId();

        AppExecutors.io(() -> {
            String path = copyImageToInternalStorage(appContext, uri, userId);
            if (path == null) {
                AppExecutors.runOnUiSafe(getActivity(), () ->
                        Snackbar.make(rootView, R.string.error_default_message, Snackbar.LENGTH_SHORT).show());
                return;
            }
            userDao.updateAvatar(userId, path);
            AppExecutors.runOnUiSafe(getActivity(), () -> {
                if (currentUser != null) currentUser.setAvatarUrl(path);
                if (currentUser != null) bindAvatar(currentUser);
            });
        });
    }

    private String copyImageToInternalStorage(Context context, Uri uri, int userId) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            File outFile = new File(context.getFilesDir(), "avatar_" + userId + ".jpg");
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // ===================== ĐỔI MẬT KHẨU =====================

    private void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null);
        TextInputEditText edtCurrentPassword = dialogView.findViewById(R.id.edtCurrentPassword);
        TextInputEditText edtNewPassword = dialogView.findViewById(R.id.edtNewPassword);
        TextInputEditText edtConfirmPassword = dialogView.findViewById(R.id.edtConfirmPassword);
        TextInputLayout tilCurrentPassword = dialogView.findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNewPassword = dialogView.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirmPassword = dialogView.findViewById(R.id.tilConfirmPassword);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_change_password_title)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.dialog_change_password_submit, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String current = edtCurrentPassword.getText() == null ? "" :
                    edtCurrentPassword.getText().toString();
            String newPass = edtNewPassword.getText() == null ? "" :
                    edtNewPassword.getText().toString();
            String confirm = edtConfirmPassword.getText() == null ? "" :
                    edtConfirmPassword.getText().toString();

            boolean valid = true;

            if (newPass.length() < 6) {
                tilNewPassword.setError(getString(R.string.dialog_change_password_error_new_short));
                valid = false;
            } else {
                tilNewPassword.setError(null);
            }
            if (!newPass.equals(confirm)) {
                tilConfirmPassword.setError(getString(R.string.dialog_change_password_error_confirm_mismatch));
                valid = false;
            } else {
                tilConfirmPassword.setError(null);
            }

            if (!valid) return;

            int userId = sessionManager.getUserId();
            AppExecutors.io(() -> {
                int rows = userDao.updatePassword(userId, current, newPass);
                AppExecutors.runOnUiSafe(getActivity(), () -> {
                    if (rows > 0) {
                        Snackbar.make(rootView, R.string.snackbar_change_password_success, Snackbar.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        tilCurrentPassword.setError(
                                getString(R.string.dialog_change_password_error_current_wrong));
                    }
                });
            });
        }));

        dialog.show();
    }

    // ===================== ĐĂNG XUẤT =====================

    private void showLogoutConfirm() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_menu_logout)
                .setMessage(R.string.profile_confirm_logout)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_logout, (d, which) -> doLogout())
                .show();
    }

    private void doLogout() {
        sessionManager.clear();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
        btnChangeAvatar = null;
        tvFullName = null;
        tvMemberSince = null;
        ivAvatar = null;
        tvAvatarInitial = null;
        tilFullName = null;
        edtFullName = null;
        edtEmail = null;
        tilPhone = null;
        edtPhone = null;
        tilDob = null;
        edtDob = null;
        btnSaveProfile = null;
        btnChangePassword = null;
        btnAboutHotel = null;
        btnLogout = null;
        pbLoading = null;
    }
}
