package com.savesnapshot;

import java.util.concurrent.atomic.AtomicBoolean;

/** 恢复流程全局锁（common 侧，server tick 与 client 线程都可见）。 */
public final class RestoreState {
    private static final AtomicBoolean RESTORING = new AtomicBoolean(false);

    private RestoreState() {}

    public static boolean isRestoring() {
        return RESTORING.get();
    }

    /** @return true 表示成功获取锁（之前未在恢复中）。 */
    public static boolean tryStart() {
        return RESTORING.compareAndSet(false, true);
    }

    public static void finish() {
        RESTORING.set(false);
    }
}
