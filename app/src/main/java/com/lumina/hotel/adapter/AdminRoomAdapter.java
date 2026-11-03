package com.lumina.hotel.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.lumina.hotel.R;
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

    /** Giữ sẵn các view của một item để không phải findViewById lại mỗi lần cuộn. */
    static class ViewHolder {
        TextView tvRoomNumber;
        TextView tvRoomStatus;
        TextView tvRoomTypeFloor;
        TextView tvRoomPrice;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_room, parent, false);

            holder = new ViewHolder();
            holder.tvRoomNumber = convertView.findViewById(R.id.tvRoomNumber);
            holder.tvRoomStatus = convertView.findViewById(R.id.tvRoomStatus);
            holder.tvRoomTypeFloor = convertView.findViewById(R.id.tvRoomTypeFloor);
            holder.tvRoomPrice = convertView.findViewById(R.id.tvRoomPrice);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        bind(convertView, holder, getItem(position));
        return convertView;
    }

    private void bind(View root, ViewHolder holder, RoomWithType item) {
        Context ctx = root.getContext();

        holder.tvRoomNumber.setText(item.getRoom().getRoomNumber());
        holder.tvRoomTypeFloor.setText(ctx.getString(R.string.admin_room_meta,
                item.getTypeName(), item.getRoom().getFloor()));
        holder.tvRoomPrice.setText(ctx.getString(R.string.admin_room_type_price_per_night,
                PriceUtils.formatShort(item.getBasePrice())));

        StatusUtils.applyRoomStatus(holder.tvRoomStatus, item.getRoom().getRoomStatus());

        root.setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(item);
        });
        root.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(item);
            return true;
        });
    }
}
