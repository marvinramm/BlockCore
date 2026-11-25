import * as PROGRAM from 'program.model';
import * as GUISTATE_C from 'guiState.controller';
import { setConnectionState } from 'guiState.controller';
import * as PROG_C from 'program.controller';
import { ConnectionInterface } from 'connection.interface';
import * as ROBOT_C from 'robot.controller';
import * as $ from 'jquery';
import * as UTIL from 'util.roberta';
import * as MSG from 'message';
// @ts-ignore
import * as Blockly from 'blockly';
import * as CONNECTION_C from 'connection.controller';

export abstract class AbstractConnection implements ConnectionInterface {
    public abstract isRobotConnected(): boolean;

    public abstract init(): void;

    public terminate() {
        //clean up nav icons
        $('#head-navi-icon-robot').removeClass('error');
        $('#head-navi-icon-robot').removeClass('busy');
        $('#head-navi-icon-robot').removeClass('wait');
        GUISTATE_C.setRunEnabled(false);
        $('#runSourceCodeEditor').addClass('disabled');
        $('#menuConnect').parent().addClass('disabled');
        GUISTATE_C.setPing(true);
    }

    public abstract setState(): void;

    /**
     * run logic for robot, this function gets called by runNative & runOnBirck alike
     * all robots have shared logic for run so far, override runNative and/or runOnBrick if other logic is needed
     * @param result
     * @protected
     */
    protected abstract run(result): void;

    public runNative(sourceCode: string): void {
        PROGRAM.runNative(GUISTATE_C.getProgramName(), sourceCode, GUISTATE_C.getLanguage(), (result) => {
            GUISTATE_C.setState(result);
            this.run(result);
        });
    }

    public runOnBrick(configName: string, xmlTextProgram: string, xmlConfigText: string): void {
        PROGRAM.runOnBrick(
            GUISTATE_C.getProgramName(),
            configName,
            xmlTextProgram,
            xmlConfigText,
            PROG_C.getSSID(),
            PROG_C.getPassword(),
            GUISTATE_C.getLanguage(),
            (result) => {
                GUISTATE_C.setState(result);
                this.run(result);
                PROG_C.reloadProgram(result);
            }
        );
    }

    public stopProgram(): void {
        throw new Error('stop button enabled but function is not implemented/overwritten');
    }

    public showConnectionModal(): void {
        $('#buttonCancelFirmwareUpdate').css('display', 'inline');
        $('#buttonCancelFirmwareUpdateAndRun').css('display', 'none');
        ROBOT_C.showSetTokenModal(8, 8);
    }

    reset2DefaultFirmware(): void {
        if (GUISTATE_C.hasRobotDefaultFirmware()) {
            PROGRAM.resetProgram((result) => {
                this.run(result);
            });
        } else {
            // @ts-ignore
            MSG.displayInformation(
                {
                    rc: 'error',
                },
                '',
                'should not happen!'
            );
        }
    }

    /**
     * Show robot info
     */
    showRobotInfo(): void {
        if (GUISTATE_C.getRobotName().length !== 0) {
            $('#robotName').html(GUISTATE_C.getRobotName());
        } else {
            $('#robotName').html('-');
        }
        $('#robotSystem').html(GUISTATE_C.getRobotRealName());
        if (GUISTATE_C.getRobotState() === 'wait' || $('#head-navi-icon-robot').hasClass('wait')) {
            $('#robotStateWait').css('display', 'inline');
            $('#robotStateDisconnected').css('display', 'none');
            $('#robotStateBusy').css('display', 'none');
        } else if (GUISTATE_C.getRobotState() === 'busy') {
            $('#robotStateWait').css('display', 'none');
            $('#robotStateDisconnected').css('display', 'none');
            $('#robotStateBusy').css('display', 'inline');
        } else {
            $('#robotStateWait').css('display', 'none');
            $('#robotStateDisconnected').css('display', 'inline');
            $('#robotStateBusy').css('display', 'none');
        }
        if (GUISTATE_C.getLanguage() == 'EN') {
            $('#robotBattery').text(GUISTATE_C.getRobotBattery() + ' V');
        } else {
            $('#robotBattery').text(GUISTATE_C.getRobotBattery().toString().replace('.', ',') + ' V');
        }
        let robotWait = parseInt(GUISTATE_C.getRobotTime(), 10);
        if (robotWait < 1000) {
            $('#robotWait').text(robotWait + ' ms');
        } else {
            $('#robotWait').text(Math.round(robotWait / 1000) + ' s');
        }
        $('#show-robot-info').modal('show');
    }

    /**
     * Show WLAN credentials form to save them for further REST calls.
     */
    showWlanModal(): void {
        $('#menu-wlan').modal('show');
    }

    //TODO rewok this whole function move setAgent to tdm logic only
    updateMenuStatus(numOfConnections: number): void {
        switch (numOfConnections) {
            case 0:
                $('#head-navi-icon-robot').removeClass('error');
                $('#head-navi-icon-robot').removeClass('busy');
                $('#head-navi-icon-robot').removeClass('wait');
                GUISTATE_C.setRunEnabled(false);
                $('#runSourceCodeEditor').addClass('disabled');
                $('#menuConnect').parent().addClass('disabled');
                CONNECTION_C.setIsAgent(true);
                break;
            case 1:
                CONNECTION_C.setIsAgent(true);
                $('#head-navi-icon-robot').removeClass('error');
                $('#head-navi-icon-robot').removeClass('busy');
                $('#head-navi-icon-robot').addClass('wait');
                GUISTATE_C.setRunEnabled(true);
                $('#runSourceCodeEditor').removeClass('disabled');
                $('#menuConnect').parent().addClass('disabled');
                break;
            default:
                CONNECTION_C.setIsAgent(true);
                // Always:
                $('#menuConnect').parent().removeClass('disabled');
                // If the port is not chosen:
                if (GUISTATE_C.getRobotPort() == '') {
                    $('#head-navi-icon-robot').removeClass('error');
                    $('#head-navi-icon-robot').removeClass('busy');
                    $('#head-navi-icon-robot').removeClass('wait');
                    GUISTATE_C.setRunEnabled(false);
                    //$('#menuConnect').parent().addClass('disabled');
                } else {
                    $('#head-navi-icon-robot').removeClass('error');
                    $('#head-navi-icon-robot').removeClass('busy');
                    $('#head-navi-icon-robot').addClass('wait');
                    GUISTATE_C.setRunEnabled(true);
                    $('#runSourceCodeEditor').removeClass('disabled');
                }
                break;
        }
    }
}
