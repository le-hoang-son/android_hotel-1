package com.lumina.hotel.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nơi duy nhất tạo luồng nền cho toàn app.
 *
 * Lý do phải có: SQLite chạy đồng bộ ngay trên luồng gọi nó. Nếu truy vấn danh sách
 * ngay trên luồng giao diện, app sẽ giật và có thể bị hệ thống báo ANR
 * ("Application Not Responding").
 *
 * Cách dùng chuẩn trong Activity:
 *
 *   binding.pbLoading.setVisibility(View.VISIBLE);
 *   AppExecutors.io(() -> {
 *       List&lt;RoomType&gt; data = roomTypeDao.getActiveTypes();   // luồng nền
 *       runOnUiThread(() -> {                                    // quay lại luồng UI
 *           binding.pbLoading.setVisibility(View.GONE);
 *           adapter.setData(data);
 *       });
 *   });
 *
 * Trong Fragment thì dùng requireActivity().runOnUiThread(...).
 */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() { }

    /** Chạy một tác vụ CSDL trên luồng nền. */
    public static void io(Runnable task) {
        IO.execute(task);
    }

    /** Chạy một đoạn cập nhật giao diện trên luồng chính. */
    public static void main(Runnable task) {
        MAIN.post(task);
    }

    /**
     * Cập nhật giao diện an toàn từ luồng nền: tự bỏ qua nếu Activity đã bị đóng
     * (tránh crash khi người dùng thoát màn hình trong lúc dữ liệu đang tải).
     */
    public static void runOnUiSafe(Activity activity, Runnable task) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(task);
    }
}
