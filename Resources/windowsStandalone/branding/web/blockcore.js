/*
 * BlockCore: adapts the lab to a local, single-user Arduino lab. Injected into index.html by build-windows.sh (after the lab's own scripts).
 * - the board (Uno, Nano, Mega) is chosen directly in the robot menu; the start page with all robot systems is never shown
 * - only German and English; the choice is remembered
 * - a local user is created and logged in automatically, so that "my programs" and "save" work without a login dialog
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
    var LOCAL_ACCOUNT = 'blockcore';
    var LOCAL_PASSWORD = 'blockcore-local';
    var TEXT = {
        de: { board: 'Board' },
        en: { board: 'Board' }
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
            link.textContent = entry[1];
            link.parentElement.classList.add('blockcore-language');
            link.addEventListener('click', function () {
                store('language', entry[0]);
                setTimeout(function () {
                    markActive(G);
                }, 500);
            });
        });
        var wanted = store('language');
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

    whenLabReady(function () {
        modules(['guiState.controller', 'robot.controller', 'user.model'], function (G, R, U) {
            setupLanguages(G);
            setupBoards(G, R);
            autoLogin(G, U);
            // the robot or the language can change elsewhere (e.g. loading a program): keep the check marks right
            setInterval(function () {
                markActive(G);
            }, 1000);
        });
    });
})();
