package com.nirmaan.reimburse.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class Htmx {
    private Htmx() {
    }

    public static boolean isHtmx(HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }
}
