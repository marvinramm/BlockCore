require.config({
    baseUrl: '.',
    paths: {
        ace: 'libs/ace/ace',
        ace_lang: 'libs/ace/ext-language_tools',
        blockly: 'blockly/blockly_compressed',
        bootstrap: 'libs/bootstrap/bootstrap.bundle.min',
        'bootstrap-table': 'libs/bootstrap/bootstrap-table-1.22.1-dist/js/bootstrap-table.min',
        'bootstrap-table-locals': 'libs/bootstrap/bootstrap-table-1.22.1-dist/js/bootstrap-table-locale-all.min',
        'bootstrap-tagsinput': 'libs/bootstrap/bootstrap-tagsinput.min',
        'bootstrap.wysiwyg': 'libs/bootstrap/bootstrap-wysiwyg.min',
        enjoyHint: 'libs/enjoyHint/enjoyhint.min',
        jquery: 'libs/jquery/jquery.min',
        'jquery-scrollto': 'libs/jquery/jquery.scrollTo-2.1.2.min',
        'jquery-validate': 'libs/jquery/jquery.validate-1.17.0.min',
        'jquery-hotkeys': 'libs/jquery/jquery.hotkeys-0.2.0',

        'startView.controller': 'js/app/roberta/controller/startView.controller',
        'confDelete.controller': 'js/app/roberta/controller/confDelete.controller',
        'configuration.controller': 'js/app/roberta/controller/configuration.controller',
        'configuration.model': 'js/app/roberta/models/configuration.model',
        'confList.controller': 'js/app/roberta/controller/confList.controller',
        'confList.model': 'js/app/roberta/models/confList.model',
        'galleryList.controller': 'js/app/roberta/controller/galleryList.controller',
        'tutorialList.controller': 'js/app/roberta/controller/tutorialList.controller',
        'guiState.controller': 'js/app/roberta/controller/guiState.controller',
        'guiState.model': 'js/app/roberta/models/guiState.model',
        'import.controller': 'js/app/roberta/controller/import.controller',
        'language.controller': 'js/app/roberta/controller/language.controller',
        'legal.controller': 'js/app/roberta/controller/legal.controller',
        'logList.controller': 'js/app/roberta/controller/logList.controller',
        'logList.model': 'js/app/roberta/models/logList.model',
        'menu.controller': 'js/app/roberta/controller/menu.controller',
        'notification.controller': 'js/app/roberta/controller/notification.controller',
        'notification.model': 'js/app/roberta/models/notification.model',
        'progCode.controller': 'js/app/roberta/controller/progCode.controller',
        'progDelete.controller': 'js/app/roberta/controller/progDelete.controller',
        'progHelp.controller': 'js/app/roberta/controller/progHelp.controller',
        'progInfo.controller': 'js/app/roberta/controller/progInfo.controller',
        'progRun.controller': 'js/app/roberta/controller/progRun.controller',
        'progList.controller': 'js/app/roberta/controller/progList.controller',
        'progList.model': 'js/app/roberta/models/progList.model',
        'program.controller': 'js/app/roberta/controller/program.controller',
        'program.model': 'js/app/roberta/models/program.model',
        'progTutorial.controller': 'js/app/roberta/controller/progTutorial.controller',
        'progShare.controller': 'js/app/roberta/controller/progShare.controller',
        'robot.controller': 'js/app/roberta/controller/robot.controller',
        'robot.model': 'js/app/roberta/models/robot.model',
        'tour.controller': 'js/app/roberta/controller/tour.controller',
        'user.controller': 'js/app/roberta/controller/user.controller',
        'userGroup.controller': 'js/app/roberta/controller/userGroup.controller',
        'userGroup.model': 'js/app/roberta/models/userGroup.model',
        'user.model': 'js/app/roberta/models/user.model',
        'rest.robot': 'js/app/roberta/rest/robot',
        'startView.model': 'js/app/roberta/models/startView.model',
        'sourceCodeEditor.controller': 'js/app/roberta/controller/sourceCodeEditor.controller',
        'connection.interface': 'js/app/roberta/controller/connections/connection.interface',
        'abstract.connections': 'js/app/roberta/controller/connections/abstract.connections',

        connections: 'js/app/roberta/controller/connections/connections',
        'connection.controller': 'js/app/roberta/controller/connection.controller',

        comm: 'js/helper/comm',
        log: 'js/helper/log',
        message: 'js/helper/msg',
        'util.roberta': 'js/helper/util',
        wrap: 'js/helper/wrap',
        table: 'js/helper/table',
        aceEditor: 'js/helper/aceEditor',

        confVisualization: 'js/app/configVisualization/confVisualization',
        'const.robots': 'js/app/configVisualization/const.robots',
        port: 'js/app/configVisualization/port',
        robotBlock: 'js/app/configVisualization/robotBlock',
        wires: 'js/app/configVisualization/wires',
    },
    shim: {
        ace_lang: {
            deps: ['ace'],
        },
        bootstrap: {
            deps: ['jquery'],
        },
        'jquery-hotkeys': {
            deps: ['jquery'],
        },
        'bootstrap-table': {
            deps: ['bootstrap'],
        },
        'bootstrap-table-locals': {
            deps: ['bootstrap-table'],
        },
        'bootstrap-tagsinput': {
            deps: ['bootstrap-table'],
        },
        'bootstrap.wysiwyg': {
            deps: ['bootstrap-table'],
        },
        blockly: {
            exports: 'Blockly',
        },
        confVisualization: {
            deps: ['blockly'],
        },
        robotBlock: {
            deps: ['blockly'],
        },
        port: {
            deps: ['blockly'],
        },
        'jquery-validate': {
            deps: ['jquery'],
        },
    },
});

/* FIX Issue #1736 : map PointerEvents to MouseEvents for Blockly drag support */
(function () {
    var forwarding = false;

    function isBlocklyTarget(target) {
        if (!target || !target.closest) {
            return false;
        }
        return !!target.closest('.blocklySvg, .blocklyFlyout, .blocklyWidgetDiv, .blocklyToolboxDiv');
    }

    function pointerToMouseAdapter(e) {
        if (forwarding) {
            return;
        }
        if (e.pointerType !== 'mouse') {
            return;
        }
        if (!isBlocklyTarget(e.target)) {
            return;
        }

        var typeMap = {
            pointerdown: 'mousedown',
            pointerup: 'mouseup',
            pointermove: 'mousemove',
            pointercancel: 'mouseup'
        };

        var mouseType = typeMap[e.type];
        if (!mouseType) {
            return;
        }

        var mouseEvent = new MouseEvent(mouseType, {
            bubbles: true,
            cancelable: true,
            view: window,
            clientX: e.clientX,
            clientY: e.clientY,
            screenX: e.screenX,
            screenY: e.screenY,
            button: e.button,
            buttons: e.buttons,
            ctrlKey: e.ctrlKey,
            shiftKey: e.shiftKey,
            altKey: e.altKey,
            metaKey: e.metaKey
        });

        forwarding = true;
        try {
            e.preventDefault();
            e.stopPropagation();
            e.target.dispatchEvent(mouseEvent);
        } finally {
            forwarding = false;
        }
    }

    document.addEventListener('pointerdown', pointerToMouseAdapter, true);
    document.addEventListener('pointerup', pointerToMouseAdapter, true);
    document.addEventListener('pointermove', pointerToMouseAdapter, true);
    document.addEventListener('pointercancel', pointerToMouseAdapter, true);
})();

require([
    'require',
    'wrap',
    'log',
    'bootstrap-table-locals',
    'blockly',
    'guiState.controller',
    'progList.controller',
    'logList.controller',
    'confList.controller',
    'confDelete.controller',
    'progShare.controller',
    'menu.controller',
    'user.controller',
    'galleryList.controller',
    'robot.controller',
    'program.controller',
    'notification.controller',
    'progCode.controller',
    'progDelete.controller',
    'progHelp.controller',
    'legal.controller',
    'progInfo.controller',
    'progRun.controller',
    'configuration.controller',
    'language.controller',
    'progTutorial.controller',
    'tutorialList.controller',
    'userGroup.controller',
    'user.model',
    'sourceCodeEditor.controller',
    'confVisualization',
    'robotBlock',
    'startView.controller',
    //start connections
    'connection.interface',
    'abstract.connections',
    'connections',
    'connection.controller',
    //end connections
    'ace',
    'ace_lang',
], function (require) {
    //window.Popper = require('popper.js').default;
    window.$ = window.jQuery = require('jquery');
    require('bootstrap');
    WRAP = require('wrap');
    LOG = require('log');
    COMM = require('comm');
    Blockly = require('blockly');
    confDeleteController = require('confDelete.controller');
    configurationController = require('configuration.controller');
    confListController = require('confList.controller');
    guiStateController = require('guiState.controller');
    languageController = require('language.controller');
    logListController = require('logList.controller');
    menuController = require('menu.controller');
    progDeleteController = require('progDelete.controller');
    progListController = require('progList.controller');
    galleryListController = require('galleryList.controller');
    tutorialListController = require('tutorialList.controller');
    legalController = require('legal.controller');
    programController = require('program.controller');
    progHelpController = require('progHelp.controller');
    progInfoController = require('progInfo.controller');
    notificationController = require('notification.controller');
    progCodeController = require('progCode.controller');
    progRunController = require('progRun.controller');
    progShareController = require('progShare.controller');
    robotController = require('robot.controller');
    userController = require('user.controller');
    userModel = require('user.model');
    tutorialController = require('progTutorial.controller');
    userGroupController = require('userGroup.controller');
    sourceCodeEditorController = require('sourceCodeEditor.controller');
    confVisualization = require('confVisualization');
    robotBlock = require('robotBlock');
    startViewController = require('startView.controller');
    connectionController = require('connection.controller');
    aceEditor = require('aceEditor');

    $(document).ready(WRAP.wrapTotal(init, 'page init'));
});

/**
 * Initializations
 */
function init() {
    COMM.setErrorFn(handleServerErrors);

    $.when(languageController.init())
        .then(function (language) {
            return guiStateController.init(language);
        })
        .then(function () {
            return userController.init();
        })
        .then(function () {
            aceEditor.init();
            galleryListController.init();
            tutorialListController.init();
            logListController.init();
            legalController.init();
            sourceCodeEditorController.init();
            tutorialController.init();
            userGroupController.init();
            notificationController.init();
            return startViewController.init(initProgramming);
        })
        .then(function () {
            menuController.init(initProgramming);
            $('#sliderDiv').hide();
            if (!guiStateController.getStartWithoutPopup()) {
                $('#tabStart').tabWrapShow();
                $('.navbar-nav > li > ul > .login').addClass('disabled');
                $('#head-navi-icon-user').addClass('error');
            }
            $('.cover').fadeOut(100);
            $('body>.pace').fadeOut(500);
        });
}

var mainCallbackCalled = false;
function initProgramming(robot, extensions, opt_callback, opt_params) {
    let callback = opt_callback;
    let params = opt_params;
    if (!mainCallbackCalled) {
        mainCallbackCalled = true;
        $.when(robotController.init(robot, extensions)).then(function () {
            $('.mainTab').parent().removeClass('invisible');
            $('.notStart').removeClass('disabled');
            $('#header').addClass('shadow');
            programController.init();
            configurationController.init();
            progHelpController.init();
            progInfoController.init();
            progCodeController.init();
            progRunController.init();
            progListController.init();
            progDeleteController.init();
            confListController.init();
            confDeleteController.init();
            progShareController.init();
            guiStateController.setInitialState();
            connectionController.initConnection(robot);
            $('#tabProgram').oneWrap('shown.bs.tab', function () {
                callback && typeof callback === 'function' && callback(...params);
            });
            $('#tabProgram').tabWrapShow();
        });
    } else {
        robotController.switchRobot(robot, extensions, null, function () {
            $('.mainTab').parent().removeClass('invisible');
            $('#header').addClass('shadow');
            $('.notStart').removeClass('disabled');
            $('#tabProgram').oneWrap('shown.bs.tab', function () {
                callback && typeof callback === 'function' && callback(...params);
            });
            $('#tabProgram').tabWrapShow();
        });
    }
}

var ALLOWED_PING_NUM = 5;

function handleServerErrors(jqXHR) {
    // TODO more?
    LOG.error('Client connection issue: ' + jqXHR.status);
    if (this.url === '/rest/ping') {
        COMM.errorNum += 1;
    }
    // show message, if REST call is no ping or EXACTLY ALLOWED_PING_NUM requests fail (to avoid multiple messages)
    if (this.url !== '/rest/ping' || COMM.errorNum == ALLOWED_PING_NUM) {
        if (jqXHR.status && jqXHR.status < 500) {
            COMM.showServerError('FRONTEND');
        } else {
            COMM.showServerError('CONNECTION');
        }
    }
}
