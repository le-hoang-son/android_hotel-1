package com.lumina.hotel.ui.admin;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.UserDao;
import com.lumina.hotel.model.Booking;
import com.lumina.hotel.model.BookingWithDetail;
import com.lumina.hotel.model.CustomerStat;
import com.lumina.hotel.model.User;
import com.lumina.hotel.util.AppExecutors;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;

import java.util.List;

/**
 * SPEC 12 — BottomSheet hồ sơ khách hàng: thông tin liên hệ, tổng đơn/chi
 * tiêu, 5 đơn gần nhất (tái sử dụng BookingDao.getByUser, cắt lấy 5 đơn đầu —
 * KHÔNG cần hàm DAO mới vì list đã sắp created_at DESC sẵn), bật/tắt VIP,
 * gọi điện.
 */
public class CustomerDetailBottomSheet extends BottomSheetDialogFragment {

    public interface OnVipChangedListener {
        void onVipChanged();
    }

    private CustomerStat stat;
    private OnVipChangedListener vipListener;

    private View rootView;
    private ImageButton btnCloseSheet;
    private TextView tvDetailAvatar;
    private ImageView ivDetailVipStar;
    private TextView tvDetailName;
    private TextView tvDetailContact;
    private SwitchMaterial switchVip;
    private TextView tvDetailTotalBookings;
    private TextView tvDetailTotalSpent;
    private LinearLayout llRecentBookings;
    private TextView tvEmptyBookings;
    private MaterialButton btnCallCustomer;

    private UserDao userDao;
    private BookingDao bookingDao;

    public static CustomerDetailBottomSheet newInstance(CustomerStat stat,
                                                        OnVipChangedListener listener) {
        CustomerDetailBottomSheet f = new CustomerDetailBottomSheet();
        f.stat = stat;
        f.vipListener = listener;
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.bottom_sheet_customer_detail, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnCloseSheet = view.findViewById(R.id.btnCloseSheet);
        tvDetailAvatar = view.findViewById(R.id.tvDetailAvatar);
        ivDetailVipStar = view.findViewById(R.id.ivDetailVipStar);
        tvDetailName = view.findViewById(R.id.tvDetailName);
        tvDetailContact = view.findViewById(R.id.tvDetailContact);
        switchVip = view.findViewById(R.id.switchVip);
        tvDetailTotalBookings = view.findViewById(R.id.tvDetailTotalBookings);
        tvDetailTotalSpent = view.findViewById(R.id.tvDetailTotalSpent);
        llRecentBookings = view.findViewById(R.id.llRecentBookings);
        tvEmptyBookings = view.findViewById(R.id.tvEmptyBookings);
        btnCallCustomer = view.findViewById(R.id.btnCallCustomer);

        if (stat == null || stat.getUser() == null) {
            dismiss();
            return;
        }
        userDao = new UserDao(requireContext());
        bookingDao = new BookingDao(requireContext());

        bindHeader();
        bindStats();
        setupVipSwitch();
        setupCallButton();
        btnCloseSheet.setOnClickListener(v -> dismiss());

        loadRecentBookings();
    }

    private void bindHeader() {
        User user = stat.getUser();
        tvDetailAvatar.setText(user.getInitial());
        ivDetailVipStar.setVisibility(user.isVip() ? View.VISIBLE : View.GONE);
        tvDetailName.setText(user.getFullName());
        tvDetailContact.setText(buildContact(user.getPhone(), user.getEmail()));
    }

    /** Bỏ SĐT/email null, rỗng hoặc chữ "null"; thiếu cả hai thì hiện chuỗi "chưa có". */
    private String buildContact(String phone, String email) {
        String p = cleanContact(phone);
        String e = cleanContact(email);
        if (!p.isEmpty() && !e.isEmpty()) {
            return getString(R.string.admin_customer_contact_format, p, e);
        }
        if (!p.isEmpty()) return p;
        if (!e.isEmpty()) return e;
        return getString(R.string.admin_customer_contact_empty);
    }

    private String cleanContact(String s) {
        if (s == null) return "";
        String t = s.trim();
        return "null".equalsIgnoreCase(t) ? "" : t;
    }

    private void bindStats() {
        tvDetailTotalBookings.setText(stat.getTotalBookings() + " đơn");
        tvDetailTotalSpent.setText(PriceUtils.formatVnd(stat.getTotalSpent()));
    }

    private void setupVipSwitch() {
        switchVip.setChecked(stat.getUser().isVip());
        switchVip.setOnCheckedChangeListener((btn, isChecked) -> {
            int userId = stat.getUser().getId();
            AppExecutors.io(() -> userDao.setVip(userId, isChecked));
            stat.getUser().setVip(isChecked);
            ivDetailVipStar.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (vipListener != null) vipListener.onVipChanged();
        });
    }

    private void setupCallButton() {
        btnCallCustomer.setOnClickListener(v -> {
            String phone = stat.getUser().getPhone();
            if (phone == null || phone.isEmpty()) return;
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });
    }

    private void loadRecentBookings() {
        int userId = stat.getUser().getId();
        AppExecutors.io(() -> {
            List<BookingWithDetail> all = bookingDao.getByUser(userId);
            List<BookingWithDetail> recent = all.subList(0, Math.min(5, all.size()));

            if (getActivity() == null) return; // sheet đã bị huỷ trong lúc chờ
            requireActivity().runOnUiThread(() -> {
                if (rootView == null) return;
                if (recent.isEmpty()) {
                    llRecentBookings.setVisibility(View.GONE);
                    tvEmptyBookings.setVisibility(View.VISIBLE);
                } else {
                    llRecentBookings.setVisibility(View.VISIBLE);
                    tvEmptyBookings.setVisibility(View.GONE);
                    bindRecentBookings(recent);
                }
            });
        });
    }

    /** Vẽ 5 đơn gần nhất vào LinearLayout (thay cho RecyclerView). */
    private void bindRecentBookings(List<BookingWithDetail> recent) {
        llRecentBookings.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (BookingWithDetail d : recent) {
            View item = inflater.inflate(R.layout.item_latest_booking, llRecentBookings, false);

            Booking booking = d.getBooking();
            String typeName = d.getTypeName();

            String initial = (typeName == null || typeName.trim().isEmpty())
                    ? "?" : typeName.trim().substring(0, 1).toUpperCase();
            ((TextView) item.findViewById(R.id.tvGuestInitial)).setText(initial);
            ((TextView) item.findViewById(R.id.tvGuestName)).setText(typeName);

            String meta = DateUtils.formatDayMonth(booking.getCheckInDate())
                    + " → " + DateUtils.formatDayMonth(booking.getCheckOutDate())
                    + " · " + DateUtils.nightsBetween(booking.getCheckInDate(),
                    booking.getCheckOutDate()) + " đêm";
            ((TextView) item.findViewById(R.id.tvBookingMeta)).setText(meta);

            ((TextView) item.findViewById(R.id.tvBookingAmount))
                    .setText(PriceUtils.formatVnd(booking.getTotalAmount()));
            ((TextView) item.findViewById(R.id.tvBookingStatus))
                    .setText(statusLabel(booking.getBookingStatus()));

            llRecentBookings.addView(item);
        }
    }

    private String statusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "pending":    return "Chờ xác nhận";
            case "confirmed":  return "Đã xác nhận";
            case "checked_in": return "Đang ở";
            case "completed":  return "Hoàn tất";
            case "cancelled":  return "Đã huỷ";
            default:           return status;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
        btnCloseSheet = null;
        tvDetailAvatar = null;
        ivDetailVipStar = null;
        tvDetailName = null;
        tvDetailContact = null;
        switchVip = null;
        tvDetailTotalBookings = null;
        tvDetailTotalSpent = null;
        llRecentBookings = null;
        tvEmptyBookings = null;
        btnCallCustomer = null;
    }
}
