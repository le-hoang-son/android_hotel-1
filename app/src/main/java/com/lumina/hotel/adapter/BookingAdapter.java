package com.lumina.hotel.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.bumptech.glide.Glide;
import com.lumina.hotel.R;
import com.lumina.hotel.data.DbContract.BookingStatus;
import com.lumina.hotel.databinding.ItemBookingCardBinding;
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemBookingCardBinding binding;

        if (convertView == null) {
            binding = ItemBookingCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            convertView = binding.getRoot();
            convertView.setTag(binding);
        } else {
            binding = (ItemBookingCardBinding) convertView.getTag();
        }

        bind(binding, getItem(position));
        return convertView;
    }

    private void bind(ItemBookingCardBinding b, BookingWithDetail item) {
        Glide.with(b.ivThumbnail).load(item.getImageUrl()).into(b.ivThumbnail);
        b.tvRoomName.setText(item.getTypeName());
        b.tvBookingCode.setText(b.getRoot().getContext()
                .getString(R.string.bookings_code_prefix, item.getBooking().getBookingCode()));
        b.tvDateRange.setText(b.getRoot().getContext()
                .getString(R.string.bookings_date_range,
                        DateUtils.formatDayMonth(item.getBooking().getCheckInDate()),
                        DateUtils.formatDayMonth(item.getBooking().getCheckOutDate()),
                        item.getBooking().getNumNights()));
        b.tvTotal.setText(PriceUtils.formatVnd(item.getBooking().getTotalAmount()));
        StatusUtils.applyBookingStatus(b.tvStatus, item.getBooking().getBookingStatus());

        String status = item.getBooking().getBookingStatus();
        boolean isCancelled = BookingStatus.CANCELLED.equals(status);
        boolean isPending = BookingStatus.PENDING.equals(status);
        boolean isConfirmed = BookingStatus.CONFIRMED.equals(status);
        boolean isCompleted = BookingStatus.COMPLETED.equals(status);

        b.getRoot().setAlpha(isCancelled ? 0.7f : 1f);
        b.getRoot().setOnClickListener(v -> listener.onDetailClick(item));

        b.btnCancel.setVisibility(isPending ? View.VISIBLE : View.GONE);
        b.btnCancel.setOnClickListener(v -> listener.onCancelClick(item));

        if (isCancelled) {
            b.btnAction.setVisibility(View.GONE);
        } else if (isPending || isConfirmed) {
            b.btnAction.setVisibility(View.VISIBLE);
            b.btnAction.setText(b.getRoot().getContext()
                    .getString(R.string.bookings_btn_detail) + " →");
            b.btnAction.setEnabled(true);
            b.btnAction.setOnClickListener(v -> listener.onDetailClick(item));
        } else if (isCompleted) {
            b.btnAction.setVisibility(View.VISIBLE);
            if (item.isReviewed()) {
                b.btnAction.setText(R.string.bookings_btn_reviewed);
                b.btnAction.setEnabled(false);
                b.btnAction.setOnClickListener(null);
            } else {
                b.btnAction.setText(R.string.bookings_btn_review);
                b.btnAction.setEnabled(true);
                b.btnAction.setOnClickListener(v -> listener.onReviewClick(item));
            }
        } else {
            b.btnAction.setVisibility(View.GONE);
        }
    }
}
