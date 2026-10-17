package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.application.shared.lock.IntakeLockPort;

import java.util.ArrayList;
import java.util.List;

/** Fake of the lock port that records the order of the locks taken, for example {@code idem:key}, {@code phone:+84...}. */
public class RecordingIntakeLock implements IntakeLockPort {

    private final List<String> locks = new ArrayList<>();

    public List<String> locks() {
        return locks;
    }

    @Override
    public void lockIdempotencyKey(String key) {
        locks.add("idem:" + key);
    }

    @Override
    public void lockPhone(String phoneNormalized) {
        locks.add("phone:" + phoneNormalized);
    }
}
