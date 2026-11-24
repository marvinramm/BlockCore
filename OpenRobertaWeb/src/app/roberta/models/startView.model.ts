import * as $ from 'jquery';

/**
 * Fetch RSS from the roberta homepage
 */
export function fetchRSS(successFn, errorFn) {
    if (location.hostname === '127.0.0.1') {
        // the local lab (BlockCore) works offline and does not contact other servers
        return;
    }
    $.ajax({
        url: 'https://www.roberta-home.de/?type=9818',
        dataType: 'xml',
        error: errorFn,
        success: successFn,
    });
}
