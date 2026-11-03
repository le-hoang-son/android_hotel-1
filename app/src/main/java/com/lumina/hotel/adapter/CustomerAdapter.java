package com.lumina.hotel.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.lumina.hotel.R;
import com.lumina.hotel.model.CustomerStat;
import com.lumina.hotel.util.PriceUtils;

import java.util.ArrayList;
import java.util.List;

/** Adapter đơn giản cho ListView ở màn Admin · Quản lý khách hàng. */
public class CustomerAdapter extends BaseAdapter {

    public interface OnCustomerClickListener {
        void onCustomerClick(CustomerStat stat);
    }

    private final List<CustomerStat> items = new ArrayList<>();
    private final OnCustomerClickListener listener;

    public CustomerAdapter(OnCustomerClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<CustomerStat> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public CustomerStat getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getUser().getId();
    }

    /** Giữ sẵn các view của một item để không phải findViewById lại mỗi lần cuộn. */
    static class ViewHolder {
        TextView tvAvatar;
        TextView tvCustomerName;
        TextView tvCustomerContact;
        TextView tvBookingCount;
        TextView tvTotalSpent;
        TextView tvVipBadge;
        ImageView ivVipStar;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_customer, parent, false);

            holder = new ViewHolder();
            holder.tvAvatar = convertView.findViewById(R.id.tvAvatar);
            holder.tvCustomerName = convertView.findViewById(R.id.tvCustomerName);
            holder.tvCustomerContact = convertView.findViewById(R.id.tvCustomerContact);
            holder.tvBookingCount = convertView.findViewById(R.id.tvBookingCount);
            holder.tvTotalSpent = convertView.findViewById(R.id.tvTotalSpent);
            holder.tvVipBadge = convertView.findViewById(R.id.tvVipBadge);
            holder.ivVipStar = convertView.findViewById(R.id.ivVipStar);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        bind(convertView, holder, getItem(position));
        return convertView;
    }

    private void bind(View root, ViewHolder holder, CustomerStat stat) {
        holder.tvAvatar.setText(stat.getUser().getInitial());
        holder.tvCustomerName.setText(stat.getUser().getFullName());
        Context context = root.getContext();

        String phone = cleanContact(stat.getUser().getPhone());
        String email = cleanContact(stat.getUser().getEmail());
        String contact;
        if (phone != null && email != null) {
            contact = context.getString(R.string.admin_customer_contact_format, phone, email);
        } else if (phone != null) {
            contact = phone;
        } else if (email != null) {
            contact = email;
        } else {
            contact = context.getString(R.string.admin_customer_contact_empty);
        }
        holder.tvCustomerContact.setText(contact);

        holder.tvBookingCount.setText(
                context.getString(R.string.customer_booking_count, stat.getTotalBookings()));
        holder.tvTotalSpent.setText(PriceUtils.formatVnd(stat.getTotalSpent()));

        boolean vip = stat.getUser().isVip();
        holder.tvVipBadge.setVisibility(vip ? View.VISIBLE : View.GONE);
        holder.ivVipStar.setVisibility(vip ? View.VISIBLE : View.GONE);

        root.setOnClickListener(v -> {
            if (listener != null) listener.onCustomerClick(stat);
        });
    }

    /** Trả về null nếu rỗng, chỉ có khoảng trắng hoặc là chữ "null" lưu nhầm trong dữ liệu. */
    private static String cleanContact(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) return null;
        return trimmed;
    }
}
