package com.lumina.hotel.ui.customer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.browser.customtabs.CustomTabsIntent;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.PromotionDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.model.Booking;
import com.lumina.hotel.model.IntentKeys;
import com.lumina.hotel.model.Promotion;
import com.lumina.hotel.model.RoomType;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.SessionManager;
import com.lumina.hotel.util.VnPayHelper;
import com.lumina.hotel.util.MoMoHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    private static final long HOLD_MILLIS = 15 * 60 * 1000L; // 15 phút
    private static final String VNPAY_RETURN_PREFIX = "lumina://payment-return";
    private static final String MOMO_RETURN_PREFIX = "lumina://momo-return";
    static final String EXTRA_PAYMENT_METHOD = "extra_payment_method";

    private View rootView;

    private MaterialToolbar tbPayment;
    private NestedScrollView svContent;
    private View layoutBottomBar;
    private WebView webViewVnpay;
    private MaterialButton btnPayNow;
    private ProgressBar pbLoading;
    private TextView tvRoomName;
    private ShapeableImageView ivRoomThumb;
    private TextView tvDateRange;
    private TextView tvNightGuest;
    private TextView tvCountdown;
    private LinearLayout layoutDiscountLine;
    private TextView tvDiscountValue;
    private TextView tvPriceLineLabel;
    private TextView tvPriceLineValue;
    private TextView tvServiceFeeValue;
    private TextView tvVatValue;
    private TextView tvTotalValue;
    private TextInputEditText edtPromoCode;
    private TextView tvPromoResult;
    private LinearLayout cvVnpay;
    private LinearLayout cvMomo;
    private LinearLayout cvCard;
    private LinearLayout cvCash;
    private RadioButton rbVnpay;
    private RadioButton rbMomo;
    private RadioButton rbCard;
    private RadioButton rbCash;
    private MaterialButton btnApplyCode;

    private RoomTypeDao roomTypeDao;
    private BookingDao bookingDao;
    private PromotionDao promotionDao;
    private SessionManager sessionManager;
    private VnPayHelper vnPayHelper;
    private MoMoHelper momoHelper;
    private boolean momoCustomTabOpen = false;
    private int typeId;
    private long checkIn;
    private long checkOut;
    private int numGuests;
    private int nights;

    private RoomType roomType;
    private String selectedMethod = "vnpay";

    private long subtotal;
    private long serviceFee;
    private long discount;
    private long vat;
    private long total;

    private CountDownTimer holdTimer;
    private boolean expired = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
        rootView = findViewById(android.R.id.content);

        tbPayment = findViewById(R.id.tbPayment);
        svContent = findViewById(R.id.svContent);
        layoutBottomBar = findViewById(R.id.layoutBottomBar);
        webViewVnpay = findViewById(R.id.webViewVnpay);
        btnPayNow = findViewById(R.id.btnPayNow);
        pbLoading = findViewById(R.id.pbLoading);
        tvRoomName = findViewById(R.id.tvRoomName);
        ivRoomThumb = findViewById(R.id.ivRoomThumb);
        tvDateRange = findViewById(R.id.tvDateRange);
        tvNightGuest = findViewById(R.id.tvNightGuest);
        tvCountdown = findViewById(R.id.tvCountdown);
        layoutDiscountLine = findViewById(R.id.layoutDiscountLine);
        tvDiscountValue = findViewById(R.id.tvDiscountValue);
        tvPriceLineLabel = findViewById(R.id.tvPriceLineLabel);
        tvPriceLineValue = findViewById(R.id.tvPriceLineValue);
        tvServiceFeeValue = findViewById(R.id.tvServiceFeeValue);
        tvVatValue = findViewById(R.id.tvVatValue);
        tvTotalValue = findViewById(R.id.tvTotalValue);
        edtPromoCode = findViewById(R.id.edtPromoCode);
        tvPromoResult = findViewById(R.id.tvPromoResult);
        cvVnpay = findViewById(R.id.cvVnpay);
        cvMomo = findViewById(R.id.cvMomo);
        cvCard = findViewById(R.id.cvCard);
        cvCash = findViewById(R.id.cvCash);
        rbVnpay = findViewById(R.id.rbVnpay);
        rbMomo = findViewById(R.id.rbMomo);
        rbCard = findViewById(R.id.rbCard);
        rbCash = findViewById(R.id.rbCash);
        btnApplyCode = findViewById(R.id.btnApplyCode);

        typeId = getIntent().getIntExtra(IntentKeys.EXTRA_ROOM_TYPE_ID, -1);
        checkIn = getIntent().getLongExtra(IntentKeys.EXTRA_CHECK_IN, 0L);
        checkOut = getIntent().getLongExtra(IntentKeys.EXTRA_CHECK_OUT, 0L);
        numGuests = getIntent().getIntExtra(IntentKeys.EXTRA_NUM_GUESTS, 1);
        if (numGuests <= 0) numGuests = 1;

        if (typeId <= 0 || checkIn <= 0 || checkOut <= 0) {
            Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        nights = (int) ((checkOut - checkIn) / DateUtils.ONE_DAY);
        if (nights <= 0) {
            Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomTypeDao = new RoomTypeDao(this);
        bookingDao = new BookingDao(this);
        promotionDao = new PromotionDao(this);
        sessionManager = new SessionManager(this);
        vnPayHelper = new VnPayHelper(getString(R.string.vnp_tmn_code), getString(R.string.vnp_hash_secret));
        momoHelper = new MoMoHelper();

        setupInsets();
        setupClicks();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webViewVnpay.getVisibility() == View.VISIBLE) {
                    closeVnPayWebView();
                    btnPayNow.setEnabled(true);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        loadData();
    }

    /* ================== INSETS ================== */

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            tbPayment.setPadding(
                    tbPayment.getPaddingLeft(), bars.top,
                    tbPayment.getPaddingRight(), tbPayment.getPaddingBottom());
            layoutBottomBar.setPadding(
                    layoutBottomBar.getPaddingLeft(), layoutBottomBar.getPaddingTop(),
                    layoutBottomBar.getPaddingRight(),
                    bars.bottom > 0 ? bars.bottom : layoutBottomBar.getPaddingBottom());
            return insets;
        });
    }

    /* ================== TẢI DỮ LIỆU ================== */

    private void loadData() {
        AppExecutors.io(() -> {
            RoomType t = roomTypeDao.getById(typeId);
            if (t == null) {
                AppExecutors.runOnUiSafe(this, () -> {
                    Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            int overlapping = bookingDao.getOverlappingCount(typeId, checkIn, checkOut);

            AppExecutors.runOnUiSafe(this, () -> {
                roomType = t;
                if (overlapping >= t.getTotalRooms()) {
                    Snackbar.make(rootView, R.string.room_detail_sold_out_note,
                            Snackbar.LENGTH_LONG).setAnchorView(layoutBottomBar).show();
                    btnPayNow.setEnabled(false);
                    btnPayNow.setAlpha(0.4f);
                    return;
                }
                bindSummary(t);
                recalcPrices();
                startHoldTimer();
            });
        });
    }

    private void bindSummary(RoomType t) {
        tvRoomName.setText(t.getTypeName());
        Glide.with(ivRoomThumb)
                .load(t.getImageUrl())
                .placeholder(R.color.surface_variant)
                .error(R.drawable.ic_error)
                .into(ivRoomThumb);

        SimpleDateFormat df = new SimpleDateFormat("dd/MM", Locale.getDefault());
        tvDateRange.setText(getString(R.string.payment_date_range_format,
                df.format(checkIn), df.format(checkOut)));
        tvNightGuest.setText(getString(R.string.payment_night_guest_format,
                nights, numGuests));
    }

    /* ================== ĐẾM NGƯỢC GIỮ CHỖ ================== */

    private void startHoldTimer() {
        holdTimer = new CountDownTimer(HOLD_MILLIS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSec = millisUntilFinished / 1000;
                long min = totalSec / 60;
                long sec = totalSec % 60;
                tvCountdown.setText(String.format(Locale.getDefault(), "%s %02d:%02d",
                        getString(R.string.payment_hold_timer_label), min, sec));
            }

            @Override
            public void onFinish() {
                expired = true;
                tvCountdown.setText(String.format(Locale.getDefault(), "%s 00:00",
                        getString(R.string.payment_hold_timer_label)));
                btnPayNow.setEnabled(false);
                btnPayNow.setAlpha(0.4f);

                new MaterialAlertDialogBuilder(PaymentActivity.this)
                        .setTitle(R.string.payment_expired_title)
                        .setMessage(R.string.payment_error_expired)
                        .setCancelable(false)
                        .setPositiveButton(R.string.action_ok, (dialog, which) -> finish())
                        .show();
            }
        }.start();
    }

    /* ================== TÍNH TIỀN ================== */

    private void recalcPrices() {
        subtotal = PriceUtils.calcSubtotal(roomType.getBasePrice(), nights);
        serviceFee = PriceUtils.SERVICE_FEE;
        vat = PriceUtils.calcVat(subtotal, serviceFee, discount);
        total = PriceUtils.calcTotal(subtotal, serviceFee, discount, vat);

        tvPriceLineLabel.setText(getString(R.string.payment_price_line_format,
                PriceUtils.formatVnd(roomType.getBasePrice()),
                getString(R.string.night_count_format, nights)));
        tvPriceLineValue.setText(PriceUtils.formatVnd(subtotal));
        tvServiceFeeValue.setText(PriceUtils.formatVnd(serviceFee));
        tvVatValue.setText(PriceUtils.formatVnd(vat));

        if (discount > 0) {
            layoutDiscountLine.setVisibility(View.VISIBLE);
            tvDiscountValue.setText(getString(R.string.payment_discount_value_format,
                    PriceUtils.formatVnd(discount)));
        } else {
            layoutDiscountLine.setVisibility(View.GONE);
        }

        tvTotalValue.setText(PriceUtils.formatVnd(total));
        btnPayNow.setText(getString(R.string.payment_btn_submit_amount_format,
                getString(R.string.payment_btn_submit), PriceUtils.formatVnd(total)));
    }

    /* ================== MÃ GIẢM GIÁ ================== */

    private void applyPromoCode() {
        String code = edtPromoCode.getText() == null
                ? "" : edtPromoCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) return;

        AppExecutors.io(() -> {
            Promotion promo = promotionDao.findValidCode(code, System.currentTimeMillis());
            AppExecutors.runOnUiSafe(this, () -> {
                tvPromoResult.setVisibility(View.VISIBLE);
                if (promo == null) {
                    discount = 0;
                    tvPromoResult.setTextColor(getColor(R.color.status_red_text));
                    tvPromoResult.setText(getString(R.string.payment_error_promo_invalid));
                } else {
                    discount = PriceUtils.calcDiscount(subtotal, promo.getDiscountPercent(), promo.getMaxDiscount());
                    tvPromoResult.setTextColor(getColor(R.color.status_green_text));
                    tvPromoResult.setText(getString(R.string.payment_promo_applied_format,
                            promo.getCode(), promo.getDiscountPercent()));
                }
                recalcPrices();
            });
        });
    }

    /* ================== CHỌN PHƯƠNG THỨC ================== */

    private void selectMethod(String method) {
        selectedMethod = method;
        cvVnpay.setSelected("vnpay".equals(method));
        cvMomo.setSelected("momo".equals(method));
        cvCard.setSelected("card".equals(method));
        cvCash.setSelected("cash".equals(method));
        rbVnpay.setChecked("vnpay".equals(method));
        rbMomo.setChecked("momo".equals(method));
        rbCard.setChecked("card".equals(method));
        rbCash.setChecked("cash".equals(method));
    }

    /* ================== THANH TOÁN ================== */

    private void submitPayment() {
        if (expired) {
            Snackbar.make(rootView, R.string.payment_error_expired, Snackbar.LENGTH_SHORT)
                    .setAnchorView(layoutBottomBar).show();
            return;
        }
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        btnPayNow.setEnabled(false);
        pbLoading.setVisibility(View.VISIBLE);

        AppExecutors.io(() -> {
            int overlapping = bookingDao.getOverlappingCount(typeId, checkIn, checkOut);
            if (overlapping >= roomType.getTotalRooms()) {
                AppExecutors.runOnUiSafe(this, () -> {
                    pbLoading.setVisibility(View.GONE);
                    btnPayNow.setEnabled(true);
                    Snackbar.make(rootView, R.string.room_detail_sold_out_note,
                            Snackbar.LENGTH_LONG).setAnchorView(layoutBottomBar).show();
                });
                return;
            }

            AppExecutors.runOnUiSafe(this, () -> {
                pbLoading.setVisibility(View.GONE);
                if ("vnpay".equals(selectedMethod)) {
                    openVnPayWebView();
                } else if ("momo".equals(selectedMethod)) {
                    startMoMoPayment();
                } else {
                    finalizeBooking(selectedMethod, null);
                }
            });
        });
    }

    /* ================== VNPAY THẬT (PROMPT #8) ================== */

    private void openVnPayWebView() {
        String orderId = "PAY" + System.currentTimeMillis();
        String orderInfo = "Thanh toan dat phong " + roomType.getTypeName();
        String url = vnPayHelper.buildPaymentUrl(total, orderId, orderInfo);
        android.util.Log.d("VNPAY_DEBUG", "URL = " + url);

        svContent.setVisibility(View.GONE);
        layoutBottomBar.setVisibility(View.GONE);
        webViewVnpay.setVisibility(View.VISIBLE);
        android.webkit.WebSettings ws = webViewVnpay.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        webViewVnpay.setWebChromeClient(new android.webkit.WebChromeClient());
        webViewVnpay.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String requestUrl) {
                if (requestUrl.startsWith(VNPAY_RETURN_PREFIX)) {
                    handleVnPayReturn(requestUrl);
                    return true;
                }
                return false;
            }
        });
        webViewVnpay.loadUrl(url);
    }

    private void handleVnPayReturn(String returnUrl) {
        Uri uri = Uri.parse(returnUrl);
        Map<String, String> params = new HashMap<>();
        for (String name : uri.getQueryParameterNames()) {
            params.put(name, uri.getQueryParameter(name));
        }
        boolean validSignature = vnPayHelper.verifyReturnParams(params);
        String responseCode = params.get("vnp_ResponseCode");

        closeVnPayWebView();

        if (validSignature && "00".equals(responseCode)) {
            finalizeBooking("vnpay", params.get("vnp_TransactionNo"));
        } else {
            btnPayNow.setEnabled(true);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.payment_failed_title)
                    .setMessage(getString(R.string.payment_failed_message, responseCode))
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
        }
    }

    private void closeVnPayWebView() {
        webViewVnpay.setVisibility(View.GONE);
        webViewVnpay.loadUrl("about:blank");
        svContent.setVisibility(View.VISIBLE);
        layoutBottomBar.setVisibility(View.VISIBLE);
    }

    /* ================== MOMO THẬT (PROMPT #10) ================== */

    private void startMoMoPayment() {
        btnPayNow.setEnabled(false);
        pbLoading.setVisibility(View.VISIBLE);

        String orderId = "PAY" + System.currentTimeMillis();
        String orderInfo = "Thanh toan dat phong " + roomType.getTypeName();

        AppExecutors.io(() -> {
            String payUrl = momoHelper.createPaymentRequest(total, orderId, orderInfo);
            AppExecutors.runOnUiSafe(this, () -> {
                pbLoading.setVisibility(View.GONE);
                if (payUrl == null) {
                    btnPayNow.setEnabled(true);
                    Snackbar.make(rootView, R.string.error_network, Snackbar.LENGTH_LONG)
                            .setAnchorView(layoutBottomBar).show();
                    return;
                }
                openMoMoCustomTab(payUrl);
            });
        });
    }

    private void openMoMoCustomTab(String payUrl) {
        momoCustomTabOpen = true;
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(this, Uri.parse(payUrl));
    }


    private void handleMoMoReturn(String returnUrl) {
        Uri uri = Uri.parse(returnUrl);
        Map<String, String> params = new HashMap<>();
        for (String name : uri.getQueryParameterNames()) {
            params.put(name, uri.getQueryParameter(name));
        }
        boolean validSignature = momoHelper.verifyReturnParams(params);
        String resultCode = params.get("resultCode");

        closeVnPayWebView();

        if (validSignature && "0".equals(resultCode)) {
            finalizeBooking("momo", params.get("transId"));
        } else {
            btnPayNow.setEnabled(true);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.payment_failed_title)
                    .setMessage(getString(R.string.payment_failed_message, resultCode))
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
        }
    }

    /** Ghi booking + payments thật — dùng chung cho VNPay (sau khi verify) và MoMo/Thẻ/Tiền mặt (demo cũ). */
    private void finalizeBooking(String method, String txnRef) {
        btnPayNow.setEnabled(false);
        pbLoading.setVisibility(View.VISIBLE);

        AppExecutors.io(() -> {
            Booking b = new Booking();
            b.setUserId(sessionManager.getUserId());
            b.setRoomTypeId(typeId);
            b.setCheckInDate(checkIn);
            b.setCheckOutDate(checkOut);
            b.setNumNights(nights);
            b.setNumGuests(numGuests);
            b.setRoomSubtotal(subtotal);
            b.setServiceFee(serviceFee);
            b.setVatAmount(vat);
            b.setDiscountAmount(discount);
            b.setTotalAmount(total);
            b.setBookingStatus("confirmed");
            long bookingId = txnRef != null
                    ? bookingDao.createBooking(b, method, txnRef)
                    : bookingDao.createBooking(b, method);

            AppExecutors.runOnUiSafe(this, () -> {
                pbLoading.setVisibility(View.GONE);
                if (bookingId == -1) {
                    btnPayNow.setEnabled(true);
                    Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (holdTimer != null) holdTimer.cancel();
                Intent intent = new Intent(PaymentActivity.this, BookingSuccessActivity.class);
                intent.putExtra(IntentKeys.EXTRA_BOOKING_ID, b.getId());
                intent.putExtra(IntentKeys.EXTRA_BOOKING_CODE, b.getBookingCode());
                intent.putExtra(EXTRA_PAYMENT_METHOD, method);
                startActivity(intent);
                finish();
            });
        });
    }

    /* ================== CLICK CHUNG ================== */

    private void setupClicks() {
        tbPayment.setNavigationOnClickListener(v -> finish());
        cvVnpay.setOnClickListener(v -> selectMethod("vnpay"));
        cvMomo.setOnClickListener(v -> selectMethod("momo"));
        cvCard.setOnClickListener(v -> selectMethod("card"));
        cvCash.setOnClickListener(v -> selectMethod("cash"));
        btnApplyCode.setOnClickListener(v -> applyPromoCode());
        btnPayNow.setOnClickListener(v -> submitPayment());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(MOMO_RETURN_PREFIX)) {
            momoCustomTabOpen = false;
            handleMoMoReturn(data.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (momoCustomTabOpen) {
            momoCustomTabOpen = false;
            btnPayNow.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (holdTimer != null) holdTimer.cancel();
    }
}
