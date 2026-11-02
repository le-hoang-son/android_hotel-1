package com.lumina.hotel.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.lumina.hotel.R;
import com.lumina.hotel.databinding.ItemCustomerBinding;
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemCustomerBinding binding;

        if (convertView == null) {
            binding = ItemCustomerBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            convertView = binding.getRoot();
            convertView.setTag(binding);
        } else {
            binding = (ItemCustomerBinding) convertView.getTag();
        }

        bind(binding, getItem(position));
        return convertView;
    }

    private void bind(ItemCustomerBinding binding, CustomerStat stat) {
        binding.tvAvatar.setText(stat.getUser().getInitial());
        binding.tvCustomerName.setText(stat.getUser().getFullName());
        Context context = binding.getRoot().getContext();

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
        binding.tvCustomerContact.setText(contact);

        binding.tvBookingCount.setText(
                context.getString(R.string.customer_booking_count, stat.getTotalBookings()));
        binding.tvTotalSpent.setText(PriceUtils.formatVnd(stat.getTotalSpent()));

        boolean vip = stat.getUser().isVip();
        binding.tvVipBadge.setVisibility(vip ? View.VISIBLE : View.GONE);
        binding.ivVipStar.setVisibility(vip ? View.VISIBLE : View.GONE);

        binding.getRoot().setOnClickListener(v -> {
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
