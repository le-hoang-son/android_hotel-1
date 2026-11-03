package com.lumina.hotel.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.lumina.hotel.R;
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

    /** Giữ sẵn các view của một item để không phải findViewById lại mỗi lần cuộn. */
    static class ViewHolder {
        ImageView ivRoomImage;
        TextView tvTypeName;
        TextView tvMeta;
        TextView tvRating;
        TextView tvReviewCount;
        TextView tvBadge;
        TextView tvPrice;
        TextView tvOldPrice;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room_type_card, parent, false);

            holder = new ViewHolder();
            holder.ivRoomImage = convertView.findViewById(R.id.ivRoomImage);
            holder.tvTypeName = convertView.findViewById(R.id.tvTypeName);
            holder.tvMeta = convertView.findViewById(R.id.tvMeta);
            holder.tvRating = convertView.findViewById(R.id.tvRating);
            holder.tvReviewCount = convertView.findViewById(R.id.tvReviewCount);
            holder.tvBadge = convertView.findViewById(R.id.tvBadge);
            holder.tvPrice = convertView.findViewById(R.id.tvPrice);
            holder.tvOldPrice = convertView.findViewById(R.id.tvOldPrice);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        bind(convertView, holder, getItem(position), position);
        return convertView;
    }

    private void bind(View root, ViewHolder holder, RoomType item, int position) {
        Context ctx = root.getContext();

        Glide.with(root)
                .load(item.getImageUrl())
                .centerCrop()
                .into(holder.ivRoomImage);

        holder.tvTypeName.setText(item.getTypeName());
        holder.tvMeta.setText(ctx.getString(
                R.string.room_meta_format, item.getAreaM2(), item.getMaxGuests()));
        holder.tvRating.setText(String.format(Locale.getDefault(), "%.1f", item.getAvgRating()));
        holder.tvReviewCount.setText(ctx.getString(
                R.string.review_count_format, item.getReviewCount()));
        holder.tvOldPrice.setVisibility(View.GONE);

        boolean isSoldOut = soldOutIds.contains(item.getId());
        if (isSoldOut) {
            holder.tvBadge.setText(R.string.badge_sold_out);
            holder.tvBadge.setBackgroundResource(R.drawable.bg_status_red);
            holder.tvBadge.setVisibility(View.VISIBLE);
            root.setAlpha(0.5f);
            holder.tvPrice.setText(R.string.badge_sold_out);
            root.setOnClickListener(null);
        } else {
            boolean isHot = item.getAvgRating() >= 4.9f;
            holder.tvBadge.setText(R.string.badge_hot);
            holder.tvBadge.setBackgroundResource(R.drawable.bg_status_gold);
            holder.tvBadge.setVisibility(isHot ? View.VISIBLE : View.GONE);
            root.setAlpha(1f);
            holder.tvPrice.setText(PriceUtils.formatVnd(item.getBasePrice()));
            root.setOnClickListener(v -> {
                if (itemClickListener != null) itemClickListener.onItemClick(item, position);
            });
        }
    }
}
