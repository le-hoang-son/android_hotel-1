package com.lumina.hotel.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.PaymentDao;
import com.lumina.hotel.model.Booking;
import com.lumina.hotel.model.BookingWithDetail;
import com.lumina.hotel.model.IntentKeys;
import com.lumina.hotel.model.Payment;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.StatusUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** SPEC 11 — Chi tiết đơn đặt phòng (phương án A: chỉ xem, không có nút hành động). */
public class AdminBookingDetailActivity extends AppCompatActivity {

    private View layoutRoot;
    private View layoutHeader;
    private View svContent;
    private View pbLoading;
    private ImageView ivBack;
    private TextView tvBookingCode;
    private TextView tvBookingStatus;
    private TextView tvCreatedAt;
    private TextView tvCustomerName;
    private View llRowPhone;
    private TextView tvCustomerPhone;
    private TextView tvRoomType;
    private TextView tvRoomNumber;
    private TextView tvGuests;
    private TextView tvCheckIn;
    private TextView tvCheckOut;
    private TextView tvNights;
    private TextView tvSubtotal;
    private TextView tvServiceFee;
    private View llRowDiscount;
    private TextView tvDiscount;
    private TextView tvVat;
    private TextView tvTotal;
    private TextView tvPayMethod;
    private TextView tvPayStatus;
    private View llRowPaidAt;
    private TextView tvPaidAt;
    private View llRowTxn;
    private TextView tvTxnRef;
    private View cvNote;
    private TextView tvNote;

    private BookingDao bookingDao;
    private PaymentDao paymentDao;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_booking_detail);
        layoutRoot = findViewById(R.id.layoutRoot);
        layoutHeader = findViewById(R.id.layoutHeader);
        svContent = findViewById(R.id.svContent);
        pbLoading = findViewById(R.id.pbLoading);
        ivBack = findViewById(R.id.ivBack);
        tvBookingCode = findViewById(R.id.tvBookingCode);
        tvBookingStatus = findViewById(R.id.tvBookingStatus);
        tvCreatedAt = findViewById(R.id.tvCreatedAt);
        tvCustomerName = findViewById(R.id.tvCustomerName);
        llRowPhone = findViewById(R.id.llRowPhone);
        tvCustomerPhone = findViewById(R.id.tvCustomerPhone);
        tvRoomType = findViewById(R.id.tvRoomType);
        tvRoomNumber = findViewById(R.id.tvRoomNumber);
        tvGuests = findViewById(R.id.tvGuests);
        tvCheckIn = findViewById(R.id.tvCheckIn);
        tvCheckOut = findViewById(R.id.tvCheckOut);
        tvNights = findViewById(R.id.tvNights);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvServiceFee = findViewById(R.id.tvServiceFee);
        llRowDiscount = findViewById(R.id.llRowDiscount);
        tvDiscount = findViewById(R.id.tvDiscount);
        tvVat = findViewById(R.id.tvVat);
        tvTotal = findViewById(R.id.tvTotal);
        tvPayMethod = findViewById(R.id.tvPayMethod);
        tvPayStatus = findViewById(R.id.tvPayStatus);
        llRowPaidAt = findViewById(R.id.llRowPaidAt);
        tvPaidAt = findViewById(R.id.tvPaidAt);
        llRowTxn = findViewById(R.id.llRowTxn);
        tvTxnRef = findViewById(R.id.tvTxnRef);
        cvNote = findViewById(R.id.cvNote);
        tvNote = findViewById(R.id.tvNote);

        setupInsets();
        ivBack.setOnClickListener(v -> finish());

        int bookingId = getIntent() != null
                ? getIntent().getIntExtra(IntentKeys.EXTRA_BOOKING_ID, -1) : -1;
        if (bookingId <= 0) {
            Toast.makeText(this, R.string.admin_booking_detail_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bookingDao = new BookingDao(getApplicationContext());
        paymentDao = new PaymentDao(getApplicationContext());
        loadDetail(bookingId);
    }

    // ---------------- GIAO DIỆN ----------------

    /** Cộng inset thanh trạng thái vào header, inset thanh điều hướng vào đáy vùng cuộn. */
    private void setupInsets() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false); // header navy → icon trạng thái màu sáng

        final int headerTop = layoutHeader.getPaddingTop();
        final int scrollBottom = svContent.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(layoutRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            layoutHeader.setPadding(layoutHeader.getPaddingLeft(),
                    headerTop + bars.top,
                    layoutHeader.getPaddingRight(),
                    layoutHeader.getPaddingBottom());
            svContent.setPadding(svContent.getPaddingLeft(),
                    svContent.getPaddingTop(),
                    svContent.getPaddingRight(),
                    scrollBottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(layoutRoot);
    }

    // ---------------- TẢI DỮ LIỆU (chạy nền) ----------------

    private void loadDetail(int bookingId) {
        pbLoading.setVisibility(View.VISIBLE);
        svContent.setVisibility(View.GONE);

        AppExecutors.io(() -> {
            BookingWithDetail detail = null;
            Payment payment = null;
            boolean failed = false;
            try {
                detail = bookingDao.getDetailById(bookingId);
                if (detail != null) payment = paymentDao.getByBookingId(bookingId);
            } catch (Exception e) {
                failed = true;
            }
            final BookingWithDetail fDetail = detail;
            final Payment fPayment = payment;
            final boolean fFailed = failed;

            AppExecutors.runOnUiSafe(this, () -> {
                pbLoading.setVisibility(View.GONE);
                if (fFailed) {
                    Toast.makeText(this, R.string.admin_booking_load_error, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                if (fDetail == null) {
                    Toast.makeText(this, R.string.admin_booking_detail_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                bind(fDetail, fPayment);
                svContent.setVisibility(View.VISIBLE);
            });
        });
    }

    // ---------------- HIỂN THỊ ----------------

    private void bind(BookingWithDetail d, @Nullable Payment p) {
        Booking b = d.getBooking();

        // Khối 1: mã đơn + trạng thái + thời điểm đặt
        tvBookingCode.setText(PriceUtils.displayCode(b.getBookingCode()));
        StatusUtils.applyBookingStatus(tvBookingStatus, b.getBookingStatus());
        tvCreatedAt.setText(getString(R.string.admin_booking_detail_created_at,
                formatDateTime(b.getCreatedAt())));

        // Khối 2: khách hàng (SĐT trống thì ẩn dòng)
        tvCustomerName.setText(orDash(d.getCustomerName()));
        String phone = d.getCustomerPhone();
        boolean hasPhone = phone != null && !phone.trim().isEmpty();
        llRowPhone.setVisibility(hasPhone ? View.VISIBLE : View.GONE);
        if (hasPhone) tvCustomerPhone.setText(phone);

        // Khối 3: phòng
        tvRoomType.setText(orDash(d.getTypeName()));
        String roomNumber = d.getRoomNumber();
        tvRoomNumber.setText(roomNumber == null || roomNumber.isEmpty()
                ? getString(R.string.admin_booking_no_room) : roomNumber);
        tvGuests.setText(getString(R.string.admin_booking_detail_guests_value, b.getNumGuests()));

        // Khối 4: lưu trú
        tvCheckIn.setText(weekdayDate(b.getCheckInDate()));
        tvCheckOut.setText(weekdayDate(b.getCheckOutDate()));
        tvNights.setText(getString(R.string.admin_booking_detail_nights_value, b.getNumNights()));

        // Khối 5: tiền (giảm giá = 0 thì ẩn dòng)
        tvSubtotal.setText(PriceUtils.formatVnd(b.getRoomSubtotal()));
        tvServiceFee.setText(PriceUtils.formatVnd(b.getServiceFee()));
        boolean hasDiscount = b.getDiscountAmount() > 0;
        llRowDiscount.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
        if (hasDiscount) {
            tvDiscount.setText(getString(R.string.admin_booking_detail_discount_value,
                    PriceUtils.formatVnd(b.getDiscountAmount())));
        }
        tvVat.setText(PriceUtils.formatVnd(b.getVatAmount()));
        tvTotal.setText(PriceUtils.formatVnd(b.getTotalAmount()));

        // Khối 6: thanh toán
        bindPayment(p);

        // Khối 7: ghi chú / lý do từ chối (chỉ hiện khi có nội dung)
        String note = b.getNote();
        boolean hasNote = note != null && !note.trim().isEmpty();
        cvNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) tvNote.setText(note.trim());
    }

    private void bindPayment(@Nullable Payment p) {
        if (p == null) {
            // Đơn chưa có bản ghi thanh toán
            tvPayMethod.setText(R.string.admin_booking_detail_empty_value);
            setPayBadge(R.string.admin_booking_detail_pay_none,
                    R.color.muted, R.drawable.bg_status_orange);
            llRowPaidAt.setVisibility(View.GONE);
            llRowTxn.setVisibility(View.GONE);
            return;
        }

        tvPayMethod.setText(methodLabel(p.getMethod()));

        String status = p.getPaymentStatus();
        boolean paid = "paid".equals(status);
        if (paid) {
            setPayBadge(R.string.success_status_paid,
                    R.color.status_green_text, R.drawable.bg_status_green);
        } else if ("refunded".equals(status)) {
            setPayBadge(R.string.admin_booking_detail_pay_refunded,
                    R.color.status_blue_text, R.drawable.bg_status_blue);
        } else {
            setPayBadge(R.string.success_status_unpaid,
                    R.color.status_orange_text, R.drawable.bg_status_orange);
        }

        // Thời điểm thanh toán: chỉ hiện khi đã thanh toán và có mốc thời gian
        boolean showPaidAt = paid && p.getPaidAt() > 0;
        llRowPaidAt.setVisibility(showPaidAt ? View.VISIBLE : View.GONE);
        if (showPaidAt) tvPaidAt.setText(formatDateTime(p.getPaidAt()));

        // Mã giao dịch: ẩn nếu trống (đơn tiền mặt chưa thu)
        String txn = p.getTxnRef();
        boolean hasTxn = txn != null && !txn.trim().isEmpty()
                && !("cash".equals(p.getMethod()) && !paid);
        llRowTxn.setVisibility(hasTxn ? View.VISIBLE : View.GONE);
        if (hasTxn) tvTxnRef.setText(txn);
    }

    private void setPayBadge(int textRes, int colorRes, int bgRes) {
        TextView tv = tvPayStatus;
        tv.setText(textRes);
        tv.setTextColor(ContextCompat.getColor(this, colorRes));
        tv.setBackgroundResource(bgRes);
    }

    // ---------------- TIỆN ÍCH ----------------

    private String methodLabel(@Nullable String method) {
        if (method == null) return getString(R.string.admin_booking_detail_empty_value);
        switch (method) {
            case "cash":  return getString(R.string.payment_method_cash);
            case "vnpay": return getString(R.string.payment_method_vnpay);
            case "momo":  return getString(R.string.payment_method_momo);
            case "card":  return getString(R.string.payment_method_card);
            default:      return method;
        }
    }

    private String weekdayDate(long millis) {
        return getString(R.string.admin_booking_detail_weekday_date,
                DateUtils.weekdayVi(millis), DateUtils.formatFull(millis));
    }

    private String formatDateTime(long millis) {
        return new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(new Date(millis));
    }

    private String orDash(@Nullable String s) {
        return s == null || s.trim().isEmpty()
                ? getString(R.string.admin_booking_detail_empty_value) : s;
    }
}
