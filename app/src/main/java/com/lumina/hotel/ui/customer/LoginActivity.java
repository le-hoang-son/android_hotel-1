package com.lumina.hotel.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lumina.hotel.data.dao.UserDao;
import com.lumina.hotel.model.User;
import com.lumina.hotel.ui.admin.AdminMainActivity;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.GoogleAuthHelper;
import com.lumina.hotel.util.SessionManager;
import com.lumina.hotel.R;

/**
 * SPEC 02 — Đăng nhập bằng email HOẶC số điện thoại, hoặc Google Sign-In thật.
 * Gọi UserDao.login trên luồng nền, thành công thì lưu phiên rồi chuyển theo role.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private View rootView;
    private TextInputLayout tilEmail;
    private TextInputEditText edtEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText edtPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnGoRegister;
    private MaterialButton btnRegisterLink;
    private MaterialButton btnGoogle;
    private MaterialButton btnForgotPassword;
    private ProgressBar pbLoading;
    private UserDao userDao;
    private GoogleAuthHelper googleAuthHelper;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        rootView = findViewById(android.R.id.content);

        tilEmail = findViewById(R.id.tilEmail);
        edtEmail = findViewById(R.id.edtEmail);
        tilPassword = findViewById(R.id.tilPassword);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);
        btnRegisterLink = findViewById(R.id.btnRegisterLink);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);
        pbLoading = findViewById(R.id.pbLoading);

        userDao = new UserDao(getApplicationContext());

        googleAuthHelper = new GoogleAuthHelper(this, getString(R.string.google_web_client_id));
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleSignInResult(result.getData()));

        applyWindowInsets();
        setupInputs();
        setupClicks();
    }

    /** Chừa chỗ cho status bar, thanh điều hướng và bàn phím (không bị che nút/ô nhập). */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void setupInputs() {
        clearErrorOnType(edtEmail, tilEmail);
        clearErrorOnType(edtPassword, tilPassword);

        // Phím "Done" trên bàn phím = bấm nút Đăng nhập
        edtPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void setupClicks() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        btnGoRegister.setOnClickListener(v -> openRegister());
        btnRegisterLink.setOnClickListener(v -> openRegister());

        btnGoogle.setOnClickListener(v -> googleSignInLauncher.launch(googleAuthHelper.getSignInIntent()));
        btnForgotPassword.setOnClickListener(v -> showComingSoon());
    }

    // ---------------- ĐĂNG NHẬP ----------------

    private void attemptLogin() {
        String account = textOf(edtEmail).trim();
        String password = textOf(edtPassword);

        boolean hasAccount = !account.isEmpty();
        boolean hasPassword = !password.isEmpty();

        tilEmail.setError(hasAccount
                ? null : getString(R.string.login_error_account_required));
        tilPassword.setError(hasPassword
                ? null : getString(R.string.login_error_password_required));

        if (!hasAccount) {
            edtEmail.requestFocus();
            return;
        }
        if (!hasPassword) {
            edtPassword.requestFocus();
            return;
        }

        hideKeyboard();
        setLoading(true);

        AppExecutors.io(() -> {
            User user = null;
            boolean failed = false;
            try {
                user = userDao.login(account, password);
            } catch (Exception e) {
                failed = true;
                Log.e(TAG, "Lỗi khi đăng nhập", e);
            }

            final User result = user;
            final boolean hasError = failed;
            AppExecutors.runOnUiSafe(this, () -> {
                setLoading(false);
                if (hasError) {
                    Snackbar.make(rootView, R.string.error_network,
                            Snackbar.LENGTH_LONG).show();
                } else if (result == null) {
                    // Không tiết lộ trường nào sai
                    Snackbar.make(rootView, R.string.login_error_invalid,
                            Snackbar.LENGTH_LONG).show();
                } else {
                    onLoginSuccess(result);
                }
            });
        });
    }

    private void onLoginSuccess(User user) {
        new SessionManager(this).saveSession(user);

        Intent intent;
        if (com.lumina.hotel.data.DbContract.Role.ADMIN.equals(user.getRole())) {
            intent = new Intent(this, AdminMainActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }

    // ---------------- GOOGLE SIGN-IN ----------------

    private void handleGoogleSignInResult(Intent data) {
        GoogleSignInAccount account;
        try {
            account = googleAuthHelper.handleSignInResult(data);
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign-In thất bại, mã lỗi: " + e.getStatusCode(), e);
            if (e.getStatusCode() != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                Snackbar.make(rootView, R.string.error_network,
                        Snackbar.LENGTH_LONG).show();
            }
            return;
        }
        // ... phần còn lại giữ nguyên

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
                Log.e(TAG, "Lỗi khi đăng nhập Google", e);
            }

            final User result = user;
            final boolean hasError = failed;
            AppExecutors.runOnUiSafe(this, () -> {
                setLoading(false);
                if (hasError || result == null) {
                    Snackbar.make(rootView, R.string.error_network,
                            Snackbar.LENGTH_LONG).show();
                } else {
                    onLoginSuccess(result);
                }
            });
        });
    }

    // ---------------- TIỆN ÍCH ----------------

    private void openRegister() {
        startActivity(new Intent(this, RegisterActivity.class));
    }

    private void showComingSoon() {
        Snackbar.make(rootView, R.string.feature_coming_soon,
                Snackbar.LENGTH_SHORT).show();
    }

    /** Hiện vòng tải và khoá các điều khiển khi đang xử lý. */
    private void setLoading(boolean loading) {
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        boolean enabled = !loading;
        btnLogin.setEnabled(enabled);
        btnGoogle.setEnabled(enabled);
        btnGoRegister.setEnabled(enabled);
        btnRegisterLink.setEnabled(enabled);
        btnForgotPassword.setEnabled(enabled);
        edtEmail.setEnabled(enabled);
        edtPassword.setEnabled(enabled);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
        }
    }

    private String textOf(TextInputEditText edt) {
        return edt.getText() == null ? "" : edt.getText().toString();
    }

    /** Người dùng gõ lại thì tự xoá thông báo lỗi ở ô đó. */
    private void clearErrorOnType(TextInputEditText edt, TextInputLayout til) {
        edt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { til.setError(null); }
        });
    }
}
