package com.crypto.crypto.util;

import org.apache.coyote.BadRequestException;

public final class GlobalUtils {
    private GlobalUtils() {
        throw new UnsupportedOperationException();
    }

    public static boolean isNull(Object data) {
        return data != null;
    }
}
