package com.lumina.hotel.ui.admin;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.core.widget.ImageViewCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.datepicker.MaterialDatePicker;

import com.lumina.hotel.R;
import com.lumina.hotel.data.dao.DashboardDao;
import com.lumina.hotel.databinding.FragmentAdminDashboardBinding;
import com.lumina.hotel.databinding.ItemLatestBookingBinding;
import com.lumina.hotel.databinding.ItemTypeShareBinding;
import com.lumina.hotel.model.BookingBrief;
import com.lumina.hotel.model.TypeShareItem;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;
import com.lumina.hotel.util.StatusUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SPEC 14 — Tab "Tổng quan" của Admin.
 * loadDashboard() tính lại TOÀN BỘ số liệu theo chip khoảng thời gian đang chọn.
 */
public class AdminDashboardFragment extends Fragment {

    private enum Mode { MONTH, TODAY, WEEK, CUSTOM }

    /** Gói kết quả truy vấn (chạy nền) để đem về luồng chính vẽ UI. */
    private static class DashData {
        long revenue, prevRevenue;
        int bookings, prevBookings;
        float occupancyRate;
        int vacant;
        final List<String> labels = new ArrayList<>();
        final List<Long> values = new ArrayList<>();
        List<TypeShareItem> typeShare = new ArrayList<>();
        List<BookingBrief> latest = new ArrayList<>();
    }

    private FragmentAdminDashboardBinding binding;
    private DashboardDao dao;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Mode mode = Mode.MONTH;
    private int lastPresetChipId;
    private long customFrom = 0, customTo = 0;
    private int headerPaddingTop;
    private int loadToken = 0; // bỏ qua kết quả của lần tải cũ

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dao = new DashboardDao(requireContext().getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        setupHeader();
        setupStatCards();
        setupLists();
        setupChart();
        setupChips();

        binding.srlDashboard.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.gold));
        binding.srlDashboard.setOnRefreshListener(this::loadDashboard);

        loadDashboard();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
        if (executor != null) executor.shutdownNow();
        binding = null;
    }

    // =========================================================
    // SETUP
    // =========================================================

    private void setupHeader() {
        binding.tvGreetingAdmin.setText(getString(R.string.dash_greeting_admin, DateUtils.greetingByHour()));
        binding.ivAdminAvatar.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AdminProfileActivity.class)));

        // App nhắm SDK 37 → nội dung tràn dưới thanh trạng thái: cộng chiều cao status bar vào padding trên của header.
        // Nếu Activity đã xử lý inset thì top = 0, không bị cộng đôi.
        headerPaddingTop = binding.llAdminHeader.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.llAdminHeader, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), headerPaddingTop + sb.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.llAdminHeader);
    }

    private void setupStatCards() {
        binding.cvStatRevenue.ivStatIcon.setImageResource(R.drawable.ic_paid);
        binding.cvStatRevenue.tvStatLabel.setText(R.string.label_total_revenue);

        binding.cvStatBookings.ivStatIcon.setImageResource(R.drawable.ic_event_available);
        binding.cvStatBookings.tvStatLabel.setText(R.string.label_new_bookings);

        binding.cvStatOccupancy.ivStatIcon.setImageResource(R.drawable.ic_percent);
        binding.cvStatOccupancy.tvStatLabel.setText(R.string.label_occupancy_rate);

        binding.cvStatVacant.ivStatIcon.setImageResource(R.drawable.ic_meeting_room);
        binding.cvStatVacant.tvStatLabel.setText(R.string.label_vacant_rooms);
        // "Tỷ lệ lấp phòng" và "Phòng trống": KHÔNG hiện badge % (CSDL không lưu lịch sử room_status).
    }

    private void setupLists() {
        binding.btnSeeAllBookings.setOnClickListener(v -> openBookingTab());
    }

    private void openBookingTab() {
        if (getActivity() instanceof AdminMainActivity) {
            ((AdminMainActivity) getActivity()).openTab(R.id.nav_admin_booking);
        }
    }

    private void setupChart() {
        LineChart c = binding.chartRevenue;
        int muted = ContextCompat.getColor(requireContext(), R.color.muted);
        int line = ContextCompat.getColor(requireContext(), R.color.line);

        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.getAxisRight().setEnabled(false);
        c.setScaleEnabled(false);
        c.setNoDataText(getString(R.string.dash_chart_empty));
        c.setNoDataTextColor(muted);
        c.setExtraBottomOffset(4f);

        XAxis x = c.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        x.setTextColor(muted);
        x.setTextSize(10f);
        x.setAxisLineColor(line);

        YAxis y = c.getAxisLeft();
        y.setDrawAxisLine(false);
        y.setGridColor(line);
        y.setTextColor(muted);
        y.setTextSize(10f);
        y.setAxisMinimum(0f);
        y.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return PriceUtils.formatShort((long) value);
            }
        });
    }

    private void setupChips() {
        lastPresetChipId = R.id.chipMonth;
        binding.cgTimeRange.setSelectionRequired(true);

        binding.cgTimeRange.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipCustom) {
                mode = Mode.CUSTOM;
                if (customFrom > 0) loadDashboard(); // đã có dải ngày trước đó → dùng lại
                return;                                // chưa có → hộp chọn ngày mở ở onClick bên dưới
            }
            lastPresetChipId = id;
            customFrom = 0;
            customTo = 0;
            binding.chipCustom.setText(R.string.chip_time_custom);
            if (id == R.id.chipToday) mode = Mode.TODAY;
            else if (id == R.id.chipWeek) mode = Mode.WEEK;
            else mode = Mode.MONTH;
            loadDashboard();
        });

        binding.chipCustom.setOnClickListener(v -> showRangePicker());
    }

    private void showRangePicker() {
        MaterialDatePicker.Builder<Pair<Long, Long>> b = MaterialDatePicker.Builder.dateRangePicker();
        b.setTitleText(R.string.dash_pick_range);
        if (customFrom > 0) b.setSelection(new Pair<>(localToUtcDay(customFrom), localToUtcDay(customTo)));
        MaterialDatePicker<Pair<Long, Long>> picker = b.build();

        picker.addOnPositiveButtonClickListener(sel -> {
            if (binding == null || sel.first == null || sel.second == null) return;
            customFrom = utcToLocalDayStart(sel.first);
            customTo = utcToLocalDayStart(sel.second) + DateUtils.ONE_DAY - 1;
            mode = Mode.CUSTOM;
            binding.chipCustom.setText(getString(R.string.dash_range_format,
                    DateUtils.formatDayMonth(customFrom), DateUtils.formatDayMonth(customTo)));
            loadDashboard();
        });
        // Đóng hộp mà chưa chọn ngày nào → trả chip về lựa chọn trước đó
        picker.addOnDismissListener(d -> {
            if (binding != null && customFrom == 0) binding.cgTimeRange.check(lastPresetChipId);
        });
        picker.show(getChildFragmentManager(), "dash_range");
    }

    /** MaterialDatePicker trả về nửa đêm UTC → đổi sang 00:00 giờ địa phương của đúng ngày đó. */
    private static long utcToLocalDayStart(long utcMillis) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utcMillis);
        return DateUtils.dateOf(u.get(Calendar.YEAR), u.get(Calendar.MONTH) + 1, u.get(Calendar.DAY_OF_MONTH));
    }

    private static long localToUtcDay(long localMillis) {
        Calendar l = Calendar.getInstance();
        l.setTimeInMillis(localMillis);
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH));
        return u.getTimeInMillis();
    }

    // =========================================================
    // TẢI DỮ LIỆU
    // =========================================================

    /** @return {from, to, prevFrom, prevTo} theo chip đang chọn; prev = kỳ liền trước để so sánh. */
    private long[] computeRange() {
        long today = DateUtils.today();
        long from, to, prevFrom, prevTo;
        switch (mode) {
            case TODAY:
                from = today;
                to = today + DateUtils.ONE_DAY - 1;
                prevFrom = from - DateUtils.ONE_DAY;
                prevTo = from - 1;
                break;
            case WEEK: {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(today);
                int dow = cal.get(Calendar.DAY_OF_WEEK);
                int back = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY; // tuần bắt đầu Thứ 2
                cal.add(Calendar.DAY_OF_YEAR, -back);
                from = cal.getTimeInMillis();
                to = from + 7 * DateUtils.ONE_DAY - 1;
                prevFrom = from - 7 * DateUtils.ONE_DAY;
                prevTo = from - 1;
                break;
            }
            case CUSTOM: {
                from = customFrom;
                to = customTo;
                long len = to - from + 1;
                prevFrom = from - len;
                prevTo = from - 1;
                break;
            }
            case MONTH:
            default:
                from = DateUtils.startOfMonth(today);
                to = DateUtils.endOfMonth(today);
                prevFrom = DateUtils.startOfMonth(DateUtils.monthsAgo(1));
                prevTo = DateUtils.endOfMonth(prevFrom);
                break;
        }
        return new long[]{from, to, prevFrom, prevTo};
    }

    private void loadDashboard() {
        if (binding == null || executor == null || executor.isShutdown()) return;
        final long[] r = computeRange();
        final int token = ++loadToken;
        if (!binding.srlDashboard.isRefreshing()) binding.pbLoading.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                DashData d = query(r);
                mainHandler.post(() -> {
                    if (binding == null || token != loadToken) return;
                    render(d, r);
                    finishLoading();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (binding == null || token != loadToken) return;
                    finishLoading();
                    Toast.makeText(requireContext(), R.string.dash_load_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private DashData query(long[] r) {
        DashData d = new DashData();
        d.revenue = dao.getRevenue(r[0], r[1]);
        d.prevRevenue = dao.getRevenue(r[2], r[3]);
        d.bookings = dao.getNewBookingsCount(r[0], r[1]);
        d.prevBookings = dao.getNewBookingsCount(r[2], r[3]);
        float[] occ = dao.getOccupancyAndVacant();
        d.occupancyRate = occ[0];
        d.vacant = (int) occ[1];
        dao.getRevenueSeries(r[0], r[1], d.labels, d.values);
        d.typeShare = dao.getTypeShare(r[0], r[1]);
        d.latest = dao.getLatestBookings(4);
        return d;
    }

    private void finishLoading() {
        binding.pbLoading.setVisibility(View.GONE);
        binding.srlDashboard.setRefreshing(false);
    }

    // =========================================================
    // VẼ UI
    // =========================================================

    private void render(DashData d, long[] r) {
        Integer revDelta = deltaPercent(d.revenue, d.prevRevenue);
        Integer bookDelta = deltaPercent(d.bookings, d.prevBookings);

        // 4 thẻ chỉ số
        binding.cvStatRevenue.tvStatValue.setText(PriceUtils.formatShort(d.revenue));
        bindDeltaBadge(binding.cvStatRevenue.tvStatBadge, revDelta);

        binding.cvStatBookings.tvStatValue.setText(String.valueOf(d.bookings));
        bindDeltaBadge(binding.cvStatBookings.tvStatBadge, bookDelta);

        binding.cvStatOccupancy.tvStatValue.setText(getString(R.string.dash_percent, Math.round(d.occupancyRate)));
        binding.cvStatVacant.tvStatValue.setText(String.valueOf(d.vacant));

        // Dải thông báo nổi bật (so sánh doanh thu)
        renderHighlight(revDelta);

        // Biểu đồ
        binding.tvChartSubtitle.setText(getString(R.string.dash_range_format,
                DateUtils.formatDayMonth(r[0]), DateUtils.formatDayMonth(r[1])));
        renderChartTrend(revDelta);
        renderChart(d.labels, d.values);

        // 2 danh sách
        renderTypeShare(d.typeShare);
        renderLatestBookings(d.latest);
    }

    /** Vẽ danh sách "Tỷ trọng theo loại phòng" vào LinearLayout (thay cho RecyclerView). */
    private void renderTypeShare(List<TypeShareItem> list) {
        binding.containerTypeShare.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (TypeShareItem item : list) {
            ItemTypeShareBinding b = ItemTypeShareBinding.inflate(
                    inflater, binding.containerTypeShare, false);
            b.tvTypeName.setText(item.getTypeName());
            b.tvTypePercent.setText(getString(R.string.dash_percent, item.getPercent()));
            b.tvTypeCount.setText(getString(R.string.dash_type_count, item.getCount()));
            b.lpiTypeShare.setProgress(item.getPercent());
            binding.containerTypeShare.addView(b.getRoot());
        }
    }

    /** Vẽ danh sách "Đơn mới nhất" vào LinearLayout (thay cho RecyclerView). */
    private void renderLatestBookings(List<BookingBrief> list) {
        binding.containerLatestBookings.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (BookingBrief item : list) {
            ItemLatestBookingBinding b = ItemLatestBookingBinding.inflate(
                    inflater, binding.containerLatestBookings, false);

            String name = item.getGuestName() == null ? "" : item.getGuestName().trim();
            b.tvGuestName.setText(name);
            b.tvGuestInitial.setText(initialOf(name));
            b.tvBookingMeta.setText(getString(R.string.dash_booking_meta,
                    item.getRoomTypeName(), item.getCheckInLabel(), item.getNumNights()));
            b.tvBookingAmount.setText(PriceUtils.formatVnd(item.getTotalAmount()));

            if (item.getStatus() == null) {
                b.tvBookingStatus.setVisibility(View.GONE);
            } else {
                b.tvBookingStatus.setVisibility(View.VISIBLE);
                StatusUtils.applyBookingStatus(b.tvBookingStatus, item.getStatus());
            }

            b.getRoot().setOnClickListener(v -> openBookingTab());
            binding.containerLatestBookings.addView(b.getRoot());
        }
    }

    /** Tên Việt Nam: lấy chữ cái đầu của TÊN (từ cuối), ví dụ "Nguyễn Văn An" → "A". */
    private static String initialOf(String fullName) {
        if (fullName.isEmpty()) return "?";
        String[] parts = fullName.split("\\s+");
        String last = parts[parts.length - 1];
        return last.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private void renderHighlight(@Nullable Integer delta) {
        int green = ContextCompat.getColor(requireContext(), R.color.status_green_text);
        int red = ContextCompat.getColor(requireContext(), R.color.status_red_text);
        int muted = ContextCompat.getColor(requireContext(), R.color.muted);
        String prevName = getString(prevPeriodNameRes());

        if (delta == null) {
            binding.tvHighlightText.setText(R.string.dash_highlight_none);
            binding.tvHighlightBadge.setVisibility(View.GONE);
            binding.ivTrendIcon.setImageResource(R.drawable.ic_trending_up);
            ImageViewCompat.setImageTintList(binding.ivTrendIcon, ColorStateList.valueOf(muted));
            return;
        }
        binding.tvHighlightBadge.setVisibility(View.VISIBLE);
        bindDeltaBadge(binding.tvHighlightBadge, delta);
        if (delta > 0) {
            binding.tvHighlightText.setText(getString(R.string.dash_highlight_up, prevName));
            binding.ivTrendIcon.setImageResource(R.drawable.ic_trending_up);
            ImageViewCompat.setImageTintList(binding.ivTrendIcon, ColorStateList.valueOf(green));
        } else if (delta < 0) {
            binding.tvHighlightText.setText(getString(R.string.dash_highlight_down, prevName));
            binding.ivTrendIcon.setImageResource(R.drawable.ic_trending_down);
            ImageViewCompat.setImageTintList(binding.ivTrendIcon, ColorStateList.valueOf(red));
        } else {
            binding.tvHighlightText.setText(getString(R.string.dash_highlight_same, prevName));
            binding.ivTrendIcon.setImageResource(R.drawable.ic_trending_up);
            ImageViewCompat.setImageTintList(binding.ivTrendIcon, ColorStateList.valueOf(muted));
        }
    }

    private int prevPeriodNameRes() {
        switch (mode) {
            case TODAY: return R.string.dash_prev_day;
            case WEEK:  return R.string.dash_prev_week;
            case CUSTOM: return R.string.dash_prev_custom;
            case MONTH:
            default:    return R.string.dash_prev_month;
        }
    }

    private void renderChartTrend(@Nullable Integer delta) {
        TextView tv = binding.tvChartTrend;
        if (delta == null) {
            tv.setVisibility(View.INVISIBLE);
            return;
        }
        tv.setVisibility(View.VISIBLE);
        tv.setText(deltaText(delta));
        int colorRes = delta > 0 ? R.color.status_green_text
                : delta < 0 ? R.color.status_red_text : R.color.muted;
        tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }

    private void renderChart(List<String> labels, List<Long> values) {
        LineChart c = binding.chartRevenue;
        if (values.isEmpty()) {
            c.clear();
            c.invalidate();
            return;
        }
        int gold = ContextCompat.getColor(requireContext(), R.color.gold);

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) entries.add(new Entry(i, values.get(i)));

        LineDataSet set = new LineDataSet(entries, "");
        set.setColor(gold);
        set.setLineWidth(2.5f);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawValues(false);
        set.setDrawCircles(entries.size() == 1); // chỉ 1 điểm thì vẽ chấm cho nhìn thấy
        set.setCircleColor(gold);
        set.setCircleRadius(4f);
        set.setDrawCircleHole(false);
        set.setDrawFilled(true);
        set.setFillDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ColorUtils.setAlphaComponent(gold, 110), ColorUtils.setAlphaComponent(gold, 0)}));
        set.setHighLightColor(gold);

        c.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        c.getXAxis().setLabelCount(Math.min(labels.size(), 6), false);
        c.setData(new LineData(set));
        c.invalidate();
    }

    // =========================================================
    // HỖ TRỢ % THAY ĐỔI
    // =========================================================

    /** % thay đổi so với kỳ trước; null nếu kỳ trước = 0 (không có gốc để so sánh). */
    @Nullable
    private static Integer deltaPercent(long current, long previous) {
        if (previous <= 0) return null;
        return Math.round((current - previous) * 100f / previous);
    }

    private String deltaText(int delta) {
        if (delta > 0) return getString(R.string.dash_delta_up, delta);
        if (delta < 0) return getString(R.string.dash_delta_down, Math.abs(delta));
        return getString(R.string.dash_delta_zero);
    }

    private void bindDeltaBadge(TextView tv, @Nullable Integer delta) {
        if (delta == null) {
            tv.setVisibility(View.GONE);
            return;
        }
        tv.setVisibility(View.VISIBLE);
        tv.setText(deltaText(delta));
        int bg, fg;
        if (delta > 0) { bg = R.drawable.bg_status_green; fg = R.color.status_green_text; }
        else if (delta < 0) { bg = R.drawable.bg_status_red; fg = R.color.status_red_text; }
        else { bg = R.drawable.bg_status_gold; fg = R.color.status_gold_text; }
        tv.setBackgroundResource(bg);
        tv.setTextColor(ContextCompat.getColor(requireContext(), fg));
    }
}