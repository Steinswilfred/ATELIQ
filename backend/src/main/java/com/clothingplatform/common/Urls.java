package com.clothingplatform.common;

import com.clothingplatform.exception.BusinessException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class Urls {
    private static final Pattern OK = Pattern.compile("^(https://|http://|/media/)\\S{1,480}$");
    private Urls() {}

    /** Accepts our own uploads (/media/...) or http(s) addresses. Rejects javascript:, data: and anything else. Returns null for blank. */
    public static String clean(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        if (!OK.matcher(u).matches()) throw new BusinessException("INVALID_URL", "Use an uploaded image or an address starting with https://.", HttpStatus.BAD_REQUEST);
        return u;
    }

    private static final Pattern MODEL = Pattern.compile("^(builtin:(shirt|trousers)|(/media/|https://)\\S{1,470}\\.glb)$", Pattern.CASE_INSENSITIVE);

    /** A 3D model reference: a built-in placeholder (builtin:shirt, builtin:trousers) or a .glb file we host or an https address. */
    public static String cleanModel(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        if (!MODEL.matcher(u).matches()) throw new BusinessException("INVALID_URL", "Use a built-in model or a .glb file.", HttpStatus.BAD_REQUEST);
        return u;
    }
}
