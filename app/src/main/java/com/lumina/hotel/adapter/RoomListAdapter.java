package com.lumina.hotel.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.bumptech.glide.Glide;
import com.lumina.hotel.R;
import com.lumina.hotel.databinding.ItemRoomTypeCardBinding;
import com.lumina.hotel.model.RoomType;
import com.lumina.hotel.util.PriceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Adapter đơn giản cho ListView ở màn danh sách / kết quả tìm kiếm phòng. */
public class RoomListAdapter extends BaseAdapter {

    public interface OnItemClickListener {
        void onItemClick(RoomType item, int position);
    }

    private final List<RoomType> items = new ArrayList<>();
    private Set<Integer> soldOutIds = Collections.emptySet();
    private OnItemClickListener itemClickListener;

    public void setData(List<RoomType> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    /** Gọi từ Activity sau khi tính xong getOverlappingCount cho từng loại phòng. */
    public void setSoldOutIds(Set<Integer> ids) {
        this.soldOutIds = (ids != null) ? ids : Collections.emptySet();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public RoomType getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemRoomTypeCardBinding binding;

        if (convertView == null) {
            binding = ItemRoomTypeCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            convertView = binding.getRoot();
            convertView.setTag(binding);
        } else {
            binding = (ItemRoomTypeCardBinding) convertView.getTag();
        }

        bind(binding, getItem(position), position);
        return convertView;
    }

    private void bind(ItemRoomTypeCardBinding b, RoomType item, int position) {
        Glide.with(b.getRoot())
                .load(item.getImageUrl())
                .centerCrop()
                .into(b.ivRoomImage);

        b.tvTypeName.setText(item.getTypeName());
        b.tvMeta.setText(b.getRoot().getContext().getString(
                R.string.room_meta_format, item.getAreaM2(), item.getMaxGuests()));
        b.tvRating.setText(String.format(Locale.getDefault(), "%.1f", item.getAvgRating()));
        b.tvReviewCount.setText(b.getRoot().getContext().getString(
                R.string.review_count_format, item.getReviewCount()));
        b.tvOldPrice.setVisibility(View.GONE);

        boolean isSoldOut = soldOutIds.contains(item.getId());
        if (isSoldOut) {
            b.tvBadge.setText(R.string.badge_sold_out);
            b.tvBadge.setBackgroundResource(R.drawable.bg_status_red);
            b.tvBadge.setVisibility(View.VISIBLE);
            b.getRoot().setAlpha(0.5f);
            b.tvPrice.setText(R.string.badge_sold_out);
            b.getRoot().setOnClickListener(null);
        } else {
            boolean isHot = item.getAvgRating() >= 4.9f;
            b.tvBadge.setText(R.string.badge_hot);
            b.tvBadge.setBackgroundResource(R.drawable.bg_status_gold);
            b.tvBadge.setVisibility(isHot ? View.VISIBLE : View.GONE);
            b.getRoot().setAlpha(1f);
            b.tvPrice.setText(PriceUtils.formatVnd(item.getBasePrice()));
            b.getRoot().setOnClickListener(v -> {
                if (itemClickListener != null) itemClickListener.onItemClick(item, position);
            });
        }
    }
}
