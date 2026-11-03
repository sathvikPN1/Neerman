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
// Show/hide password: buttons with data-toggle-password="<input id>".
document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-toggle-password]');
    if (!btn) {
        return;
    }
    var input = document.getElementById(btn.dataset.togglePassword);
    if (!input) {
        return;
    }
    var show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    btn.setAttribute('aria-pressed', String(show));
    btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    btn.querySelector('.pw-eye').classList.toggle('hidden', show);
    btn.querySelector('.pw-eye-off').classList.toggle('hidden', !show);
    input.focus();
});
// Set-password form: tick "At least 10 characters" and "Passwords match" as the person types.
document.addEventListener('input', function (e) {
    var form = e.target.closest('[data-password-rules]');
    if (!form) {
        return;
    }
    var pw = form.querySelector('#password').value;
    var confirm = form.querySelector('#confirm').value;
    [['length', pw.length >= 10], ['match', pw.length > 0 && pw === confirm]].forEach(function (rule) {
        var el = form.querySelector('[data-rule="' + rule[0] + '"]');
        el.style.color = rule[1] ? 'var(--color-accent-700)' : 'var(--color-neutral-700)';
        el.querySelector('i').className = rule[1] ? 'icon-circle-check' : 'icon-circle';
    });
});
