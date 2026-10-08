package com.nirmaan.reimburse.common.web;

import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Business-rule errors return the user to the page they came from with the message shown;
 * HTMX requests get an inline error fragment instead.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    public String businessRule(BusinessRuleException e, HttpServletRequest request, HttpServletResponse response,
                               RedirectAttributes redirect, Model model) {
        return back(e.getMessage(), request, response, redirect, model, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect,
                           Model model) {
        return back("Files must be 10 MB or smaller.", request, response, redirect, model, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String accessDenied(AccessDeniedException e, HttpServletRequest request, HttpServletResponse response,
                               Model model) {
        log.info("Access denied for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("status", 403);
        model.addAttribute("message", e.getMessage() == null || e.getMessage().equals("Access Denied")
                ? "You don't have permission to do that." : e.getMessage());
        return Htmx.isHtmx(request) ? "fragments/flash :: inline-error" : "error";
    }

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException e, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("status", 404);
        model.addAttribute("message", e.getMessage());
        return "error";
    }

    private String back(String message, HttpServletRequest request, HttpServletResponse response,
                        RedirectAttributes redirect, Model model, HttpStatus status) {
        if (Htmx.isHtmx(request)) {
            response.setStatus(status.value());
            // Let HTMX swap the error into the page despite the 4xx status.
            response.setHeader("HX-Reswap", "innerHTML");
            model.addAttribute("message", message);
            return "fragments/flash :: inline-error";
        }
        redirect.addFlashAttribute("error", message);
        return "redirect:" + safeReferer(request);
    }

    /** Only redirect back to a path on this site. */
    static String safeReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            String host = request.getServerName();
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(host)) {
                return "/";
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
