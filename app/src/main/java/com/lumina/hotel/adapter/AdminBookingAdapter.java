package com.lumina.hotel.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.lumina.hotel.R;
import com.lumina.hotel.data.DbContract.BookingStatus;
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

    /** Giữ sẵn các view của một item để không phải findViewById lại mỗi lần cuộn. */
    static class ViewHolder {
        TextView tvBookingCode;
        TextView tvBookingStatus;
        TextView tvCustomerInfo;
        TextView tvRoomInfo;
        TextView tvDateRange;
        TextView tvTotal;
        MaterialButton btnConfirm;
        MaterialButton btnReject;
        MaterialButton btnAction;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_booking, parent, false);

            holder = new ViewHolder();
            holder.tvBookingCode = convertView.findViewById(R.id.tvBookingCode);
            holder.tvBookingStatus = convertView.findViewById(R.id.tvBookingStatus);
            holder.tvCustomerInfo = convertView.findViewById(R.id.tvCustomerInfo);
            holder.tvRoomInfo = convertView.findViewById(R.id.tvRoomInfo);
            holder.tvDateRange = convertView.findViewById(R.id.tvDateRange);
            holder.tvTotal = convertView.findViewById(R.id.tvTotal);
            holder.btnConfirm = convertView.findViewById(R.id.btnConfirm);
            holder.btnReject = convertView.findViewById(R.id.btnReject);
            holder.btnAction = convertView.findViewById(R.id.btnAction);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        bind(convertView, holder, getItem(position));
        return convertView;
    }

    private void bind(View root, ViewHolder holder, BookingWithDetail item) {
        Context ctx = root.getContext();
        String status = item.getBooking().getBookingStatus();

        holder.tvBookingCode.setText(ctx.getString(R.string.admin_booking_code, item.getBooking().getBookingCode()));
        StatusUtils.applyBookingStatus(holder.tvBookingStatus, status);

        String phone = item.getCustomerPhone();
        holder.tvCustomerInfo.setText(TextUtils.isEmpty(phone)
                ? item.getCustomerName()
                : ctx.getString(R.string.admin_booking_customer_info,
                        item.getCustomerName(), phone));

        String roomPart = item.getRoomNumber() != null
                ? item.getRoomNumber()
                : ctx.getString(R.string.admin_booking_no_room);
        holder.tvRoomInfo.setText(ctx.getString(R.string.admin_booking_room_info,
                item.getTypeName(), roomPart));

        holder.tvDateRange.setText(ctx.getString(R.string.bookings_date_range,
                DATE_FMT.format(item.getBooking().getCheckInDate()),
                DATE_FMT.format(item.getBooking().getCheckOutDate()),
                item.getBooking().getNumNights()));

        holder.tvTotal.setText(PriceUtils.formatVnd(item.getBooking().getTotalAmount()));

        root.setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(item);
        });

        applyActionButtons(holder, item, status);

        root.setAlpha(BookingStatus.CANCELLED.equals(status) ? 0.7f : 1f);
    }

    private void applyActionButtons(ViewHolder holder, BookingWithDetail item, String status) {
        boolean showConfirmReject = BookingStatus.PENDING.equals(status);
        boolean showAction = BookingStatus.CONFIRMED.equals(status) || BookingStatus.CHECKED_IN.equals(status);

        holder.btnConfirm.setVisibility(showConfirmReject ? View.VISIBLE : View.GONE);
        holder.btnReject.setVisibility(showConfirmReject ? View.VISIBLE : View.GONE);
        holder.btnAction.setVisibility(showAction ? View.VISIBLE : View.GONE);

        if (showConfirmReject) {
            holder.btnConfirm.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onConfirm(item);
            });
            holder.btnReject.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onReject(item);
            });
        }

        if (showAction) {
            holder.btnAction.setText(BookingStatus.CONFIRMED.equals(status)
                    ? R.string.admin_booking_action_checkin
                    : R.string.admin_booking_action_checkout);
            holder.btnAction.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onActionClick(item);
            });
        }
    }
}
