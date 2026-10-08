package com.nirmaan.reimburse.common.web;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.LinkedHashMap;
import java.util.Map;

/** Field → first error message, for templates (forms are records, so th:field is not used). */
public final class FormErrors {

    private FormErrors() {
    }

    public static Map<String, String> of(BindingResult result) {
        Map<String, String> map = new LinkedHashMap<>();
        if (result != null) {
            for (FieldError e : result.getFieldErrors()) {
                map.putIfAbsent(e.getField(), e.isBindingFailure() ? "Enter a valid value" : e.getDefaultMessage());
            }
        }
        return map;
    }
}
