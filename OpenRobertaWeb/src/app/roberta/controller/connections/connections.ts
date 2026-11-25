import { AbstractConnection } from 'abstract.connections';
import * as $ from 'jquery';
import * as GUISTATE_C from 'guiState.controller';
import * as PROGRAM from 'program.model';
import * as MSG from 'message';
import * as GUISTATE from 'guiState.model';
import * as ROBOT_C from 'robot.controller';
// @ts-ignore
import * as Blockly from 'blockly';
import * as LOG from 'log';

/*
 * Connections of BlockCore: the Arduino boards are connected to the COM ports of this computer (LocalSerialOrTokenConnection). The
 * connection class of a board is found by its name: "uno" -> UnoConnection (see connection.controller).
 */

class TokenConnection extends AbstractConnection {
    public override init(): void {
        $('#head-navi-icon-robot').removeClass('error');
        $('#head-navi-icon-robot').removeClass('busy');
        $('#head-navi-icon-robot').removeClass('wait');
        GUISTATE_C.setRunEnabled(false);
        $('#runSourceCodeEditor').addClass('disabled');
        $('#menuConnect').parent().removeClass('disabled');
        GUISTATE_C.setPingTime(GUISTATE_C.SHORT);
        GUISTATE_C.setPing(true);
    }

    public setState(): void {
        $('#menuConnect').parent().removeClass('disabled');
        if (GUISTATE.robot.state === 'wait') {
            $('#head-navi-icon-robot').removeClass('error');
            $('#head-navi-icon-robot').removeClass('busy');
            $('#head-navi-icon-robot').addClass('wait');
            GUISTATE_C.setRunEnabled(true);
            $('#runSourceCodeEditor').removeClass('disabled');
        } else if (GUISTATE.robot.state === 'busy') {
            $('#head-navi-icon-robot').removeClass('wait');
            $('#head-navi-icon-robot').removeClass('error');
            $('#head-navi-icon-robot').addClass('busy');
            GUISTATE_C.setRunEnabled(false);
            $('#runSourceCodeEditor').addClass('disabled');
        } else {
            $('#head-navi-icon-robot').removeClass('busy');
            $('#head-navi-icon-robot').removeClass('wait');
            $('#head-navi-icon-robot').addClass('error');
            GUISTATE_C.setRunEnabled(false);
            $('#runSourceCodeEditor').addClass('disabled');
        }
    }

    public override isRobotConnected(): boolean {
        return GUISTATE.robot.time > 0;
    }

    protected override run(result) {
        GUISTATE_C.setState(result);
        MSG.displayInformation(result, result.message, result.message, result.programName || GUISTATE_C.getProgramName(), GUISTATE_C.getRobot());
        if (result.rc == 'ok') {
            if (Blockly.Msg['MENU_ROBOT_STOP_HINT_' + GUISTATE_C.getRobotGroup().toUpperCase()]) {
                // @ts-ignore
                MSG.displayMessage('MENU_ROBOT_STOP_HINT_' + GUISTATE_C.getRobotGroup().toUpperCase(), 'TOAST');
            }
        } else {
            GUISTATE_C.setConnectionState('error');
        }
    }

    public override showConnectionModal() {
        $('#buttonCancelFirmwareUpdate').css('display', 'inline');
        $('#buttonCancelFirmwareUpdateAndRun').css('display', 'none');
        ROBOT_C.showSetTokenModal(8, 8);
    }

    public override stopProgram() {
        PROGRAM.stopProgram(function () {
            //TODO handle server result
        });
    }
}

/**
 * Connection for the local (standalone) lab: the lab server runs on the computer the robot is plugged into (e.g. the Windows exe).
 * The server lists the serial ports of this computer and uploads the compiled program with avrdude. The user only has to select the COM port.
 * If the server does not offer the local mode (a normal, public lab), this connection behaves like a TokenConnection.
 */
class LocalSerialOrTokenConnection extends TokenConnection {
    static readonly PORTS_URL = '/rest/local/ports';
    static readonly UPLOAD_URL = '/rest/local/upload';
    private local = false;
    private terminated = false;
    private timerId: number;
    private ports: { name: string; description: string }[] = [];
    // the port this connection selected by itself (not chosen by the user)
    private autoPort = '';

    /** the port of the last successful upload, remembered across restarts (e.g. COM5, while COM1 and COM3 exist, too) */
    private static lastUploadPort(port?: string): string {
        try {
            if (port !== undefined) {
                localStorage.setItem('openroberta.localSerial.lastUploadPort', port);
                return port;
            }
            return localStorage.getItem('openroberta.localSerial.lastUploadPort') || '';
        } catch (e) {
            return '';
        }
    }

    public override init(): void {
        super.init();
        this.terminated = false;
        $.ajax({ url: LocalSerialOrTokenConnection.PORTS_URL, type: 'GET', dataType: 'json' })
            .done((response) => {
                if (this.terminated) {
                    return;
                }
                this.local = true;
                this.addSerialMonitorMenu();
                this.updatePorts(response);
                this.timerId = window.setInterval(() => this.refreshPorts(), 3000);
            })
            .fail(() => {
                this.local = false; // no local mode: keep the token connection
            });
    }

    public override terminate(): void {
        this.terminated = true;
        if (this.timerId) {
            window.clearInterval(this.timerId);
            this.timerId = undefined;
        }
        this.local = false;
        $('#robotSerialMonitor').remove();
        super.terminate();
    }

    /**
     * adds "Serial Monitor" to the robot menu. It opens serial.html in its own window, with the selected port.
     */
    private addSerialMonitorMenu(): void {
        if ($('#robotSerialMonitor').length > 0) {
            return;
        }
        const item = $('<li id="robotSerialMonitor"><a class="dropdown-item" href="#" id="menuSerialMonitor"><span class="typcn typcn-messages"></span></a></li>');
        item.find('span').text(' ' + (Blockly.Msg['LOCAL_SERIAL_MONITOR'] || 'Serial Monitor'));
        item.find('a').on('click', (event) => {
            event.preventDefault();
            const query = '?port=' + encodeURIComponent(GUISTATE_C.getRobotPort() || '') + '&lang=' + String(GUISTATE_C.getLanguage() || 'en').toLowerCase();
            const monitor = window.open('/serial.html' + query, 'serialMonitor', 'popup,width=820,height=580');
            if (monitor) {
                monitor.focus();
            }
        });
        $('#robotConnect').after(item);
    }

    private refreshPorts(): void {
        $.ajax({ url: LocalSerialOrTokenConnection.PORTS_URL, type: 'GET', dataType: 'json' }).done((response) => this.updatePorts(response));
    }

    /**
     * keeps the selected port while it exists. Otherwise (or if the port was selected automatically) the port of the last upload is
     * selected as soon as it is there again, e.g. after the robot was unplugged; if there is only one port, that one.
     */
    private updatePorts(response): void {
        this.ports = (response && response.ports) || [];
        const names = this.ports.map((port) => port.name);
        const current = GUISTATE_C.getRobotPort();
        const last = LocalSerialOrTokenConnection.lastUploadPort();
        if (names.indexOf(current) < 0) {
            GUISTATE_C.setRobotPort('');
        }
        const chosenByUser = GUISTATE_C.getRobotPort() !== '' && GUISTATE_C.getRobotPort() !== this.autoPort;
        if (!chosenByUser) {
            let wanted = '';
            if (last !== '' && names.indexOf(last) >= 0) {
                wanted = last;
            } else if (names.length === 1) {
                wanted = names[0];
            }
            if (wanted !== '' && wanted !== GUISTATE_C.getRobotPort()) {
                this.autoPort = wanted;
                ROBOT_C.setPort(wanted);
            }
        }
        this.setState();
    }

    public override setState(): void {
        if (!this.local) {
            super.setState();
            return;
        }
        $('#menuConnect').parent().removeClass('disabled');
        $('#head-navi-icon-robot').removeClass('busy');
        if (GUISTATE_C.getRobotPort() !== '') {
            $('#head-navi-icon-robot').removeClass('error');
            $('#head-navi-icon-robot').addClass('wait');
            GUISTATE_C.setRunEnabled(true);
            $('#runSourceCodeEditor').removeClass('disabled');
        } else {
            $('#head-navi-icon-robot').removeClass('wait');
            $('#head-navi-icon-robot').addClass('error');
            GUISTATE_C.setRunEnabled(false);
            $('#runSourceCodeEditor').addClass('disabled');
        }
    }

    public override isRobotConnected(): boolean {
        if (!this.local) {
            return super.isRobotConnected();
        }
        return GUISTATE_C.getRobotPort() !== '';
    }

    public override showConnectionModal(): void {
        if (!this.local) {
            super.showConnectionModal();
            return;
        }
        if (this.ports.length === 0) {
            // @ts-ignore
            MSG.displayMessage('LOCAL_SERIAL_NO_PORT', 'POPUP', '');
            return;
        }
        $('#singleModalListInput').empty();
        this.ports.forEach((port) => {
            // port first, it may be cut off in the narrow dialog: "COM3 – USB-SERIAL CH340"
            const description = (port.description || '').replace(' (' + port.name + ')', '').trim();
            const label = description && description !== port.name ? port.name + ' – ' + description : port.name;
            const option = $('<option></option>').attr('value', port.name).text(label);
            if (port.name === GUISTATE_C.getRobotPort()) {
                option.attr('selected', 'selected');
            }
            $('#singleModalListInput').append(option);
        });
        ROBOT_C.showListModal();
    }

    public override stopProgram(): void {
        if (!this.local) {
            super.stopProgram();
        }
    }

    protected override run(result): void {
        if (!this.local) {
            super.run(result);
            return;
        }
        GUISTATE_C.setState(result);
        if (result.rc !== 'ok' || !result.compiledCode) {
            MSG.displayInformation(result, result.message, result.message, result.programName || GUISTATE_C.getProgramName(), GUISTATE_C.getRobot());
            return;
        }
        const port = GUISTATE_C.getRobotPort();
        if (port === '') {
            this.showConnectionModal();
            return;
        }
        $('#head-navi-icon-robot').removeClass('wait').addClass('busy');
        GUISTATE_C.setRunEnabled(false);
        // progress and success are shown by the BlockCore upload display (blockcore.js), errors as a popup
        $.ajax({
            url: LocalSerialOrTokenConnection.UPLOAD_URL,
            type: 'POST',
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            data: JSON.stringify({ port: port, robot: GUISTATE_C.getRobot(), hex: result.compiledCode }),
        })
            .done((answer) => {
                if (answer.rc === 'ok') {
                    LocalSerialOrTokenConnection.lastUploadPort(port);
                } else {
                    LOG.error(answer.message);
                    // @ts-ignore
                    MSG.displayMessage('LOCAL_SERIAL_UPLOAD_ERROR', 'POPUP', port);
                }
            })
            .fail((xhr) => {
                LOG.error(xhr.responseText);
                // @ts-ignore
                MSG.displayMessage('LOCAL_SERIAL_UPLOAD_ERROR', 'POPUP', port);
            })
            .always(() => this.setState());
    }
}

export class UnoConnection extends LocalSerialOrTokenConnection {}

export class NanoConnection extends LocalSerialOrTokenConnection {}

export class MegaConnection extends LocalSerialOrTokenConnection {}
