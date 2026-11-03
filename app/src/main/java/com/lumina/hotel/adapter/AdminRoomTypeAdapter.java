package com.lumina.hotel.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.lumina.hotel.R;
import com.lumina.hotel.model.RoomType;
import com.lumina.hotel.util.PriceUtils;

import java.util.ArrayList;
import java.util.List;

/** Adapter đơn giản cho ListView ở màn Admin · Quản lý loại phòng. */
public class AdminRoomTypeAdapter extends BaseAdapter {

    private static final float ALPHA_INACTIVE = 0.6f;

    public interface OnActionListener {
        void onEdit(RoomType roomType);
        void onDelete(RoomType roomType);
    }

    private final List<RoomType> items = new ArrayList<>();
    private final OnActionListener listener;

    public AdminRoomTypeAdapter(OnActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<RoomType> data) {
        items.clear();
        if (data != null) items.addAll(data);
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
        ImageView ivRoomTypeImage;
        TextView tvTypeName;
        TextView tvTypeMeta;
        TextView tvTypePrice;
        TextView tvBadgeInactive;
        ImageButton btnEdit;
        ImageButton btnDelete;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_room_type, parent, false);

            holder = new ViewHolder();
            holder.ivRoomTypeImage = convertView.findViewById(R.id.ivRoomTypeImage);
            holder.tvTypeName = convertView.findViewById(R.id.tvTypeName);
            holder.tvTypeMeta = convertView.findViewById(R.id.tvTypeMeta);
            holder.tvTypePrice = convertView.findViewById(R.id.tvTypePrice);
            holder.tvBadgeInactive = convertView.findViewById(R.id.tvBadgeInactive);
            holder.btnEdit = convertView.findViewById(R.id.btnEdit);
            holder.btnDelete = convertView.findViewById(R.id.btnDelete);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        bind(convertView, holder, getItem(position));
        return convertView;
    }

    private void bind(View root, ViewHolder holder, RoomType t) {
        Context ctx = root.getContext();

        holder.tvTypeName.setText(t.getTypeName());
        holder.tvTypeMeta.setText(ctx.getString(R.string.admin_room_type_meta,
                t.getAreaM2(), t.getMaxGuests(), t.getTotalRooms()));
        holder.tvTypePrice.setText(ctx.getString(R.string.admin_room_type_price_per_night,
                PriceUtils.formatVnd(t.getBasePrice())));

        holder.tvBadgeInactive.setVisibility(t.isActive() ? View.GONE : View.VISIBLE);
        root.setAlpha(t.isActive() ? 1f : ALPHA_INACTIVE);

        holder.btnEdit.setContentDescription(
                ctx.getString(R.string.admin_room_type_cd_edit, t.getTypeName()));
        holder.btnDelete.setContentDescription(
                ctx.getString(R.string.admin_room_type_cd_delete, t.getTypeName()));

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(t);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(t);
        });

        String url = t.getImageUrl();
        if (url != null && !url.trim().isEmpty()) {
            Glide.with(holder.ivRoomTypeImage).load(url).centerCrop()
                    .into(holder.ivRoomTypeImage);
        } else {
            Glide.with(holder.ivRoomTypeImage).clear(holder.ivRoomTypeImage);
            holder.ivRoomTypeImage.setImageDrawable(null);
        }
    }
}
