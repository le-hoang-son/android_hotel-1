package com.lumina.hotel.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.lumina.hotel.data.DbContract.ReviewTable;
import com.lumina.hotel.data.DatabaseHelper;
import com.lumina.hotel.model.Review;

import java.util.ArrayList;
import java.util.List;

/**
 * Thao tác với bảng reviews.
 * Dùng cho: khối Đánh giá ở màn Chi tiết phòng và nút "Đánh giá" ở màn Đơn của tôi.
 */
public class ReviewDao {

    private final DatabaseHelper helper;

    public ReviewDao(Context context) {
        this.helper = DatabaseHelper.getInstance(context);
    }

    /** Vài đánh giá mới nhất của một loại phòng, kèm tên người đánh giá. */
    public List<Review> getByRoomType(int typeId, int limit) {
        return queryReviews(
                "SELECT r.*, u.full_name AS reviewer_name " +
                "FROM reviews r JOIN users u ON u.id = r.user_id " +
                "WHERE r.room_type_id = ? ORDER BY r.created_at DESC LIMIT ?",
                new String[]{ String.valueOf(typeId), String.valueOf(limit) });
    }

    /** TOÀN BỘ đánh giá của một loại phòng, mới nhất trước — dùng cho nút "Xem tất cả". */
    public List<Review> getAllByRoomType(int typeId) {
        return queryReviews(
                "SELECT r.*, u.full_name AS reviewer_name " +
                "FROM reviews r JOIN users u ON u.id = r.user_id " +
                "WHERE r.room_type_id = ? ORDER BY r.created_at DESC",
                new String[]{ String.valueOf(typeId) });
    }

    private List<Review> queryReviews(String sql, String[] args) {
        List<Review> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(sql, args);
            while (c.moveToNext()) list.add(cursorToReview(c));
        } finally {
            if (c != null) c.close();
        }
        return list;
    }

    /** Điểm trung bình, làm tròn 1 chữ số thập phân. */
    public float getAvgRating(int typeId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT IFNULL(ROUND(AVG(rating), 1), 0) FROM reviews " +
                    "WHERE room_type_id = ?", new String[]{ String.valueOf(typeId) });
            return c.moveToFirst() ? c.getFloat(0) : 0f;
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Số lượt đánh giá theo từng mức sao — dùng cho 5 thanh phân bố ở màn Chi tiết.
     * @return mảng 5 phần tử: [0] = số lượt 5 sao ... [4] = số lượt 1 sao.
     */
    public int[] countByStar(int typeId) {
        int[] result = new int[5];
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT rating, COUNT(*) AS cnt FROM reviews " +
                            "WHERE room_type_id = ? GROUP BY rating",
                    new String[]{ String.valueOf(typeId) });
            while (c.moveToNext()) {
                int star = c.getInt(c.getColumnIndexOrThrow("rating"));
                if (star >= 1 && star <= 5) {
                    result[5 - star] = c.getInt(c.getColumnIndexOrThrow("cnt"));
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public int countByRoomType(int typeId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM reviews WHERE room_type_id = ?",
                    new String[]{ String.valueOf(typeId) });
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Một đơn chỉ được đánh giá một lần. */
    public boolean hasReviewed(int bookingId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT 1 FROM reviews WHERE booking_id = ? LIMIT 1",
                    new String[]{ String.valueOf(bookingId) });
            return c.moveToFirst();
        } finally {
            if (c != null) c.close();
        }
    }

    public long insert(int bookingId, int userId, int typeId, int rating, String comment) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        if (bookingId > 0) v.put(ReviewTable.BOOKING_ID, bookingId);
        v.put(ReviewTable.USER_ID, userId);
        v.put(ReviewTable.ROOM_TYPE_ID, typeId);
        v.put(ReviewTable.RATING, rating);
        v.put(ReviewTable.COMMENT, comment);
        v.put(ReviewTable.CREATED_AT, System.currentTimeMillis());
        return db.insert(ReviewTable.TABLE_NAME, null, v);
    }

    public int delete(int reviewId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(ReviewTable.TABLE_NAME, "id = ?",
                new String[]{ String.valueOf(reviewId) });
    }

    private Review cursorToReview(Cursor c) {
        Review r = new Review();
        r.setId(c.getInt(c.getColumnIndexOrThrow(ReviewTable.ID)));
        int bIdx = c.getColumnIndexOrThrow(ReviewTable.BOOKING_ID);
        r.setBookingId(c.isNull(bIdx) ? 0 : c.getInt(bIdx));
        r.setUserId(c.getInt(c.getColumnIndexOrThrow(ReviewTable.USER_ID)));
        r.setRoomTypeId(c.getInt(c.getColumnIndexOrThrow(ReviewTable.ROOM_TYPE_ID)));
        r.setRating(c.getInt(c.getColumnIndexOrThrow(ReviewTable.RATING)));
        r.setComment(c.getString(c.getColumnIndexOrThrow(ReviewTable.COMMENT)));
        r.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(ReviewTable.CREATED_AT)));
        int nameIdx = c.getColumnIndex("reviewer_name");
        if (nameIdx >= 0) r.setReviewerName(c.getString(nameIdx));
        return r;
    }
}
