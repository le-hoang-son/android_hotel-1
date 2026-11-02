package com.lumina.hotel.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.lumina.hotel.R;
import com.lumina.hotel.databinding.ItemAdminRoomBinding;
import com.lumina.hotel.model.RoomWithType;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.StatusUtils;

import java.util.ArrayList;
import java.util.List;

/** Adapter đơn giản cho GridView (2 cột) ở màn Admin · Quản lý phòng. */
public class AdminRoomAdapter extends BaseAdapter {

    public interface OnItemClickListener {
        void onItemClick(RoomWithType item);
    }

    public interface OnRoomLongClickListener {
        void onLongClick(RoomWithType item);
    }

    private final List<RoomWithType> items = new ArrayList<>();
    private OnItemClickListener itemClickListener;
    private OnRoomLongClickListener longClickListener;

    public void setData(List<RoomWithType> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnRoomLongClickListener(OnRoomLongClickListener listener) {
        this.longClickListener = listener;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public RoomWithType getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getRoom().getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemAdminRoomBinding binding;

        if (convertView == null) {
            binding = ItemAdminRoomBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            convertView = binding.getRoot();
            convertView.setTag(binding);
        } else {
            binding = (ItemAdminRoomBinding) convertView.getTag();
        }

        bind(binding, getItem(position));
        return convertView;
    }

    private void bind(ItemAdminRoomBinding binding, RoomWithType item) {
        Context ctx = binding.getRoot().getContext();

        binding.tvRoomNumber.setText(item.getRoom().getRoomNumber());
        binding.tvRoomTypeFloor.setText(ctx.getString(R.string.admin_room_meta,
                item.getTypeName(), item.getRoom().getFloor()));
        binding.tvRoomPrice.setText(ctx.getString(R.string.admin_room_type_price_per_night,
                PriceUtils.formatShort(item.getBasePrice())));

        StatusUtils.applyRoomStatus(binding.tvRoomStatus, item.getRoom().getRoomStatus());

        binding.getRoot().setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(item);
        });
        binding.getRoot().setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(item);
            return true;
        });
    }
}
