package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.shared.OtpHasher;

/** A readable stand-in for the keyed hash; the real one has its own test. */
public final class TestOtpHasher implements OtpHasher {

    @Override
    public String hash(long rowId, String code) {
        return "hash-of:" + rowId + ":" + code;
    }
}
