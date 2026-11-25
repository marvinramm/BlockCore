/* BlockCore: sets the colour theme before the page is drawn (no white flash). Settings: blockcore.js; default: dark. */
(function () {
    var theme = 'dark';
    try {
        var settings = JSON.parse(localStorage.getItem('blockcore.settings') || '{}');
        theme = settings.theme || 'dark';
    } catch (e) {
        theme = 'dark';
    }
    if (theme === 'system') {
        theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    }
    document.documentElement.setAttribute('data-bc-theme', theme);
})();
