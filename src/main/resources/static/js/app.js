// Send the CSRF token with every HTMX request.
document.addEventListener('htmx:configRequest', function (e) {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) {
        e.detail.headers[header.content] = token.content;
    }
});
// Show server error fragments (4xx) returned to HTMX requests.
document.addEventListener('htmx:beforeSwap', function (e) {
    if (e.detail.xhr.status === 422 || e.detail.xhr.status === 403 || e.detail.xhr.status === 413) {
        e.detail.shouldSwap = true;
        e.detail.isError = false;
    }
});
// "Select all" checkbox for bulk actions.
document.addEventListener('change', function (e) {
    if (e.target.matches('[data-select-all]')) {
        document.querySelectorAll('input[name="' + e.target.dataset.selectAll + '"]').forEach(function (cb) {
            cb.checked = e.target.checked;
        });
    }
});
