package com.lumina.hotel.ui.customer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import com.lumina.hotel.R;
import com.lumina.hotel.adapter.BookingAdapter;
import com.lumina.hotel.data.DbContract.BookingStatus;
import com.lumina.hotel.data.dao.BookingDao;
import com.lumina.hotel.data.dao.ReviewDao;
import com.lumina.hotel.data.dao.RoomTypeDao;
import com.lumina.hotel.model.BookingWithDetail;
import com.lumina.hotel.model.IntentKeys;
import com.lumina.hotel.ui.admin.AdminBookingDetailActivity;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SPEC 07 — Đơn của tôi. 5 tab lọc theo booking_status, huỷ đơn, đánh giá. */
public class MyBookingsFragment extends Fragment implements BookingAdapter.Listener {

    /** Cha (MainActivity) cài đặt để Fragment chuyển sang tab Tìm kiếm từ nút empty state. */
    public interface BottomNavHost {
        void goToSearchTab();
    }

    private static final int TAB_ALL = 0;
    private static final int TAB_PENDING = 1;
    private static final int TAB_CONFIRMED = 2;
    private static final int TAB_COMPLETED = 3;
    private static final int TAB_CANCELLED = 4;

    private View rootView;
    private SwipeRefreshLayout srlBookings;
    private TabLayout tlBookingStatus;
    private ListView lvBookings;
    private View layoutEmpty;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private Button btnEmptyAction;

    private BookingDao bookingDao;
    private ReviewDao reviewDao;
    private RoomTypeDao roomTypeDao;
    private SessionManager session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BookingAdapter adapter;
    private List<BookingWithDetail> allBookings = new ArrayList<>();
    private int currentTab = TAB_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_bookings, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        srlBookings = view.findViewById(R.id.srlBookings);
        tlBookingStatus = view.findViewById(R.id.tlBookingStatus);
        lvBookings = view.findViewById(R.id.lvBookings);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        tvEmptyTitle = layoutEmpty.findViewById(R.id.tvEmptyTitle);
        tvEmptyDesc = layoutEmpty.findViewById(R.id.tvEmptyDesc);
        btnEmptyAction = layoutEmpty.findViewById(R.id.btnEmptyAction);

        bookingDao = new BookingDao(requireContext());
        reviewDao = new ReviewDao(requireContext());
        roomTypeDao = new RoomTypeDao(requireContext());
        session = new SessionManager(requireContext());

        setupTabs();
        setupList();
        srlBookings.setOnRefreshListener(this::loadBookings);
        btnEmptyAction.setOnClickListener(v -> {
            if (getActivity() instanceof BottomNavHost) {
                ((BottomNavHost) getActivity()).goToSearchTab();
            }
        });

        loadBookings();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Khách có thể vừa đặt đơn mới ở màn khác quay lại đây → nạp lại danh sách.
        if (rootView != null) loadBookings();
    }

    private void setupTabs() {
        String[] labels = {
                getString(R.string.bookings_tab_all),
                getString(R.string.bookings_tab_pending),
                getString(R.string.bookings_tab_confirmed),
                getString(R.string.bookings_tab_completed),
                getString(R.string.bookings_tab_cancelled)
        };
        for (String label : labels) {
            TabLayout.Tab tab = tlBookingStatus.newTab();
            tab.setCustomView(buildTabChip(label));
            tlBookingStatus.addTab(tab);
        }
        tlBookingStatus.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                applyFilter();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    /** Tab dạng "chip": nền navy khi chọn, nền surface viền line khi thường (BLOCK DS). */
    private View buildTabChip(String label) {
        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(13);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setBackgroundResource(R.drawable.selector_tab_booking_bg);
        tv.setTextColor(androidx.core.content.ContextCompat.getColorStateList(
                requireContext(), R.color.selector_tab_booking_text));
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setupList() {
        adapter = new BookingAdapter(this);
        lvBookings.setAdapter(adapter);
    }

    private void loadBookings() {
        srlBookings.setRefreshing(true);
        int userId = session.getUserId();
        executor.execute(() -> {
            List<BookingWithDetail> data = bookingDao.getByUser(userId);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (rootView == null) return;
                allBookings = data;
                srlBookings.setRefreshing(false);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        List<BookingWithDetail> filtered = new ArrayList<>();
        for (BookingWithDetail item : allBookings) {
            String status = item.getBooking().getBookingStatus();
            boolean match;
            switch (currentTab) {
                case TAB_PENDING:   match = BookingStatus.PENDING.equals(status); break;
                case TAB_CONFIRMED: match = BookingStatus.CONFIRMED.equals(status); break;
                case TAB_COMPLETED: match = BookingStatus.COMPLETED.equals(status); break;
                case TAB_CANCELLED: match = BookingStatus.CANCELLED.equals(status); break;
                default:            match = true;
            }
            if (match) filtered.add(item);
        }
        adapter.setData(filtered);

        boolean empty = filtered.isEmpty();
        lvBookings.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            tvEmptyTitle.setText(R.string.empty_bookings_title);
            tvEmptyDesc.setText(R.string.empty_bookings_desc);
            btnEmptyAction.setText(R.string.empty_bookings_action);
            btnEmptyAction.setVisibility(View.VISIBLE);
        }
    }

    /* ================= HÀNH ĐỘNG TRÊN TỪNG ĐƠN ================= */

    @Override
    public void onDetailClick(BookingWithDetail item) {
        // Mở màn Chi tiết đơn (chỉ xem) của chính đơn được bấm.
        android.content.Intent intent = new android.content.Intent(requireContext(),
                AdminBookingDetailActivity.class);
        intent.putExtra(IntentKeys.EXTRA_BOOKING_ID, item.getBooking().getId());
        startActivity(intent);
    }

    @Override
    public void onCancelClick(BookingWithDetail item) {
        // Cho huỷ tới hết ngày nhận phòng (kể cả nhận phòng hôm nay).
        boolean withinCancelWindow = item.getBooking().getCheckInDate() >= DateUtils.today();
        boolean cancellable = (BookingStatus.PENDING.equals(item.getBooking().getBookingStatus())
                || BookingStatus.CONFIRMED.equals(item.getBooking().getBookingStatus()))
                && withinCancelWindow;
        if (!cancellable) {
            Snackbar.make(rootView, R.string.snackbar_cancel_failed, Snackbar.LENGTH_SHORT).show();
            return;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_cancel_booking_title)
                .setMessage(getString(R.string.dialog_cancel_booking_message,
                        item.getBooking().getBookingCode()))
                .setNegativeButton(R.string.dialog_cancel_booking_negative, null)
                .setPositiveButton(R.string.dialog_cancel_booking_positive, (d, w) ->
                        executor.execute(() -> {
                            int rows = bookingDao.cancelByCustomer(item.getBooking().getId());
                            if (getActivity() == null) return;
                            requireActivity().runOnUiThread(() -> {
                                if (rootView == null) return;
                                if (rows > 0) {
                                    Snackbar.make(rootView, R.string.snackbar_cancel_success,
                                            Snackbar.LENGTH_SHORT).show();
                                    loadBookings();
                                } else {
                                    Snackbar.make(rootView, R.string.snackbar_cancel_failed,
                                            Snackbar.LENGTH_SHORT).show();
                                }
                            });
                        }))
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(androidx.core.content.ContextCompat.getColor(
                        requireContext(), R.color.status_red_text));
    }

    @Override
    public void onReviewClick(BookingWithDetail item) {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_review, null);
        RatingBar rbRating = form.findViewById(R.id.rbReviewRating);
        TextInputEditText edtComment = form.findViewById(R.id.edtReviewComment);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_review_title)
                .setView(form)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.dialog_review_submit, (d, w) -> {
                    int rating = Math.round(rbRating.getRating());
                    if (rating <= 0) {
                        Snackbar.make(rootView, R.string.dialog_review_error_rating,
                                Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    String comment = edtComment.getText() == null
                            ? "" : edtComment.getText().toString().trim();
                    int userId = session.getUserId();
                    int bookingId = item.getBooking().getId();
                    int typeId = item.getBooking().getRoomTypeId();

                    executor.execute(() -> {
                        reviewDao.insert(bookingId, userId, typeId, rating, comment);
                        roomTypeDao.refreshRatingCache(typeId);
                        if (getActivity() == null) return;
                        requireActivity().runOnUiThread(() -> {
                            if (rootView == null) return;
                            Snackbar.make(rootView, R.string.snackbar_review_success,
                                    Snackbar.LENGTH_SHORT).show();
                            loadBookings();
                        });
                    });
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
        srlBookings = null;
        tlBookingStatus = null;
        lvBookings = null;
        layoutEmpty = null;
        tvEmptyTitle = null;
        tvEmptyDesc = null;
        btnEmptyAction = null;
    }
}
