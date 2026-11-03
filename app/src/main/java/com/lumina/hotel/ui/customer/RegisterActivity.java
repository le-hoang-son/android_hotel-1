package com.lumina.hotel.ui.customer;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.lumina.hotel.util.GoogleAuthHelper;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lumina.hotel.R;
import com.lumina.hotel.data.DbContract;
import com.lumina.hotel.data.dao.UserDao;
import com.lumina.hotel.model.User;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.SessionManager;

/**
 * SPEC 01 — Đăng ký. Ghi user mới vào bảng users (role=customer, is_vip=false,
 * created_at=now), tự đăng nhập luôn sau khi đăng ký thành công.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private NestedScrollView svRegister;
    private TextInputLayout tilFullName;
    private TextInputEditText edtFullName;
    private TextInputLayout tilEmail;
    private TextInputEditText edtEmail;
    private TextInputLayout tilPhone;
    private TextInputEditText edtPhone;
    private TextInputLayout tilPassword;
    private TextInputEditText edtPassword;
    private TextInputLayout tilConfirmPassword;
    private TextInputEditText edtConfirmPassword;
    private CheckBox cbTerms;
    private TextView tvTerms;
    private TextView btnGoLogin;
    private TextView btnLoginLink;
    private MaterialButton btnGoogle;
    private MaterialButton btnRegister;
    private ProgressBar pbLoading;
    private UserDao userDao;
    private SessionManager sessionManager;
    private GoogleAuthHelper googleAuthHelper;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        svRegister = findViewById(R.id.svRegister);
        tilFullName = findViewById(R.id.tilFullName);
        edtFullName = findViewById(R.id.edtFullName);
        tilEmail = findViewById(R.id.tilEmail);
        edtEmail = findViewById(R.id.edtEmail);
        tilPhone = findViewById(R.id.tilPhone);
        edtPhone = findViewById(R.id.edtPhone);
        tilPassword = findViewById(R.id.tilPassword);
        edtPassword = findViewById(R.id.edtPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        tvTerms = findViewById(R.id.tvTerms);
        btnGoLogin = findViewById(R.id.btnGoLogin);
        btnLoginLink = findViewById(R.id.btnLoginLink);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnRegister = findViewById(R.id.btnRegister);
        pbLoading = findViewById(R.id.pbLoading);

        userDao = new UserDao(this);
        sessionManager = new SessionManager(this);

        googleAuthHelper = new GoogleAuthHelper(this, getString(R.string.google_web_client_id));
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleSignInResult(result.getData()));

        setupInsets();
        setupTermsText();
        setupListeners();
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(svRegister, (v, insets) -> {
            int bars = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime();
            androidx.core.graphics.Insets i = insets.getInsets(bars);
            v.setPadding(v.getPaddingLeft(), i.top, v.getPaddingRight(), i.bottom);
            return insets;
        });
    }

    private void setupTermsText() {
        String full = getString(R.string.register_terms_agree);
        SpannableString spannable = new SpannableString(full);
        boldPart(spannable, full, "Điều khoản sử dụng");
        boldPart(spannable, full, "Chính sách bảo mật");
        tvTerms.setText(spannable);
    }

    private void boldPart(SpannableString spannable, String full, String part) {
        int start = full.indexOf(part);
        if (start >= 0) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, start + part.length(), 0);
        }
    }

    private void setupListeners() {
        btnGoLogin.setOnClickListener(v -> finish());
        btnLoginLink.setOnClickListener(v -> finish());

        btnGoogle.setOnClickListener(v -> googleSignInLauncher.launch(googleAuthHelper.getSignInIntent()));

        edtConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                attemptRegister();
                return true;
            }
            return false;
        });

        btnRegister.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        clearErrors();

        String fullName = text(edtFullName);
        String email = text(edtEmail);
        String phone = text(edtPhone);
        String password = text(edtPassword);
        String confirmPassword = text(edtConfirmPassword);

        if (fullName.isEmpty()) {
            tilFullName.setError(getString(R.string.error_field_required));
            return;
        }
        if (fullName.length() < 2) {
            tilFullName.setError(getString(R.string.register_error_fullname_short));
            return;
        }
        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_field_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_email_invalid));
            return;
        }
        if (phone.isEmpty()) {
            tilPhone.setError(getString(R.string.error_field_required));
            return;
        }
        if (!phone.matches("0\\d{9}")) {
            tilPhone.setError(getString(R.string.error_phone_invalid));
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_field_required));
            return;
        }
        if (password.length() < 6) {
            tilPassword.setError(getString(R.string.error_password_too_short));
            return;
        }
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError(getString(R.string.error_field_required));
            return;
        }
        if (!confirmPassword.equals(password)) {
            tilConfirmPassword.setError(getString(R.string.error_password_not_match));
            return;
        }
        if (!cbTerms.isChecked()) {
            Snackbar.make(svRegister, R.string.register_error_terms_required, Snackbar.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        AppExecutors.io(() -> {
            boolean emailExists = userDao.isEmailExists(email);
            if (emailExists) {
                AppExecutors.runOnUiSafe(this, () -> {
                    setLoading(false);
                    tilEmail.setError(getString(R.string.error_email_exists));
                });
                return;
            }

            User user = new User();
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPhone(phone);
            user.setPassword(password.toLowerCase());
            user.setRole(DbContract.Role.CUSTOMER);
            user.setVip(false);
            user.setCreatedAt(System.currentTimeMillis());

            long id = userDao.insert(user);

            AppExecutors.runOnUiSafe(this, () -> {
                setLoading(false);
                if (id <= 0) {
                    Snackbar.make(svRegister, R.string.error_network, Snackbar.LENGTH_SHORT).show();
                    return;
                }
                user.setId((int) id);
                Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                sessionManager.saveSession(user);
                startActivity(new android.content.Intent(this, MainActivity.class));
                finish();
            });
        });
    }

    private void handleGoogleSignInResult(Intent data) {
        GoogleSignInAccount account;
        try {
            account = googleAuthHelper.handleSignInResult(data);
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign-In thất bại, mã lỗi: " + e.getStatusCode(), e);
            if (e.getStatusCode() != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                Snackbar.make(svRegister, R.string.error_network, Snackbar.LENGTH_LONG).show();
            }
            return;
        }

        setLoading(true);
        AppExecutors.io(() -> {
            User user = null;
            boolean failed = false;
            try {
                user = userDao.loginOrRegisterWithGoogle(
                        account.getId(), account.getEmail(),
                        account.getDisplayName(),
                        account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);
            } catch (Exception e) {
                failed = true;
                Log.e(TAG, "Lỗi khi đăng ký/đăng nhập Google", e);
            }

            final User result = user;
            final boolean hasError = failed;
            AppExecutors.runOnUiSafe(this, () -> {
                setLoading(false);
                if (hasError || result == null) {
                    Snackbar.make(svRegister, R.string.error_network, Snackbar.LENGTH_LONG).show();
                } else {
                    sessionManager.saveSession(result);
                    Intent intent;
                    if (com.lumina.hotel.data.DbContract.Role.ADMIN.equals(result.getRole())) {
                        intent = new Intent(this, com.lumina.hotel.ui.admin.AdminMainActivity.class);
                    } else {
                        intent = new Intent(this, MainActivity.class);
                    }
                    startActivity(intent);
                    finish();
                }
            });
        });
    }

    private void clearErrors() {
        tilFullName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
    }

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        pbLoading.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private String text(TextInputEditText edt) {
        return edt.getText() == null ? "" : edt.getText().toString().trim();
    }
}
