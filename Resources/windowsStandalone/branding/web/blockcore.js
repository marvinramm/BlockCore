/*
 * BlockCore: adapts the lab to a local, single-user Arduino lab. Injected into index.html by build-windows.sh (after the lab's own scripts).
 * - the board (Uno, Nano, Mega) is chosen directly in the robot menu; the start page with all robot systems is never shown
 * - only English (default) and German; the choice is remembered
 * - a local user is created and logged in automatically, so that "my programs" and "save" work without a login dialog
 * - the version is shown next to the logo; at start, a small note appears if a newer version was released on GitHub
 * - "Settings" in the edit menu: colour theme (dark by default), language, block size, grid, sounds, start board, serial monitor,
 *   updates and more; stored in localStorage "blockcore.settings" (blockcore-theme.js applies the theme before the page is drawn)
 */
(function () {
    'use strict';
    var BOARDS = [
        ['uno', 'Arduino Uno'],
        ['nano', 'Arduino Nano'],
        ['mega', 'Arduino Mega 2560']
    ];
    var LANGUAGES = [
        ['de', 'Deutsch'],
        ['en', 'English']
    ];
    var RELEASES_API = 'https://api.github.com/repos/marvinramm/BlockCore/releases/latest';
    var ALL_RELEASES_API = 'https://api.github.com/repos/marvinramm/BlockCore/releases?per_page=20';
    var LOCAL_ACCOUNT = 'blockcore';
    var LOCAL_PASSWORD = 'blockcore-local';
    var TEXT = {
        de: { update: 'BlockCore {version} ist verfügbar', download: 'Herunterladen', install: 'Jetzt aktualisieren', downloading: 'Update wird geladen … {percent} %', installing: 'Update wird installiert – BlockCore startet neu …', updateFailed: 'Update fehlgeschlagen', board: 'Board', compiling: 'Programm wird übersetzt …', uploading: 'Wird auf {port} hochgeladen …', done: 'Auf {port} hochgeladen', failed: 'Hochladen fehlgeschlagen', compileFailed: 'Programm hat Fehler' },
        en: { update: 'BlockCore {version} is available', download: 'Download', install: 'Update now', downloading: 'Downloading update … {percent} %', installing: 'Installing update – BlockCore restarts …', updateFailed: 'Update failed', board: 'Board', compiling: 'Compiling program …', uploading: 'Uploading to {port} …', done: 'Uploaded to {port}', failed: 'Upload failed', compileFailed: 'Program has errors' }
    };

    var SETTINGS_KEY = 'blockcore.settings';
    var DEFAULTS = {
        theme: 'dark', // dark, light, system
        blockScale: 1.0,
        grid: false,
        watermark: true,
        sounds: true,
        startBoard: 'last',
        rememberPort: true,
        uploadPanel: true,
        serialBaud: '9600',
        serialTimestamps: false,
        checkUpdates: true,
        updateChannel: 'stable' // stable, beta (pre-releases)
    };
    function loadSettings() {
        var settings = {};
        try {
            settings = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}') || {};
        } catch (e) {
            settings = {};
        }
        Object.keys(DEFAULTS).forEach(function (key) {
            if (settings[key] === undefined) {
                settings[key] = DEFAULTS[key];
            }
        });
        return settings;
    }

    function saveSettings(settings) {
        try {
            localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
        } catch (e) {
            // not stored: the settings are used for this session only
        }
    }

    var settings = loadSettings();

    function store(key, value) {
        try {
            if (value === undefined) {
                return localStorage.getItem('blockcore.' + key);
            }
            localStorage.setItem('blockcore.' + key, value);
        } catch (e) {
            return null;
        }
        return null;
    }

    function modules(names, callback) {
        window.require(names, callback);
    }

    /* waits until the lab has loaded its modules and selected a robot */
    function whenLabReady(callback) {
        var tries = 0;
        (function check() {
            var G = null;
            try {
                if (window.require && window.require.defined && window.require.defined('guiState.controller') && window.require.defined('robot.controller')) {
                    G = window.require('guiState.controller');
                }
            } catch (e) {
                G = null;
            }
            try {
                if (G && typeof G.getRobot === 'function' && G.getRobot()) {
                    callback();
                    return;
                }
            } catch (e) {
                // the lab is still starting
            }
            if (++tries < 600) {
                setTimeout(check, 100);
            }
        })();
    }

    /*
     * without "?loadSystem=..." the lab would show its start page with all robot systems (e.g. after F5, because the lab removes the
     * parameter from the address). Load the board used last instead, before the lab starts.
     */
    if (!/[?&]loadSystem=/.test(location.search)) {
        var startBoard = settings.startBoard && settings.startBoard !== 'last' ? settings.startBoard : store('board');
        location.replace('/?loadSystem=' + encodeURIComponent(startBoard || 'uno'));
        return;
    }

    /* the start page (logo click, "back to robot selection") is not used: the board is chosen in the robot menu */
    document.addEventListener('click', function (event) {
        var target = event.target && event.target.closest && event.target.closest('.menuShowStart, #menuRobotSwitch');
        if (target) {
            event.preventDefault();
            event.stopPropagation();
            event.stopImmediatePropagation();
        }
    }, true);

    function language(G) {
        return String(G.getLanguage() || 'de').toLowerCase();
    }

    function setupLanguages(G) {
        LANGUAGES.forEach(function (entry) {
            var link = document.querySelector('#language a[lang="' + entry[0] + '"]');
            if (!link) {
                return;
            }
            // the name ("Deutsch", "English") is added by the lab's language style sheet (::after)
            link.parentElement.classList.add('blockcore-language');
            link.addEventListener('click', function () {
                store('language', entry[0]);
                setTimeout(function () {
                    markActive(G);
                }, 500);
            });
        });
        // English on the first start, afterwards the language chosen last
        var wanted = store('language') || 'en';
        if (wanted && wanted !== language(G)) {
            var link = document.querySelector('#language a[lang="' + wanted + '"]');
            if (link) {
                link.click();
            }
        }
    }

    function setupBoards(G, R) {
        var robotConnect = document.getElementById('robotConnect');
        if (!robotConnect || document.getElementById('blockcoreBoards')) {
            return;
        }
        var list = robotConnect.parentElement;
        var heading = document.createElement('li');
        heading.id = 'blockcoreBoards';
        heading.className = 'blockcore-heading';
        heading.textContent = (TEXT[language(G)] || TEXT.en).board;
        var divider = document.createElement('li');
        divider.innerHTML = '<hr class="dropdown-divider">';
        list.appendChild(divider);
        list.appendChild(heading);
        BOARDS.forEach(function (board) {
            var item = document.createElement('li');
            item.className = 'blockcore-board';
            item.setAttribute('data-board', board[0]);
            var link = document.createElement('a');
            link.className = 'dropdown-item';
            link.href = '#';
            link.textContent = board[1];
            link.addEventListener('click', function (event) {
                event.preventDefault();
                if (G.getRobot() !== board[0]) {
                    R.switchRobot(board[0], {}, false, function () {
                        markActive(G);
                    });
                    setTimeout(function () {
                        markActive(G);
                    }, 1500);
                }
            });
            item.appendChild(link);
            list.appendChild(item);
        });
        markActive(G);
    }

    function markActive(G) {
        var robot = G.getRobot();
        if (robot) {
            store('board', robot);
        }
        Array.prototype.forEach.call(document.querySelectorAll('#head-navigation-robot .blockcore-board'), function (item) {
            item.classList.toggle('active', item.getAttribute('data-board') === robot);
        });
        var current = language(G);
        Array.prototype.forEach.call(document.querySelectorAll('#language li.blockcore-language'), function (item) {
            var link = item.querySelector('a');
            item.classList.toggle('active', !!link && link.getAttribute('lang') === current);
        });
        var heading = document.getElementById('blockcoreBoards');
        if (heading) {
            heading.textContent = (TEXT[current] || TEXT.en).board;
        }
    }

    /* one local user for this computer: the lab saves programs only for logged-in users */
    function autoLogin(G, U) {
        if (G.isUserLoggedIn()) {
            return;
        }
        U.login(LOCAL_ACCOUNT, LOCAL_PASSWORD, function (result) {
            if (result.rc === 'ok') {
                G.setLogin(result);
                return;
            }
            U.createUserToServer(LOCAL_ACCOUNT, 'BlockCore', '', LOCAL_PASSWORD, '0', language(G), function (created) {
                if (created.rc === 'ok') {
                    U.login(LOCAL_ACCOUNT, LOCAL_PASSWORD, function (second) {
                        if (second.rc === 'ok') {
                            G.setLogin(second);
                        }
                    });
                }
            });
        });
    }

    /*
     * upload display: a panel at the top with a progress bar (compiling, uploading, done with a check mark) and a spinning ring around
     * the run button. The progress is estimated: compiling fills the bar up to 60 %, uploading up to 95 %.
     */
    function setupUploadDisplay(G) {
        var panel = document.createElement('div');
        panel.id = 'blockcoreUpload';
        panel.innerHTML = '<div class="bc-row"><span class="bc-icon"></span><span class="bc-text"></span></div><div class="bc-bar"><div class="bc-fill"></div></div>';
        document.body.appendChild(panel);
        var fill = panel.querySelector('.bc-fill');
        var textElement = panel.querySelector('.bc-text');
        var progress = 0, target = 0, timer = null, hideTimer = null;

        function text(key, port) {
            return ((TEXT[language(G)] || TEXT.en)[key] || '').replace('{port}', port || '');
        }
        function runButton() {
            return document.getElementById('runOnBrick');
        }
        function ring(on) {
            var button = runButton();
            if (!button) {
                return;
            }
            var existing = button.querySelector('.bc-ring');
            if (on && !existing) {
                var circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
                circle.setAttribute('class', 'bc-ring');
                circle.setAttribute('cx', '24');
                circle.setAttribute('cy', '24');
                circle.setAttribute('r', '19');
                button.appendChild(circle);
            } else if (!on && existing) {
                existing.parentNode.removeChild(existing);
            }
        }
        function animate() {
            // move towards the target, slower and slower: the bar never stops completely while waiting
            progress += Math.max((target - progress) * 0.04, 0.05);
            if (progress > target) {
                progress = target;
            }
            fill.style.width = progress + '%';
        }
        function show(state, message, until) {
            clearTimeout(hideTimer);
            panel.className = 'bc-visible bc-' + state;
            textElement.textContent = message;
            target = until;
            if (!timer) {
                timer = setInterval(animate, 50);
            }
            var button = runButton();
            if (button) {
                button.classList.toggle('bc-busy', state === 'busy');
                button.classList.toggle('bc-done', state === 'done');
            }
            ring(state === 'busy');
            if (state !== 'busy') {
                progress = state === 'done' ? 100 : progress;
                fill.style.width = progress + '%';
                clearInterval(timer);
                timer = null;
                hideTimer = setTimeout(function () {
                    panel.className = '';
                    if (button) {
                        button.classList.remove('bc-done');
                    }
                }, state === 'done' ? 2500 : 4000);
            }
        }
        function start() {
            progress = 0;
            fill.style.width = '0%';
            show('busy', text('compiling'), 60);
        }
        function data(options) {
            try {
                return JSON.parse(options.data || '{}');
            } catch (e) {
                return {};
            }
        }
        // the save button is hidden (blockcore.css): move the buttons right of it into its place
        (function closeGap() {
            var save = document.getElementById('saveProgram');
            if (!save) {
                setTimeout(closeGap, 500);
                return;
            }
            var x = /translate\((\d+)/.exec(save.getAttribute('transform') || '');
            Array.prototype.forEach.call(save.parentNode.children, function (button) {
                var match = /translate\((\d+),\s*(\d+)\)/.exec(button.getAttribute('transform') || '');
                if (x && match && +match[1] > +x[1]) {
                    button.setAttribute('transform', 'translate(' + (+match[1] - 50) + ',' + match[2] + ')');
                }
            });
        })();
        $(document).ajaxSend(function (event, xhr, options) {
            if (/\/projectWorkflow\/run$/.test(options.url || '')) {
                start();
            } else if (/\/local\/upload$/.test(options.url || '')) {
                show('busy', text('uploading', data(options).port), 95);
            }
        });
        $(document).ajaxComplete(function (event, xhr, options) {
            var answer = xhr.responseJSON || {};
            if (/\/projectWorkflow\/run$/.test(options.url || '')) {
                if (answer.rc !== 'ok' || !answer.compiledCode) {
                    show('error', text('compileFailed'), progress);
                }
            } else if (/\/local\/upload$/.test(options.url || '')) {
                var port = data(options).port;
                if (xhr.status === 200 && answer.rc === 'ok') {
                    show('done', text('done', port), 100);
                } else {
                    show('error', text('failed'), progress);
                }
            }
        });
    }

    /** "1.0.15" -> [1, 0, 15, ''], "1.1.20-beta" -> [1, 1, 20, 'beta']; null for anything else (e.g. "1.0-dev") */
    function parseVersion(text) {
        var match = /^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.]+))?$/.exec(String(text || '').trim());
        return match ? [+match[1], +match[2], +match[3], match[4] || ''] : null;
    }

    function versionText(v) {
        return v[0] + '.' + v[1] + '.' + v[2] + (v[3] ? '-' + v[3] : '');
    }

    /* the numbers decide; with equal numbers a stable version is newer than a pre-release */
    function isNewer(a, b) {
        for (var i = 0; i < 3; i++) {
            if (a[i] !== b[i]) {
                return a[i] > b[i];
            }
        }
        return !a[3] && !!b[3];
    }

    /* shows the version next to the logo and looks (once per start, silently) for a newer release */
    function setupVersion(G) {
        fetch('/blockcore/version.json', { cache: 'no-store' })
            .then(function (response) {
                return response.json();
            })
            .then(function (info) {
                var version = String(info.version || '');
                var logo = document.getElementById('logoShowStart');
                if (version && logo && !document.getElementById('blockcoreVersion')) {
                    var label = document.createElement('span');
                    label.id = 'blockcoreVersion';
                    label.textContent = 'v' + version;
                    logo.parentNode.insertBefore(label, logo.nextSibling);
                }
                document.title = 'BlockCore ' + version;
                currentVersion = version;
                checkForUpdate(G, version);
            })
            .catch(function () {
                // no version file (development): nothing to show
            });
    }

    var currentVersion = '';

    function checkForUpdate(G, version) {
        var current = parseVersion(version);
        if (!current || !window.fetch || !settings.checkUpdates) {
            return;
        }
        try {
            // once per start of BlockCore, not after every F5 (the window keeps its session storage until it is closed)
            if (sessionStorage.getItem('blockcore.updateChecked') === 'yes') {
                return;
            }
            sessionStorage.setItem('blockcore.updateChecked', 'yes');
        } catch (e) {
            // no session storage: check anyway
        }
        var controller = window.AbortController ? new AbortController() : null;
        var timeout = setTimeout(function () {
            if (controller) {
                controller.abort();
            }
        }, 8000);
        var beta = settings.updateChannel === 'beta';
        fetch(beta ? ALL_RELEASES_API : RELEASES_API, { cache: 'no-store', signal: controller ? controller.signal : undefined, headers: { Accept: 'application/vnd.github+json' } })
            .then(function (response) {
                return response.ok ? response.json() : null;
            })
            .then(function (answer) {
                clearTimeout(timeout);
                // stable: the latest release (GitHub leaves pre-releases out); beta: the newest of all releases
                var best = null;
                (Array.isArray(answer) ? answer : answer ? [answer] : []).forEach(function (release) {
                    var version = !release.draft && parseVersion(release.tag_name);
                    if (version && (!best || isNewer(version, best.version))) {
                        best = { version: version, release: release };
                    }
                });
                if (best && isNewer(best.version, current)) {
                    showUpdate(G, versionText(best.version), best.release.html_url);
                }
            })
            .catch(function () {
                // offline or GitHub not reachable: say nothing
                clearTimeout(timeout);
            });
    }

    /*
     * note about a newer version. An installed BlockCore installs the update itself ("update now": the server downloads the installer,
     * then BlockCore.exe closes the windows and starts it); an unzipped one links to the download page.
     */
    function showUpdate(G, version, url) {
        var texts = TEXT[language(G)] || TEXT.en;
        var note = document.createElement('div');
        var closeTimer = null;
        var installable = false;
        note.id = 'blockcoreUpdate';
        note.innerHTML = '<span class="bc-update-icon"></span><div class="bc-update-body"><span class="bc-update-text"></span>' +
            '<div class="bc-update-bar"><div class="bc-update-fill"></div></div></div>' +
            '<a class="bc-update-link" target="_blank" rel="noopener"></a><button type="button" class="bc-update-close" aria-label="close">&times;</button>';
        var textElement = note.querySelector('.bc-update-text');
        var fill = note.querySelector('.bc-update-fill');
        var link = note.querySelector('.bc-update-link');
        textElement.textContent = texts.update.replace('{version}', version);
        link.textContent = texts.download;
        link.href = /^https:\/\/github\.com\//.test(url || '') ? url : 'https://github.com/marvinramm/BlockCore/releases/latest';
        function close() {
            note.classList.remove('bc-visible');
            setTimeout(function () {
                if (note.parentNode) {
                    note.parentNode.removeChild(note);
                }
            }, 400);
        }
        function poll() {
            fetch('/rest/local/update', { cache: 'no-store' })
                .then(function (response) {
                    return response.json();
                })
                .then(function (status) {
                    if (status.state === 'DOWNLOADING') {
                        textElement.textContent = texts.downloading.replace('{percent}', status.percent || 0);
                        fill.style.width = (status.percent || 0) + '%';
                        setTimeout(poll, 400);
                    } else if (status.state === 'READY') {
                        note.classList.add('bc-installing');
                        textElement.textContent = texts.installing;
                        fill.style.width = '100%';
                    } else if (status.state === 'FAILED') {
                        failed();
                    } else {
                        setTimeout(poll, 400);
                    }
                })
                .catch(function () {
                    // the server stops for the installation: that is expected after READY
                    if (!note.classList.contains('bc-installing')) {
                        failed();
                    }
                });
        }
        function failed() {
            note.classList.remove('bc-updating');
            note.classList.add('bc-failed');
            textElement.textContent = texts.updateFailed;
            link.textContent = texts.download;
            link.onclick = null;
        }
        note.querySelector('.bc-update-close').addEventListener('click', close);
        document.body.appendChild(note);
        // installed (not unzipped)? then the update is installed directly
        fetch('/rest/local/update', { cache: 'no-store' })
            .then(function (response) {
                return response.json();
            })
            .then(function (status) {
                if (status.canInstall) {
                    installable = true;
                    link.textContent = texts.install;
                    link.removeAttribute('target');
                    link.onclick = function (event) {
                        event.preventDefault();
                        clearTimeout(closeTimer);
                        note.classList.add('bc-updating');
                        textElement.textContent = texts.downloading.replace('{percent}', 0);
                        fetch('/rest/local/update?channel=' + encodeURIComponent(settings.updateChannel || 'stable'), { method: 'POST', cache: 'no-store' })
                            .then(function () {
                                poll();
                            })
                            .catch(failed);
                    };
                }
            })
            .catch(function () {
                // keep the download link
            });
        link.addEventListener('click', function () {
            // the download page opens in the browser: the note is not needed any more (not so for "update now")
            if (!installable) {
                setTimeout(close, 300);
            }
        });
        setTimeout(function () {
            note.classList.add('bc-visible');
        }, 50);
        closeTimer = setTimeout(function () {
            if (!note.classList.contains('bc-updating')) {
                close();
            }
        }, 20000);
    }

    /*
     * the narrow layout (small window) collapses the menus into a button. If that menu was open when the window gets wide again, the
     * navigation bars keep the class "show" and the tab bar covers the dropdown menus (edit, robot, languages): they open but cannot be
     * seen or clicked. Close that state as soon as the wide layout is back; in the narrow layout the version label is hidden.
     */
    function setupResizeFix() {
        function narrow() {
            var toggler = document.getElementById('navbarButtonsHead');
            return !!toggler && getComputedStyle(toggler).display !== 'none';
        }
        function check() {
            var isNarrow = narrow();
            document.documentElement.classList.toggle('blockcore-narrow', isNarrow);
            if (isNarrow) {
                return;
            }
            Array.prototype.forEach.call(document.querySelectorAll('#header .navbar-collapse.show'), function (bar) {
                bar.classList.remove('show');
            });
            var toggler = document.getElementById('navbarButtonsHead');
            if (toggler) {
                toggler.classList.add('collapsed');
                toggler.setAttribute('aria-expanded', 'false');
            }
        }
        var timer = null;
        window.addEventListener('resize', function () {
            clearTimeout(timer);
            timer = setTimeout(check, 150);
        });
        check();
    }

    /*
     * the lab's Blockly loads only NEPO XML (<instance x y><block/></instance>). Blockly's own undo and redo hand it plain <block>
     * elements (redo of a new block, undo of a deleted block), which were silently ignored: wrap them into instances.
     */
    function patchUndoXml() {
        var Xml = window.Blockly && window.Blockly.Xml;
        if (!Xml || Xml.domToWorkspace.blockcorePatched) {
            return;
        }
        var original = Xml.domToWorkspace;
        var patched = function (xml, workspace) {
            if (xml && xml.childNodes && !(xml instanceof window.Blockly.Workspace)) {
                Array.prototype.slice.call(xml.childNodes).forEach(function (node) {
                    var name = String(node.nodeName || '').toLowerCase();
                    if (name === 'block' || name === 'shadow') {
                        var instance = document.createElementNS(xml.namespaceURI || null, 'instance');
                        instance.setAttribute('x', node.getAttribute('x') || '0');
                        instance.setAttribute('y', node.getAttribute('y') || '0');
                        xml.replaceChild(instance, node);
                        instance.appendChild(node);
                    }
                });
            }
            return original.apply(this, arguments);
        };
        patched.blockcorePatched = true;
        Xml.domToWorkspace = patched;
    }

    /*
     * undo and redo with the keyboard: Ctrl+Z undoes, Ctrl+Y and Ctrl+Shift+Z redo (Cmd on a Mac), in the workspace that is shown
     * (program or configuration). Blockly itself only knows Ctrl+Z and Ctrl+Shift+Z, and only while the workspace has the focus.
     * Typing in a text field, a block field or the source code editor keeps its own undo.
     */
    function setupUndoRedo() {
        patchUndoXml();
        function isTyping(target) {
            if (!target || !target.closest) {
                return false;
            }
            if (target.isContentEditable || target.closest('.ace_editor, .blocklyHtmlInput, .blocklyWidgetDiv')) {
                return true;
            }
            var tag = String(target.tagName || '').toLowerCase();
            return tag === 'textarea' || tag === 'select' || (tag === 'input' && !/^(checkbox|radio|button|submit)$/i.test(target.type || ''));
        }
        function visibleWorkspace() {
            var G = window.require('guiState.controller');
            var configuration = document.getElementById('bricklyDiv');
            if (configuration && configuration.offsetParent !== null && G.getBricklyWorkspace) {
                return G.getBricklyWorkspace();
            }
            return G.getBlocklyWorkspace ? G.getBlocklyWorkspace() : window.Blockly && window.Blockly.getMainWorkspace();
        }
        document.addEventListener('keydown', function (event) {
            if (!(event.ctrlKey || event.metaKey) || event.altKey || isTyping(event.target)) {
                return;
            }
            var key = String(event.key || '').toLowerCase();
            var undo = (key === 'z' || event.keyCode === 90) && !event.shiftKey;
            var redo = ((key === 'y' || event.keyCode === 89) && !event.shiftKey) || ((key === 'z' || event.keyCode === 90) && event.shiftKey);
            if (!undo && !redo) {
                return;
            }
            if (document.querySelector('.modal.show')) {
                return; // a dialog is open
            }
            var workspace;
            try {
                workspace = visibleWorkspace();
            } catch (e) {
                return;
            }
            if (!workspace || typeof workspace.undo !== 'function') {
                return;
            }
            if (window.Blockly && window.Blockly.dragMode_ && window.Blockly.dragMode_ !== 0) {
                return; // a block is being dragged
            }
            event.preventDefault();
            event.stopImmediatePropagation(); // Blockly would undo a second time
            if (window.Blockly && window.Blockly.hideChaff) {
                window.Blockly.hideChaff();
            }
            workspace.undo(redo);
        }, true);
    }

    /* ------------------------------------------------------------------ settings */

    var SETTINGS_TEXT = {
        de: {
            menu: 'Einstellungen', title: 'Einstellungen', close: 'Schließen', reset: 'Zurücksetzen',
            appearance: 'Darstellung', theme: 'Farbschema', dark: 'Dunkel', light: 'Hell', system: 'Wie Windows',
            language: 'Sprache', workspace: 'Programmierfläche', blockScale: 'Blockgröße', grid: 'Raster anzeigen',
            watermark: 'Logo im Hintergrund', sounds: 'Klicktöne der Blöcke', start: 'Start', startBoard: 'Board beim Start',
            last: 'Zuletzt benutztes', upload: 'Hochladen', rememberPort: 'Letzten COM-Port wieder wählen',
            uploadPanel: 'Fortschritt beim Hochladen anzeigen', serial: 'Serieller Monitor', serialBaud: 'Baudrate',
            serialTimestamps: 'Zeitstempel', updates: 'Updates', checkUpdates: 'Beim Start nach neuer Version suchen',
            updateChannel: 'Update-Kanal', stable: 'Stabil', beta: 'Beta (Vorabversionen)',
            about: 'Über BlockCore', aboutText: 'Blockprogrammierung für Arduino und ESP32, basierend auf dem Open Roberta Lab.',
            licenses: 'Lizenzen', shortcuts: 'Tastenkürzel', shortcutsText: 'Strg+Z rückgängig · Strg+Y wiederholen · Strg+, Einstellungen'
        },
        en: {
            menu: 'Settings', title: 'Settings', close: 'Close', reset: 'Reset',
            appearance: 'Appearance', theme: 'Theme', dark: 'Dark', light: 'Light', system: 'Like Windows',
            language: 'Language', workspace: 'Workspace', blockScale: 'Block size', grid: 'Show grid',
            watermark: 'Logo in the background', sounds: 'Block click sounds', start: 'Start', startBoard: 'Board at start',
            last: 'Last used', upload: 'Upload', rememberPort: 'Select the last COM port again',
            uploadPanel: 'Show the upload progress', serial: 'Serial monitor', serialBaud: 'Baud rate',
            serialTimestamps: 'Timestamps', updates: 'Updates', checkUpdates: 'Look for a new version at start',
            updateChannel: 'Update channel', stable: 'Stable', beta: 'Beta (pre-releases)',
            about: 'About BlockCore', aboutText: 'Block programming for Arduino and ESP32, based on the Open Roberta Lab.',
            licenses: 'Licenses', shortcuts: 'Keyboard shortcuts', shortcutsText: 'Ctrl+Z undo · Ctrl+Y redo · Ctrl+, settings'
        }
    };

    function resolvedTheme() {
        if (settings.theme === 'system') {
            return window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
        }
        return settings.theme === 'light' ? 'light' : 'dark';
    }

    var originalPlayAudio = null;

    function applySettings(changed) {
        var root = document.documentElement;
        root.setAttribute('data-bc-theme', resolvedTheme());
        root.classList.toggle('bc-grid', !!settings.grid);
        root.classList.toggle('bc-no-watermark', !settings.watermark);
        root.classList.toggle('bc-no-upload-panel', !settings.uploadPanel);
        if (!changed || changed === 'blockScale') {
            applyBlockScale(true);
        }
        var Blockly = window.Blockly;
        if (Blockly && Blockly.WorkspaceSvg && Blockly.WorkspaceSvg.prototype.playAudio) {
            if (!originalPlayAudio) {
                originalPlayAudio = Blockly.WorkspaceSvg.prototype.playAudio;
            }
            Blockly.WorkspaceSvg.prototype.playAudio = settings.sounds ? originalPlayAudio : function () {};
        }
        if (!settings.rememberPort) {
            try {
                localStorage.removeItem('openroberta.localSerial.lastUploadPort');
            } catch (e) {
                // ignore
            }
        }
        // the serial monitor window reads these
        try {
            localStorage.setItem('serial.baud', String(settings.serialBaud));
            localStorage.setItem('serial.timestamps', settings.serialTimestamps ? '1' : '0');
        } catch (e) {
            // ignore
        }
    }

    /* block size of the program workspace (the configuration keeps the zoom of the lab, which fits the board) */
    var scaledWorkspace = null;
    function applyBlockScale(force) {
        var ws = null;
        try {
            ws = window.require('guiState.controller').getBlocklyWorkspace();
        } catch (e) {
            return;
        }
        if (ws && ws.setScale && (force || ws !== scaledWorkspace)) {
            scaledWorkspace = ws;
            ws.setScale(Number(settings.blockScale) || 1);
        }
    }

    function settingsText(G) {
        return SETTINGS_TEXT[language(G)] || SETTINGS_TEXT.en;
    }

    function addSettingsMenu(G) {
        ['#head-navigation-program-edit', '#head-navigation-configuration-edit'].forEach(function (selector) {
            var menu = document.querySelector(selector + ' .dropdown-menu');
            if (!menu || menu.querySelector('.blockcore-settings-item')) {
                return;
            }
            var divider = document.createElement('li');
            divider.innerHTML = '<hr class="dropdown-divider">';
            var item = document.createElement('li');
            item.className = 'blockcore-settings-item';
            item.innerHTML = '<a class="dropdown-item" href="#"><span class="typcn typcn-cog-outline"></span><span class="bc-settings-label"></span></a>';
            item.querySelector('a').addEventListener('click', function (event) {
                event.preventDefault();
                openSettings(G);
            });
            menu.appendChild(divider);
            menu.appendChild(item);
        });
        updateSettingsMenuText(G);
    }

    function updateSettingsMenuText(G) {
        Array.prototype.forEach.call(document.querySelectorAll('.bc-settings-label'), function (label) {
            label.textContent = ' ' + settingsText(G).menu;
        });
    }

    function openSettings(G) {
        var t = settingsText(G);
        var old = document.getElementById('bcSettings');
        if (old) {
            old.parentNode.removeChild(old);
        }
        var boards = BOARDS.map(function (b) {
            return '<option value="' + b[0] + '">' + b[1] + '</option>';
        }).join('');
        var bauds = [300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 74880, 115200, 230400, 250000, 500000, 1000000].map(function (b) {
            return '<option value="' + b + '">' + b + '</option>';
        }).join('');
        var overlay = document.createElement('div');
        overlay.id = 'bcSettings';
        overlay.innerHTML =
            '<div class="bc-dialog" role="dialog" aria-modal="true" aria-labelledby="bcSettingsTitle">' +
            '<div class="bc-dialog-head"><h2 id="bcSettingsTitle">' + t.title + '</h2><button type="button" class="bc-x" data-close aria-label="' + t.close + '">&times;</button></div>' +
            '<div class="bc-dialog-body">' +
            '<section><h3>' + t.appearance + '</h3>' +
            '<div class="bc-row"><span>' + t.theme + '</span><div class="bc-seg" data-setting="theme">' +
            '<button type="button" data-value="dark">' + t.dark + '</button><button type="button" data-value="light">' + t.light + '</button>' +
            '<button type="button" data-value="system">' + t.system + '</button></div></div>' +
            '<div class="bc-row"><span>' + t.language + '</span><div class="bc-seg" data-setting="language">' +
            '<button type="button" data-value="en">English</button><button type="button" data-value="de">Deutsch</button></div></div>' +
            '</section>' +
            '<section><h3>' + t.workspace + '</h3>' +
            '<div class="bc-row"><span>' + t.blockScale + '</span><div class="bc-range"><input type="range" min="0.6" max="1.6" step="0.1" data-setting="blockScale"><output></output></div></div>' +
            '<label class="bc-row bc-switch"><span>' + t.grid + '</span><input type="checkbox" data-setting="grid"><i></i></label>' +
            '<label class="bc-row bc-switch"><span>' + t.watermark + '</span><input type="checkbox" data-setting="watermark"><i></i></label>' +
            '<label class="bc-row bc-switch"><span>' + t.sounds + '</span><input type="checkbox" data-setting="sounds"><i></i></label>' +
            '</section>' +
            '<section><h3>' + t.start + ' &amp; ' + t.upload + '</h3>' +
            '<div class="bc-row"><span>' + t.startBoard + '</span><select data-setting="startBoard"><option value="last">' + t.last + '</option>' + boards + '</select></div>' +
            '<label class="bc-row bc-switch"><span>' + t.rememberPort + '</span><input type="checkbox" data-setting="rememberPort"><i></i></label>' +
            '<label class="bc-row bc-switch"><span>' + t.uploadPanel + '</span><input type="checkbox" data-setting="uploadPanel"><i></i></label>' +
            '</section>' +
            '<section><h3>' + t.serial + '</h3>' +
            '<div class="bc-row"><span>' + t.serialBaud + '</span><select data-setting="serialBaud">' + bauds + '</select></div>' +
            '<label class="bc-row bc-switch"><span>' + t.serialTimestamps + '</span><input type="checkbox" data-setting="serialTimestamps"><i></i></label>' +
            '</section>' +
            '<section><h3>' + t.updates + '</h3>' +
            '<label class="bc-row bc-switch"><span>' + t.checkUpdates + '</span><input type="checkbox" data-setting="checkUpdates"><i></i></label>' +
            '<div class="bc-row"><span>' + t.updateChannel + '</span><div class="bc-seg" data-setting="updateChannel">' +
            '<button type="button" data-value="stable">' + t.stable + '</button><button type="button" data-value="beta">' + t.beta + '</button></div></div>' +
            '</section>' +
            '<section class="bc-about"><h3>' + t.about + '</h3><p>' + t.aboutText + '</p>' +
            '<p class="bc-muted"><span class="bc-version"></span> · <a href="https://github.com/marvinramm/BlockCore" target="_blank" rel="noopener">GitHub</a> · ' +
            '<a href="https://github.com/marvinramm/BlockCore/blob/main/THIRD-PARTY-NOTICES.md" target="_blank" rel="noopener">' + t.licenses + '</a></p>' +
            '<p class="bc-muted">' + t.shortcuts + ': ' + t.shortcutsText + '</p></section>' +
            '</div>' +
            '<div class="bc-dialog-foot"><button type="button" class="bc-btn" data-reset>' + t.reset + '</button><button type="button" class="bc-btn bc-primary" data-close>' + t.close + '</button></div>' +
            '</div>';
        document.body.appendChild(overlay);
        var version = document.getElementById('blockcoreVersion');
        overlay.querySelector('.bc-version').textContent = 'BlockCore ' + (version ? version.textContent.replace(/^v/, '') : '');

        function sync() {
            Array.prototype.forEach.call(overlay.querySelectorAll('[data-setting]'), function (control) {
                var key = control.getAttribute('data-setting');
                var value = key === 'language' ? language(G) : settings[key];
                if (control.classList.contains('bc-seg')) {
                    Array.prototype.forEach.call(control.querySelectorAll('button'), function (button) {
                        button.classList.toggle('active', button.getAttribute('data-value') === String(value));
                    });
                } else if (control.type === 'checkbox') {
                    control.checked = !!value;
                } else {
                    control.value = String(value);
                    if (control.type === 'range') {
                        control.parentNode.querySelector('output').textContent = Math.round(Number(value) * 100) + ' %';
                    }
                }
            });
        }
        function set(key, value) {
            if (key === 'language') {
                var link = document.querySelector('#language a[lang="' + value + '"]');
                if (link) {
                    link.click();
                }
                setTimeout(function () {
                    updateSettingsMenuText(G);
                    openSettings(G); // texts in the new language
                }, 600);
                return;
            }
            settings[key] = value;
            saveSettings(settings);
            applySettings(key);
            sync();
            if ((key === 'updateChannel' || key === 'checkUpdates') && value !== false && currentVersion) {
                try {
                    sessionStorage.removeItem('blockcore.updateChecked');
                } catch (e) {
                    // ignore
                }
                var note = document.getElementById('blockcoreUpdate');
                if (note && note.parentNode) {
                    note.parentNode.removeChild(note);
                }
                checkForUpdate(G, currentVersion);
            }
        }
        Array.prototype.forEach.call(overlay.querySelectorAll('.bc-seg'), function (group) {
            group.addEventListener('click', function (event) {
                var button = event.target.closest('button');
                if (button) {
                    set(group.getAttribute('data-setting'), button.getAttribute('data-value'));
                }
            });
        });
        Array.prototype.forEach.call(overlay.querySelectorAll('input[data-setting], select[data-setting]'), function (control) {
            control.addEventListener(control.type === 'range' ? 'input' : 'change', function () {
                var key = control.getAttribute('data-setting');
                set(key, control.type === 'checkbox' ? control.checked : control.type === 'range' ? Number(control.value) : control.value);
            });
        });
        function close() {
            document.removeEventListener('keydown', onKey, true);
            overlay.classList.remove('bc-open');
            setTimeout(function () {
                if (overlay.parentNode) {
                    overlay.parentNode.removeChild(overlay);
                }
            }, 200);
        }
        function onKey(event) {
            if (event.key === 'Escape') {
                event.stopPropagation();
                close();
            }
        }
        document.addEventListener('keydown', onKey, true);
        overlay.addEventListener('click', function (event) {
            if (event.target === overlay || event.target.closest('[data-close]')) {
                close();
            }
            if (event.target.closest('[data-reset]')) {
                settings = loadSettingsFrom({});
                saveSettings(settings);
                applySettings();
                sync();
            }
        });
        sync();
        setTimeout(function () {
            overlay.classList.add('bc-open');
        }, 10);
    }

    function loadSettingsFrom(values) {
        var result = {};
        Object.keys(DEFAULTS).forEach(function (key) {
            result[key] = values[key] === undefined ? DEFAULTS[key] : values[key];
        });
        return result;
    }

    function setupSettings(G) {
        addSettingsMenu(G);
        applySettings();
        // the menu texts follow the language; a new program workspace gets the block size once (zooming by hand stays possible)
        setInterval(function () {
            updateSettingsMenuText(G);
            applyBlockScale(false);
        }, 1500);
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: light)').addListener(function () {
                if (settings.theme === 'system') {
                    applySettings('theme');
                }
            });
        }
        // Ctrl+, opens the settings
        document.addEventListener('keydown', function (event) {
            if ((event.ctrlKey || event.metaKey) && event.key === ',') {
                event.preventDefault();
                openSettings(G);
            }
        });
    }

    whenLabReady(function () {
        modules(['guiState.controller', 'robot.controller', 'user.model'], function (G, R, U) {
            setupLanguages(G);
            setupBoards(G, R);
            setupUploadDisplay(G);
            setupVersion(G);
            setupSettings(G);
            setupResizeFix();
            setupUndoRedo();
            autoLogin(G, U);
            // the robot or the language can change elsewhere (e.g. loading a program): keep the check marks right
            setInterval(function () {
                markActive(G);
            }, 1000);
        });
    });
})();
