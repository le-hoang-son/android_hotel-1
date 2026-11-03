package com.lumina.hotel.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.lumina.hotel.data.DbContract.BookingStatus;
import com.lumina.hotel.data.DbContract.BookingTable;
import com.lumina.hotel.data.DbContract.PaymentTable;
import com.lumina.hotel.data.DbContract.RoomStatus;
import com.lumina.hotel.data.DbContract.RoomTable;
import com.lumina.hotel.data.DatabaseHelper;
import com.lumina.hotel.model.Booking;
import com.lumina.hotel.model.BookingFilter;
import com.lumina.hotel.model.BookingWithDetail;
import com.lumina.hotel.util.DateUtils;
import com.lumina.hotel.util.PriceUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thao tác với bảng bookings — DAO quan trọng nhất của dự án.
 * Dùng cho: Thanh toán, Đơn của tôi, Quản lý đơn đặt (admin), Dashboard.
 */
public class BookingDao {

    /** createBooking: lỗi chung, không tạo được đơn. */
    public static final long RESULT_ERROR = -1;
    /** createBooking: hết phòng cho khoảng ngày đã chọn (chốt lại trong giao dịch). */
    public static final long RESULT_SOLD_OUT = -2;

    /** checkIn: nhận phòng thành công. */
    public static final int CHECKIN_OK = 1;
    /** checkIn: chưa tới ngày nhận phòng, không cho nhận sớm. */
    public static final int CHECKIN_TOO_EARLY = -1;
    /** checkIn: thất bại (đơn không còn "confirmed" hoặc lỗi ghi). */
    public static final int CHECKIN_FAILED = 0;

    /** Huỷ đơn xong nhưng không phát sinh hoàn tiền. */
    public static final int CANCEL_OK = 1;
    /** Huỷ đơn xong và đã đánh dấu hoàn tiền cho khách. */
    public static final int CANCEL_REFUNDED = 2;
    /** Không huỷ được (đơn đã đổi trạng thái từ trước). */
    public static final int CANCEL_FAILED = 0;

    private static final String PAY_STATUS_PAID     = "paid";
    private static final String PAY_STATUS_REFUNDED = "refunded";

    private final DatabaseHelper helper;

    public BookingDao(Context context) {
        this.helper = DatabaseHelper.getInstance(context);
    }

    /* ================================================================
     * 1. KIỂM TRA CÒN PHÒNG (quy tắc nghiệp vụ cốt lõi)
     * ================================================================ */

    /**
     * Đếm số đơn đang chiếm phòng của loại {@code typeId} trong khoảng ngày yêu cầu.
     *
     * Hai khoảng ngày KHÔNG giao nhau khi:
     *     checkOut_mới <= check_in_date    HOẶC    checkIn_mới >= check_out_date
     * Phủ định điều kiện đó ra được các đơn CÓ giao nhau.
     *
     * Chỉ tính các đơn còn hiệu lực (pending, confirmed, checked_in) — đơn đã huỷ
     * hoặc đã hoàn tất thì phòng đã được giải phóng.
     */
    public int getOverlappingCount(int typeId, long checkIn, long checkOut) {
        return countOverlapping(helper.getReadableDatabase(), typeId, checkIn, checkOut);
    }

    /** Bản dùng chung nhận sẵn {@code db} để gọi được cả BÊN TRONG giao dịch tạo đơn. */
    private int countOverlapping(SQLiteDatabase db, int typeId, long checkIn, long checkOut) {
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT COUNT(*) FROM bookings " +
                            "WHERE room_type_id = ? " +
                            "  AND booking_status IN ('pending','confirmed','checked_in') " +
                            "  AND NOT (? <= check_in_date OR ? >= check_out_date)",
                    new String[]{ String.valueOf(typeId),
                            String.valueOf(DateUtils.atStartOfDay(checkOut)),
                            String.valueOf(DateUtils.atStartOfDay(checkIn)) });
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Tổng số phòng vật lý của một loại (0 nếu không tìm thấy loại). */
    private int totalRoomsOf(SQLiteDatabase db, int typeId) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT total_rooms FROM room_types WHERE id = ? LIMIT 1",
                    new String[]{ String.valueOf(typeId) });
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Loại phòng còn nhận đặt trong khoảng ngày này không?
     * So số đơn đang chiếm với tổng số phòng vật lý của loại đó.
     */
    public boolean isAvailable(int typeId, int totalRooms, long checkIn, long checkOut) {
        return getOverlappingCount(typeId, checkIn, checkOut) < totalRooms;
    }

    /* ================================================================
     * 2. TẠO ĐƠN
     * ================================================================ */

    /**
     * Tạo đơn mới kèm bản ghi thanh toán, trong một giao dịch.
     * Nếu bất kỳ bước nào hỏng thì toàn bộ bị huỷ, không để lại đơn mồ côi.
     *
     * Tồn phòng được chốt lại NGAY TRONG giao dịch (không tin vào kiểm tra ở UI),
     * nhờ vậy hai khách cùng đặt phòng cuối thì chỉ một đơn qua được.
     *
     * @return id đơn vừa tạo; {@link #RESULT_SOLD_OUT} nếu hết phòng;
     *         {@link #RESULT_ERROR} nếu thất bại.
     */
    /** Giữ nguyên cho MoMo/Thẻ/Tiền mặt — txn_ref tự sinh như cũ. */
    public long createBooking(Booking b, String paymentMethod) {
        return createBooking(b, paymentMethod, null);
    }

    /**
     * PROMPT #8 — overload mới cho VNPay thật: cho phép truyền đúng
     * vnp_TransactionNo làm txn_ref thay vì tự sinh "TXN"+now.
     * txnRef = null thì hành vi y hệt hàm cũ.
     */
    public long createBooking(Booking b, String paymentMethod, String txnRef) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Chốt tồn phòng ngay trong giao dịch — chặn overbooking khi nhiều đơn cùng lúc.
            int totalRooms = totalRoomsOf(db, b.getRoomTypeId());
            if (totalRooms > 0
                    && countOverlapping(db, b.getRoomTypeId(), b.getCheckInDate(), b.getCheckOutDate())
                        >= totalRooms) {
                return RESULT_SOLD_OUT;
            }

            long now = System.currentTimeMillis();
            if (b.getCreatedAt() <= 0) b.setCreatedAt(now);
            String code = uniqueCode(db, PriceUtils.generateBookingCode(b.getCreatedAt()));

            ContentValues v = new ContentValues();
            v.put(BookingTable.BOOKING_CODE, code);
            v.put(BookingTable.USER_ID, b.getUserId());
            v.put(BookingTable.ROOM_TYPE_ID, b.getRoomTypeId());
            if (b.getRoomId() > 0) v.put(BookingTable.ROOM_ID, b.getRoomId());
            v.put(BookingTable.CHECK_IN_DATE, DateUtils.atStartOfDay(b.getCheckInDate()));
            v.put(BookingTable.CHECK_OUT_DATE, DateUtils.atStartOfDay(b.getCheckOutDate()));
            v.put(BookingTable.NUM_NIGHTS, b.getNumNights());
            v.put(BookingTable.NUM_GUESTS, b.getNumGuests());
            v.put(BookingTable.ROOM_SUBTOTAL, b.getRoomSubtotal());
            v.put(BookingTable.SERVICE_FEE, b.getServiceFee());
            v.put(BookingTable.VAT_AMOUNT, b.getVatAmount());
            v.put(BookingTable.DISCOUNT_AMOUNT, b.getDiscountAmount());
            v.put(BookingTable.TOTAL_AMOUNT, b.getTotalAmount());
            v.put(BookingTable.BOOKING_STATUS, b.getBookingStatus() == null
                    ? BookingStatus.PENDING : b.getBookingStatus());
            v.put(BookingTable.NOTE, b.getNote());
            v.put(BookingTable.CREATED_AT, b.getCreatedAt());

            long bookingId = db.insert(BookingTable.TABLE_NAME, null, v);
            if (bookingId == -1) return -1;   // finally vẫn chạy, giao dịch bị huỷ

            if (paymentMethod != null) {
                boolean isCash = "cash".equals(paymentMethod);
                ContentValues p = new ContentValues();
                p.put("booking_id", bookingId);
                p.put("method", paymentMethod);
                p.put("amount", b.getTotalAmount());
                p.put("payment_status", isCash ? "unpaid" : "paid");
                if (isCash) {
                    p.putNull("paid_at");
                } else {
                    p.put("paid_at", now);
                }
                p.put("txn_ref", txnRef != null ? txnRef : ("TXN" + now));
                if (db.insert("payments", null, p) == -1) return -1;
            }

            db.setTransactionSuccessful();
            b.setId((int) bookingId);
            b.setBookingCode(code);
            return bookingId;
        } finally {
            db.endTransaction();
        }
    }

    /* ================================================================
     * 3. ĐỌC
     * ================================================================ */

    private static final String SELECT_WITH_DETAIL =
            "SELECT b.*, t.type_name AS type_name, t.image_url AS image_url, " +
                    "       r.room_number AS room_number, u.full_name AS customer_name, " +
                    "       u.phone AS customer_phone, " +
                    "       (SELECT COUNT(*) FROM reviews rv WHERE rv.booking_id = b.id) AS reviewed " +
                    "FROM bookings b " +
                    "JOIN room_types t ON t.id = b.room_type_id " +
                    "JOIN users u      ON u.id = b.user_id " +
                    "LEFT JOIN rooms r ON r.id = b.room_id ";

    /** Đơn của một khách — màn "Đơn của tôi". */
    public List<BookingWithDetail> getByUser(int userId) {
        return queryDetail(SELECT_WITH_DETAIL + "WHERE b.user_id = ? ORDER BY b.created_at DESC",
                new String[]{ String.valueOf(userId) });
    }

    /** Toàn bộ đơn — màn Quản lý đơn đặt của admin (không có bộ lọc nâng cao). */
    public List<BookingWithDetail> getAllWithDetail(String status) {
        return getAllWithDetail(status, null);
    }

    /**
     * Phụ lục F: lọc theo trạng thái (chip) + bộ lọc nâng cao (phương thức thanh toán,
     * khoảng ngày nhận phòng, tên/SĐT khách). Các điều kiện kết hợp AND.
     */
    public List<BookingWithDetail> getAllWithDetail(String status, BookingFilter filter) {
        List<String> args = new ArrayList<>();
        String where = buildWhere(status, filter, args);
        return queryDetail(SELECT_WITH_DETAIL + where + "ORDER BY b.created_at DESC",
                args.toArray(new String[0]));
    }

    public BookingWithDetail getDetailById(int bookingId) {
        List<BookingWithDetail> list = queryDetail(
                SELECT_WITH_DETAIL + "WHERE b.id = ? LIMIT 1",
                new String[]{ String.valueOf(bookingId) });
        return list.isEmpty() ? null : list.get(0);
    }

    public Booking getByCode(String code) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT * FROM bookings WHERE booking_code = ? LIMIT 1",
                    new String[]{ code });
            return c.moveToFirst() ? cursorToBooking(c) : null;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Đếm đơn theo từng trạng thái — dùng cho các chip lọc có số (không có bộ lọc nâng cao). */
    public Map<String, Integer> countByStatus() {
        return countByStatus(null);
    }

    /** Đếm theo trạng thái, nhưng chỉ tính các đơn thoả bộ lọc nâng cao (khớp với danh sách). */
    public Map<String, Integer> countByStatus(BookingFilter filter) {
        Map<String, Integer> map = new LinkedHashMap<>();
        List<String> args = new ArrayList<>();
        String where = buildWhere(null, filter, args);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        int total = 0;
        try {
            c = db.rawQuery("SELECT b.booking_status AS booking_status, COUNT(*) AS cnt " +
                    "FROM bookings b " +
                    "JOIN room_types t ON t.id = b.room_type_id " +
                    "JOIN users u      ON u.id = b.user_id " +
                    where + "GROUP BY b.booking_status", args.toArray(new String[0]));
            while (c.moveToNext()) {
                int cnt = c.getInt(c.getColumnIndexOrThrow("cnt"));
                map.put(c.getString(c.getColumnIndexOrThrow("booking_status")), cnt);
                total += cnt;
            }
        } finally {
            if (c != null) c.close();
        }
        map.put("all", total);
        return map;
    }

    /* ================================================================
     * 4. CHUYỂN TRẠNG THÁI (phần CRUD của admin)
     * ================================================================ */

    public int updateStatus(int bookingId, String status) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(BookingTable.BOOKING_STATUS, status);
        return db.update(BookingTable.TABLE_NAME, v, "id = ?",
                new String[]{ String.valueOf(bookingId) });
    }

    /** Admin xác nhận đơn: chỉ chuyển được khi đơn còn "pending".
     *  @return số dòng được cập nhật (0 = đơn đã đổi trạng thái từ trước). */
    public int confirmBooking(int bookingId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(BookingTable.BOOKING_STATUS, BookingStatus.CONFIRMED);
        return db.update(BookingTable.TABLE_NAME, v,
                "id = ? AND booking_status = ?",
                new String[]{ String.valueOf(bookingId), BookingStatus.PENDING });
    }

    /**
     * Chính sách hủy theo mốc thời gian (so với ngày nhận phòng):
     *   - Hủy TRƯỚC ngày nhận phòng (còn ≥ 1 ngày): hoàn 100%.
     *   - Hủy trong ngày nhận phòng trở đi: không hoàn.
     * @return phần trăm được hoàn (0 hoặc 100).
     */
    public static int refundPercent(long checkInDate) {
        return DateUtils.atStartOfDay(checkInDate) > DateUtils.today() ? 100 : 0;
    }

    /**
     * Khách tự huỷ đơn và hoàn tiền theo chính sách, tất cả trong MỘT giao dịch.
     * Chỉ cho phép khi đơn còn "pending" hoặc "confirmed".
     *
     * @return {@link #CANCEL_REFUNDED} nếu huỷ được VÀ có hoàn tiền;
     *         {@link #CANCEL_OK} nếu huỷ được nhưng không hoàn tiền (ngoài mốc hoặc chưa trả tiền);
     *         {@link #CANCEL_FAILED} nếu không huỷ được.
     */
    public int cancelByCustomer(int bookingId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            long checkIn = bookingCheckInDate(db, bookingId, "booking_status IN ('pending','confirmed')");
            if (checkIn <= 0) return CANCEL_FAILED;

            ContentValues v = new ContentValues();
            v.put(BookingTable.BOOKING_STATUS, BookingStatus.CANCELLED);
            int rows = db.update(BookingTable.TABLE_NAME, v,
                    "id = ? AND booking_status IN ('pending','confirmed')",
                    new String[]{ String.valueOf(bookingId) });
            if (rows == 0) return CANCEL_FAILED;

            // Chỉ hoàn khi khách huỷ đúng mốc được hoàn; chỉ hoàn cho khoản đã thanh toán.
            int refunded = refundPercent(checkIn) == 100 ? refundPaidPayments(db, bookingId) : 0;
            db.setTransactionSuccessful();
            return refunded > 0 ? CANCEL_REFUNDED : CANCEL_OK;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Admin từ chối đơn: chỉ áp dụng khi đơn còn "pending", ghi lý do vào note
     * và hoàn lại tiền nếu khách đã trả (lỗi từ phía khách sạn).
     * @return số dòng đơn được cập nhật.
     */
    public int rejectBooking(int bookingId, String reason) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put(BookingTable.BOOKING_STATUS, BookingStatus.CANCELLED);
            v.put(BookingTable.NOTE, reason);
            int rows = db.update(BookingTable.TABLE_NAME, v,
                    "id = ? AND booking_status = ?",
                    new String[]{ String.valueOf(bookingId), BookingStatus.PENDING });
            if (rows == 0) return 0;

            refundPaidPayments(db, bookingId);
            db.setTransactionSuccessful();
            return rows;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Tự động hủy các đơn "confirmed" đã qua ngày nhận phòng mà khách không đến (no-show).
     * Không hoàn tiền vì lỗi thuộc về khách. Gọi định kỳ khi mở danh sách đơn.
     * @return số đơn bị hủy.
     */
    public int cancelNoShows() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(BookingTable.BOOKING_STATUS, BookingStatus.CANCELLED);
        return db.update(BookingTable.TABLE_NAME, v,
                "booking_status = ? AND check_in_date < ?",
                new String[]{ BookingStatus.CONFIRMED, String.valueOf(DateUtils.today()) });
    }

    /**
     * Check-in: gán phòng vật lý cho đơn và đổi trạng thái phòng sang "đang ở".
     * Chỉ áp dụng khi đơn còn "confirmed" và đã tới ngày nhận phòng.
     * Hai thao tác phải cùng thành công hoặc cùng thất bại.
     *
     * @return {@link #CHECKIN_OK}, {@link #CHECKIN_TOO_EARLY} hoặc {@link #CHECKIN_FAILED}.
     */
    public int checkIn(int bookingId, int roomId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            long checkIn = bookingCheckInDate(db, bookingId,
                    "booking_status = '" + BookingStatus.CONFIRMED + "'");
            if (checkIn <= 0) return CHECKIN_FAILED;
            // Không cho nhận phòng trước ngày khách đã đặt.
            if (DateUtils.atStartOfDay(checkIn) > DateUtils.today()) return CHECKIN_TOO_EARLY;

            ContentValues b = new ContentValues();
            b.put(BookingTable.ROOM_ID, roomId);
            b.put(BookingTable.BOOKING_STATUS, BookingStatus.CHECKED_IN);
            int r1 = db.update(BookingTable.TABLE_NAME, b,
                    "id = ? AND booking_status = ?",
                    new String[]{ String.valueOf(bookingId), BookingStatus.CONFIRMED });

            ContentValues rm = new ContentValues();
            rm.put("room_status", RoomStatus.OCCUPIED);
            int r2 = db.update("rooms", rm, "id = ?",
                    new String[]{ String.valueOf(roomId) });

            if (r1 > 0 && r2 > 0) {
                db.setTransactionSuccessful();
                return CHECKIN_OK;
            }
            return CHECKIN_FAILED;
        } finally {
            db.endTransaction();
        }
    }

    /** Check-out: đơn chuyển sang hoàn tất, phòng được giải phóng về "trống".
     *  Nếu đơn trả bằng tiền mặt và chưa thanh toán, đánh dấu đã thu tiền lúc này. */
    public boolean checkOut(int bookingId, int roomId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues b = new ContentValues();
            b.put(BookingTable.BOOKING_STATUS, BookingStatus.COMPLETED);
            int r1 = db.update(BookingTable.TABLE_NAME, b,
                    "id = ? AND booking_status = ?",
                    new String[]{ String.valueOf(bookingId), BookingStatus.CHECKED_IN });

            if (roomId > 0) {
                ContentValues rm = new ContentValues();
                rm.put("room_status", RoomStatus.AVAILABLE);
                db.update("rooms", rm, "id = ?", new String[]{ String.valueOf(roomId) });
            }

            ContentValues pay = new ContentValues();
            pay.put("payment_status", PAY_STATUS_PAID);
            pay.put("paid_at", System.currentTimeMillis());
            db.update("payments", pay,
                    "booking_id = ? AND method = 'cash' AND payment_status = 'unpaid'",
                    new String[]{ String.valueOf(bookingId) });

            if (r1 > 0) {
                db.setTransactionSuccessful();
                return true;
            }
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /** Ngày nhận phòng của đơn thoả điều kiện; -1 nếu không tìm thấy. */
    private long bookingCheckInDate(SQLiteDatabase db, int bookingId, String condition) {
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT check_in_date FROM bookings WHERE id = ? AND " + condition + " LIMIT 1",
                    new String[]{ String.valueOf(bookingId) });
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Đánh dấu hoàn tiền cho các khoản đã thanh toán của đơn; bỏ qua tiền mặt/chưa trả.
     *  @return số bản ghi payments được đánh dấu hoàn tiền. */
    private int refundPaidPayments(SQLiteDatabase db, int bookingId) {
        ContentValues v = new ContentValues();
        v.put(PaymentTable.PAYMENT_STATUS, PAY_STATUS_REFUNDED);
        return db.update(PaymentTable.TABLE_NAME, v,
                "booking_id = ? AND payment_status = ?",
                new String[]{ String.valueOf(bookingId), PAY_STATUS_PAID });
    }

    public int delete(int bookingId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(BookingTable.TABLE_NAME, "id = ?",
                new String[]{ String.valueOf(bookingId) });
    }

    /* ================================================================
     * 5. HÀM DÙNG CHUNG TRONG LỚP
     * ================================================================ */

    /** Mã đơn "LH"+ngày có thể trùng khi nhiều đơn cùng ngày → thêm hậu tố 01, 02... */
    private String uniqueCode(SQLiteDatabase db, String base) {
        String code = base;
        int suffix = 1;
        while (codeExists(db, code)) {
            code = base + String.format(Locale.US, "%02d", suffix++);
        }
        return code;
    }

    private boolean codeExists(SQLiteDatabase db, String code) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT 1 FROM bookings WHERE booking_code = ? LIMIT 1",
                    new String[]{ code });
            return c.moveToFirst();
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Dựng mệnh đề WHERE động (luôn dùng dấu ?, không nối giá trị vào chuỗi).
     * Alias bắt buộc: b = bookings, u = users. Trả "" nếu không có điều kiện nào,
     * ngược lại trả chuỗi "WHERE ... " (có khoảng trắng cuối).
     */
    private String buildWhere(String status, BookingFilter filter, List<String> args) {
        List<String> conds = new ArrayList<>();

        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            conds.add("b.booking_status = ?");
            args.add(status);
        }

        if (filter != null) {
            if (!filter.getMethods().isEmpty()) {
                conds.add("EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.id " +
                        "AND p.method IN (" + placeholders(filter.getMethods().size()) + "))");
                args.addAll(filter.getMethods());
            }
            if (filter.getDateFrom() > 0) {
                conds.add("b.check_in_date >= ?");
                args.add(String.valueOf(filter.getDateFrom()));
            }
            if (filter.getDateTo() > 0) {
                conds.add("b.check_in_date <= ?");
                args.add(String.valueOf(filter.getDateTo()));
            }
            String kw = filter.getKeyword();
            if (!kw.isEmpty()) {
                conds.add("(u.full_name LIKE ? ESCAPE '\\' " +
                        "OR u.phone LIKE ? ESCAPE '\\' " +
                        "OR REPLACE(u.phone, ' ', '') LIKE ? ESCAPE '\\')");
                args.add("%" + escapeLike(kw) + "%");
                args.add("%" + escapeLike(kw) + "%");
                args.add("%" + escapeLike(kw.replace(" ", "")) + "%");
            }
        }

        if (conds.isEmpty()) return "";
        return "WHERE " + TextUtils.join(" AND ", conds) + " ";
    }

    private String placeholders(int n) {
        String[] marks = new String[n];
        for (int i = 0; i < n; i++) marks[i] = "?";
        return TextUtils.join(",", marks);
    }

    /** Thoát ký tự đặc biệt của LIKE (\ % _) để người dùng gõ gì khớp đúng nấy. */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<BookingWithDetail> queryDetail(String sql, String[] args) {
        List<BookingWithDetail> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(sql, args);
            while (c.moveToNext()) {
                BookingWithDetail d = new BookingWithDetail();
                d.setBooking(cursorToBooking(c));
                d.setTypeName(c.getString(c.getColumnIndexOrThrow("type_name")));
                d.setImageUrl(c.getString(c.getColumnIndexOrThrow("image_url")));
                d.setRoomNumber(c.getString(c.getColumnIndexOrThrow("room_number")));
                d.setCustomerName(c.getString(c.getColumnIndexOrThrow("customer_name")));
                d.setCustomerPhone(c.getString(c.getColumnIndexOrThrow("customer_phone")));
                d.setReviewed(c.getInt(c.getColumnIndexOrThrow("reviewed")) > 0);
                list.add(d);
            }
        } finally {
            if (c != null) c.close();
        }
        return list;
    }

    private Booking cursorToBooking(Cursor c) {
        Booking b = new Booking();
        b.setId(c.getInt(c.getColumnIndexOrThrow(BookingTable.ID)));
        b.setBookingCode(c.getString(c.getColumnIndexOrThrow(BookingTable.BOOKING_CODE)));
        b.setUserId(c.getInt(c.getColumnIndexOrThrow(BookingTable.USER_ID)));
        b.setRoomTypeId(c.getInt(c.getColumnIndexOrThrow(BookingTable.ROOM_TYPE_ID)));
        int roomIdx = c.getColumnIndexOrThrow(BookingTable.ROOM_ID);
        b.setRoomId(c.isNull(roomIdx) ? 0 : c.getInt(roomIdx));   // cột này cho phép NULL
        b.setCheckInDate(c.getLong(c.getColumnIndexOrThrow(BookingTable.CHECK_IN_DATE)));
        b.setCheckOutDate(c.getLong(c.getColumnIndexOrThrow(BookingTable.CHECK_OUT_DATE)));
        b.setNumNights(c.getInt(c.getColumnIndexOrThrow(BookingTable.NUM_NIGHTS)));
        b.setNumGuests(c.getInt(c.getColumnIndexOrThrow(BookingTable.NUM_GUESTS)));
        b.setRoomSubtotal(c.getLong(c.getColumnIndexOrThrow(BookingTable.ROOM_SUBTOTAL)));
        b.setServiceFee(c.getLong(c.getColumnIndexOrThrow(BookingTable.SERVICE_FEE)));
        b.setVatAmount(c.getLong(c.getColumnIndexOrThrow(BookingTable.VAT_AMOUNT)));
        b.setDiscountAmount(c.getLong(c.getColumnIndexOrThrow(BookingTable.DISCOUNT_AMOUNT)));
        b.setTotalAmount(c.getLong(c.getColumnIndexOrThrow(BookingTable.TOTAL_AMOUNT)));
        b.setBookingStatus(c.getString(c.getColumnIndexOrThrow(BookingTable.BOOKING_STATUS)));
        b.setNote(c.getString(c.getColumnIndexOrThrow(BookingTable.NOTE)));
        b.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(BookingTable.CREATED_AT)));
        return b;
    }
}