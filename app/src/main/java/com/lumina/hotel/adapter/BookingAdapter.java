package com.lumina.hotel.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.lumina.hotel.R;
import com.lumina.hotel.data.DbContract.BookingStatus;
import com.lumina.hotel.model.BookingWithDetail;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.StatusUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter đơn giản cho ListView ở màn "Đơn của tôi".
 * Cách dùng: adapter.setData(list); listView.setAdapter(adapter);
 */
public class BookingAdapter extends BaseAdapter {

    public interface Listener {
        void onDetailClick(BookingWithDetail item);
        void onCancelClick(BookingWithDetail item);
        void onReviewClick(BookingWithDetail item);
    }

    private final List<BookingWithDetail> items = new ArrayList<>();
    private final Listener listener;

    public BookingAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<BookingWithDetail> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
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
        ImageView ivThumbnail;
        TextView tvRoomName;
        TextView tvBookingCode;
        TextView tvDateRange;
        TextView tvTotal;
        TextView tvStatus;
        MaterialButton btnCancel;
        MaterialButton btnAction;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_booking_card, parent, false);

            holder = new ViewHolder();
            holder.ivThumbnail = convertView.findViewById(R.id.ivThumbnail);
            holder.tvRoomName = convertView.findViewById(R.id.tvRoomName);
            holder.tvBookingCode = convertView.findViewById(R.id.tvBookingCode);
            holder.tvDateRange = convertView.findViewById(R.id.tvDateRange);
            holder.tvTotal = convertView.findViewById(R.id.tvTotal);
            holder.tvStatus = convertView.findViewById(R.id.tvStatus);
            holder.btnCancel = convertView.findViewById(R.id.btnCancel);
            holder.btnAction = convertView.findViewById(R.id.btnAction);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        bind(convertView, holder, getItem(position));
        return convertView;
    }

    private void bind(View root, ViewHolder h, BookingWithDetail item) {
        Glide.with(h.ivThumbnail).load(item.getImageUrl()).into(h.ivThumbnail);
        h.tvRoomName.setText(item.getTypeName());
        h.tvBookingCode.setText(root.getContext()
                .getString(R.string.bookings_code_prefix, item.getBooking().getBookingCode()));
        h.tvDateRange.setText(root.getContext()
                .getString(R.string.bookings_date_range,
                        DateUtils.formatDayMonth(item.getBooking().getCheckInDate()),
                        DateUtils.formatDayMonth(item.getBooking().getCheckOutDate()),
                        item.getBooking().getNumNights()));
        h.tvTotal.setText(PriceUtils.formatVnd(item.getBooking().getTotalAmount()));
        StatusUtils.applyBookingStatus(h.tvStatus, item.getBooking().getBookingStatus());

        String status = item.getBooking().getBookingStatus();
        boolean isCancelled = BookingStatus.CANCELLED.equals(status);
        boolean isPending = BookingStatus.PENDING.equals(status);
        boolean isConfirmed = BookingStatus.CONFIRMED.equals(status);
        boolean isCompleted = BookingStatus.COMPLETED.equals(status);

        root.setAlpha(isCancelled ? 0.7f : 1f);
        root.setOnClickListener(v -> listener.onDetailClick(item));

        // Khớp điều kiện huỷ của MyBookingsFragment: pending hoặc confirmed, chưa qua ngày nhận phòng
        // (cho huỷ tới hết ngày nhận phòng, kể cả nhận phòng hôm nay).
        boolean cancellable = (isPending || isConfirmed)
                && item.getBooking().getCheckInDate() >= DateUtils.today();
        h.btnCancel.setVisibility(cancellable ? View.VISIBLE : View.GONE);
        h.btnCancel.setOnClickListener(v -> listener.onCancelClick(item));

        if (isCancelled) {
            h.btnAction.setVisibility(View.GONE);
        } else if (isPending || isConfirmed) {
            h.btnAction.setVisibility(View.VISIBLE);
            h.btnAction.setText(root.getContext()
                    .getString(R.string.bookings_btn_detail) + " →");
            h.btnAction.setEnabled(true);
            h.btnAction.setOnClickListener(v -> listener.onDetailClick(item));
        } else if (isCompleted) {
            h.btnAction.setVisibility(View.VISIBLE);
            if (item.isReviewed()) {
                h.btnAction.setText(R.string.bookings_btn_reviewed);
                h.btnAction.setEnabled(false);
                h.btnAction.setOnClickListener(null);
            } else {
                h.btnAction.setText(R.string.bookings_btn_review);
                h.btnAction.setEnabled(true);
                h.btnAction.setOnClickListener(v -> listener.onReviewClick(item));
            }
        } else {
            h.btnAction.setVisibility(View.GONE);
        }
    }
}
