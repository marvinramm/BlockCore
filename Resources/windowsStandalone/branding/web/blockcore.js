/*
 * BlockCore: adapts the lab to a local, single-user Arduino lab. Injected into index.html by build-windows.sh (after the lab's own scripts).
 * - the board (Uno, Nano, Mega) is chosen directly in the robot menu; the start page with all robot systems is never shown
 * - only English (default) and German; the choice is remembered
 * - a local user is created and logged in automatically, so that "my programs" and "save" work without a login dialog
 * - the version is shown next to the logo; at start, a small note appears if a newer version was released on GitHub
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
    var LOCAL_ACCOUNT = 'blockcore';
    var LOCAL_PASSWORD = 'blockcore-local';
    var TEXT = {
        de: { update: 'BlockCore {version} ist verfügbar', download: 'Herunterladen', install: 'Jetzt aktualisieren', downloading: 'Update wird geladen … {percent} %', installing: 'Update wird installiert – BlockCore startet neu …', updateFailed: 'Update fehlgeschlagen', board: 'Board', compiling: 'Programm wird übersetzt …', uploading: 'Wird auf {port} hochgeladen …', done: 'Auf {port} hochgeladen', failed: 'Hochladen fehlgeschlagen', compileFailed: 'Programm hat Fehler' },
        en: { update: 'BlockCore {version} is available', download: 'Download', install: 'Update now', downloading: 'Downloading update … {percent} %', installing: 'Installing update – BlockCore restarts …', updateFailed: 'Update failed', board: 'Board', compiling: 'Compiling program …', uploading: 'Uploading to {port} …', done: 'Uploaded to {port}', failed: 'Upload failed', compileFailed: 'Program has errors' }
    };

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
        location.replace('/?loadSystem=' + encodeURIComponent(store('board') || 'uno'));
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

    /** "1.0.15" -> [1, 0, 15]; null for anything else (e.g. "1.0-dev") */
    function parseVersion(text) {
        var match = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(text || '').trim());
        return match ? [+match[1], +match[2], +match[3]] : null;
    }

    function isNewer(a, b) {
        for (var i = 0; i < 3; i++) {
            if (a[i] !== b[i]) {
                return a[i] > b[i];
            }
        }
        return false;
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
                checkForUpdate(G, version);
            })
            .catch(function () {
                // no version file (development): nothing to show
            });
    }

    function checkForUpdate(G, version) {
        var current = parseVersion(version);
        if (!current || !window.fetch) {
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
        fetch(RELEASES_API, { cache: 'no-store', signal: controller ? controller.signal : undefined, headers: { Accept: 'application/vnd.github+json' } })
            .then(function (response) {
                return response.ok ? response.json() : null;
            })
            .then(function (release) {
                clearTimeout(timeout);
                var latest = release && parseVersion(release.tag_name);
                if (latest && isNewer(latest, current)) {
                    showUpdate(G, latest.join('.'), release.html_url);
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
                        fetch('/rest/local/update', { method: 'POST', cache: 'no-store' })
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

    whenLabReady(function () {
        modules(['guiState.controller', 'robot.controller', 'user.model'], function (G, R, U) {
            setupLanguages(G);
            setupBoards(G, R);
            setupUploadDisplay(G);
            setupVersion(G);
            autoLogin(G, U);
            // the robot or the language can change elsewhere (e.g. loading a program): keep the check marks right
            setInterval(function () {
                markActive(G);
            }, 1000);
        });
    });
})();
