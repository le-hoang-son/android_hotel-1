package com.lumina.hotel.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.lumina.hotel.R;
import com.lumina.hotel.data.DbContract.BookingStatus;
import com.lumina.hotel.databinding.ItemAdminBookingBinding;
import com.lumina.hotel.model.BookingWithDetail;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.StatusUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adapter đơn giản cho ListView ở màn Admin · Quản lý đơn đặt phòng. */
public class AdminBookingAdapter extends BaseAdapter {

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM", Locale.forLanguageTag("vi-VN"));

    public interface OnItemClickListener {
        void onItemClick(BookingWithDetail item);
    }

    public interface OnBookingActionListener {
        void onConfirm(BookingWithDetail item);
        void onReject(BookingWithDetail item);
        void onActionClick(BookingWithDetail item);
    }

    private final List<BookingWithDetail> items = new ArrayList<>();
    private OnItemClickListener itemClickListener;
    private OnBookingActionListener actionListener;

    public void setData(List<BookingWithDetail> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnBookingActionListener(OnBookingActionListener listener) {
        this.actionListener = listener;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public BookingWithDetail getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getBooking().getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemAdminBookingBinding binding;

        if (convertView == null) {
            binding = ItemAdminBookingBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            convertView = binding.getRoot();
            convertView.setTag(binding);
        } else {
            binding = (ItemAdminBookingBinding) convertView.getTag();
        }

        bind(binding, getItem(position));
        return convertView;
    }

    private void bind(ItemAdminBookingBinding binding, BookingWithDetail item) {
        Context ctx = binding.getRoot().getContext();
        String status = item.getBooking().getBookingStatus();

        binding.tvBookingCode.setText(ctx.getString(R.string.admin_booking_code, item.getBooking().getBookingCode()));
        StatusUtils.applyBookingStatus(binding.tvBookingStatus, status);

        String phone = item.getCustomerPhone();
        binding.tvCustomerInfo.setText(TextUtils.isEmpty(phone)
                ? item.getCustomerName()
                : ctx.getString(R.string.admin_booking_customer_info,
                        item.getCustomerName(), phone));

        String roomPart = item.getRoomNumber() != null
                ? item.getRoomNumber()
                : ctx.getString(R.string.admin_booking_no_room);
        binding.tvRoomInfo.setText(ctx.getString(R.string.admin_booking_room_info,
                item.getTypeName(), roomPart));

        binding.tvDateRange.setText(ctx.getString(R.string.bookings_date_range,
                DATE_FMT.format(item.getBooking().getCheckInDate()),
                DATE_FMT.format(item.getBooking().getCheckOutDate()),
                item.getBooking().getNumNights()));

        binding.tvTotal.setText(PriceUtils.formatVnd(item.getBooking().getTotalAmount()));

        binding.getRoot().setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(item);
        });

        applyActionButtons(binding, item, status);

        binding.getRoot().setAlpha(BookingStatus.CANCELLED.equals(status) ? 0.7f : 1f);
    }

    private void applyActionButtons(ItemAdminBookingBinding binding, BookingWithDetail item, String status) {
        boolean showConfirmReject = BookingStatus.PENDING.equals(status);
        boolean showAction = BookingStatus.CONFIRMED.equals(status) || BookingStatus.CHECKED_IN.equals(status);

        binding.btnConfirm.setVisibility(showConfirmReject ? View.VISIBLE : View.GONE);
        binding.btnReject.setVisibility(showConfirmReject ? View.VISIBLE : View.GONE);
        binding.btnAction.setVisibility(showAction ? View.VISIBLE : View.GONE);

        if (showConfirmReject) {
            binding.btnConfirm.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onConfirm(item);
            });
            binding.btnReject.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onReject(item);
            });
        }

        if (showAction) {
            binding.btnAction.setText(BookingStatus.CONFIRMED.equals(status)
                    ? R.string.admin_booking_action_checkin
                    : R.string.admin_booking_action_checkout);
            binding.btnAction.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onActionClick(item);
            });
        }
    }
}
