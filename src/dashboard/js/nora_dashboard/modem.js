'use strict';

function setupContentsModem(modemtab, modemInfo, repeaterInfo, data) {
    if(modemInfo.modemType === 'AnalogModemPiGPIO'){
        setupContentsAnalogModemPiGPIO(modemtab, modemInfo, repeaterInfo, data);
    }
    else if(modemInfo.modemType === 'NoraVR') {
        setupContentsNoraVR(modemtab, modemInfo, repeaterInfo, data);
    }
}

function setupContentsNoraVR(modemtab, modemInfo, repeaterInfo, data) {
    noraapi.addEventHandler("update_status_" + modemInfo.webSocketRoomId,(data) =>{
        const maintabId = "#maintab_" + selectorEscape(data.webSocketRoomId);
        const maintab = $(maintabId);
        if(maintab.exists())
            updateContentsNoraVR(maintab, data);
        else
            logger.error("Modem tab not found = " + maintabId + ".");
    });

}

function updateContentsNoraVR(modemtab, data) {
    logger.trace(
        "Update contents NoraVR.\n" +
        JSON.stringify(data)
    );

    modemtab.empty();


}

function setupContentsAnalogModemPiGPIO(modemtab, modemInfo, repeaterInfo, data) {

    noraapi.addEventHandler("update_status_" + modemInfo.webSocketRoomId,(data) =>{
        const maintabId = "#maintab_" + selectorEscape(data.webSocketRoomId);
        const maintab = $(maintabId);
        if(maintab.exists())
            updateContentsAnalogModemPiGPIO(maintab, data);
        else
            logger.error("Modem tab not found = " + maintabId + ".");
    });

    updateContentsAnalogModemPiGPIO(modemtab,{
        "webSocketRoomId":modemInfo.webSocketRoomId,
        "gatewayCallsign":data.gatewayInfo.gatewayCallsign,
        "repeaterCallsign":repeaterInfo.repeaterCallsign,
        "uplinkActive":false,
        "downlinkActive":false,
        "uplinkHeader":{
            "flags":[0,0,0],
            "repeater1Callsign":"",
            "repeater2Callsign":"",
            "yourCallsign":"",
            "myCallsignLong":"",
            "myCallsignShort":""
        },
        "downlinkHeader":{
            "flags":[0,0,0],
            "repeater1Callsign":"",
            "repeater2Callsign":"",
            "yourCallsign":"",
            "myCallsignLong":"",
            "myCallsignShort":""
        },
        "uplinkConfigUseGateway":false,
        "uplinkConfigYourCallsign":"",
        "uplinkConfigMyCallsign":"",
    });
}

function updateContentsAnalogModemPiGPIO(modemtab, data) {
    logger.trace(
        "Update contents AnalogModemPiGPIO.\n" +
        JSON.stringify(data)
    );

    modemtab.empty();

    modemtab.append(
        $('<div/>').loadTemplate($("#template_AnalogModemPiGPIO"),{
            "contents_id":data.webSocketRoomId,
            "downlink_yourcallsign":data.downlinkActive ? data.downlinkHeader.yourCallsign : "",
            "downlink_mycallsign":data.downlinkActive ? data.downlinkHeader.myCallsignLong + "/" + data.downlinkHeader.myCallsignShort : "",
            "uplink_usegateway":undefined,
            "uplink_yourcallsign":data.uplinkActive ? data.uplinkHeader.yourCallsign : "",
            "uplink_mycallsign":data.uplinkActive ? data.uplinkHeader.myCallsignLong + "/" + data.uplinkHeader.myCallsignShort : "",
            "uplink_config_usegateway":data.uplinkConfigUseGateway,
            "uplink_config_yourcallsign":data.uplinkConfigYourCallsign.trim(),
            "uplink_config_mycallsign":data.uplinkConfigMyCallsign.trim(),
            "uplink_config_form_id":data.webSocketRoomId + ".uplink_config_form",
            "uplink_config_usegateway_id":data.webSocketRoomId + ".uplink_config_usegateway_checkbox",
            "uplink_config_yourcallsign_id":data.webSocketRoomId + ".uplink_config_youcallsign_input",
            "uplink_config_mycallsign_id":data.webSocketRoomId + ".uplink_config_mycallsign_input",
            "uplink_config_update_button_id":data.webSocketRoomId + ".uplink_config_update_button"
        })
    );

    const updateConfigUpdateButton =
        $("#" + selectorEscape(data.webSocketRoomId) + "\\.uplink_config_update_button");
    if(!updateConfigUpdateButton.exists()){
        logger.error("Could not found updateConfigUpdateButton");

        return;
    }

    updateConfigUpdateButton.on('click', function() {
        logger.trace("Update button click() " + $(this));

        const contentsID = $(this).parents('[contents-id]');
        logger.trace("ContentsID = " + contentsID);
        if(!contentsID.exists()){
            logger.error("Could not fould contents-id");

            return;
        }
        const webSocketRoomId = contentsID.attr('contents-id');
        const info = findGatewayRepeaterInfoByModemWebSocketRoomID(webSocketRoomId);
        if(!info){return;}

        const useGateway =
            $(
                "#" + selectorEscape(webSocketRoomId) +
                "\\.uplink_config_usegateway_checkbox"
            ).prop("checked");
        const yourCallsign =
            $(
                "#" + selectorEscape(webSocketRoomId) +
                "\\.uplink_config_youcallsign_input"
            ).val();
        const myCallsign =
            $(
                "#" + selectorEscape(webSocketRoomId) +
                "\\.uplink_config_mycallsign_input"
            ).val();

        noraapi.updateUplinkHeader(
            "update_uplink_header_" + webSocketRoomId,
            {
                "flags":[0,0,0],
                "repeater1Callsign":info.repeaterInfo.repeaterCallsign,
                "repeater2Callsign":useGateway?info.gatewayInfo.gatewayCallsign:info.repeaterInfo.repeaterCallsign,
                "yourCallsign":yourCallsign,
                "myCallsignLong":myCallsign,
                "myCallsignShort":"NVRA"
            }
        );
    });
}
