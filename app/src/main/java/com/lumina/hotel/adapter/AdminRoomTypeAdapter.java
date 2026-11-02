package com.lumina.hotel.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.bumptech.glide.Glide;
import com.lumina.hotel.R;
import com.lumina.hotel.databinding.ItemAdminRoomTypeBinding;
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemAdminRoomTypeBinding binding;

        if (convertView == null) {
            binding = ItemAdminRoomTypeBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            convertView = binding.getRoot();
            convertView.setTag(binding);
        } else {
            binding = (ItemAdminRoomTypeBinding) convertView.getTag();
        }

        bind(binding, getItem(position));
        return convertView;
    }

    private void bind(ItemAdminRoomTypeBinding binding, RoomType t) {
        Context ctx = binding.getRoot().getContext();

        binding.tvTypeName.setText(t.getTypeName());
        binding.tvTypeMeta.setText(ctx.getString(R.string.admin_room_type_meta,
                t.getAreaM2(), t.getMaxGuests(), t.getTotalRooms()));
        binding.tvTypePrice.setText(ctx.getString(R.string.admin_room_type_price_per_night,
                PriceUtils.formatVnd(t.getBasePrice())));

        binding.tvBadgeInactive.setVisibility(t.isActive() ? View.GONE : View.VISIBLE);
        binding.getRoot().setAlpha(t.isActive() ? 1f : ALPHA_INACTIVE);

        binding.btnEdit.setContentDescription(
                ctx.getString(R.string.admin_room_type_cd_edit, t.getTypeName()));
        binding.btnDelete.setContentDescription(
                ctx.getString(R.string.admin_room_type_cd_delete, t.getTypeName()));

        binding.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(t);
        });
        binding.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(t);
        });

        String url = t.getImageUrl();
        if (url != null && !url.trim().isEmpty()) {
            Glide.with(binding.ivRoomTypeImage).load(url).centerCrop()
                    .into(binding.ivRoomTypeImage);
        } else {
            Glide.with(binding.ivRoomTypeImage).clear(binding.ivRoomTypeImage);
            binding.ivRoomTypeImage.setImageDrawable(null);
        }
    }
}
