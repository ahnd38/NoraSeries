'use strict';

let _repeaterSearchReflectorQueryExecuting = false;



function setupContentsRepeater(repeatertab, repeaterInfo, data) {
    const handler = (data) => {
        updateContentsRepeater(repeatertab, data);
    };

    noraapi.addEventHandler(
        "update_status_" + repeaterInfo.webSocketRoomId,
        handler
    );
}

function updateContentsRepeater(repeatertab, data) {
    logger.trace(
        "Update contents repeater.\n" +
        JSON.stringify(data)
    );

    repeatertab.empty();

    const repeaterContents =
        createContentsRepeater(repeatertab, data.webSocketRoomId);

    const isScopePrivate = String(data.scope).toLowerCase() === 'private';
    const isScopePublic = String(data.scope).toLowerCase() === 'public';
    const isScopUnknown = String(data.scope).toLowerCase() === 'unknown';

    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.repeater_callsign'))
        .text(data.repeaterCallsign);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.repeater_type'))
        .text(data.repeaterType);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.routing_service'))
        .text(data.routingService);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.linked_reflector'))
        .text(data.linkedReflectorCallsign);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.scope'))
        .text(data.scope)
        .removeClass('text-success text-warning text-danger')
        .addClass(
            (isScopePrivate ? 'text-success' : '') +
            (isScopePublic ? 'text-warning' : '') +
            (isScopUnknown ? 'text-danger' : '')
        );
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.name'))
        .text(data.name);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.position'))
        .text('[Map]')
        .addClass('text-white')
        .attr(
            'href',
            'https://www.google.com/maps?q=' + data.latitude + ',' + data.longitude
        );
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.location'))
        .text(data.location);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.frequency'))
        .text(
            (Math.round(data.frequency / 10000) / 100) + 'MHz(Offset : ' +
            (Math.round(data.frequencyOffset / 10000) / 100) + 'MHz)'
        );
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.agl'))
        .text(data.agl);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.description'))
        .text(data.description1);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.url'))
        .text(data.url).attr("href", data.url);
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.allow_direct'))
        .text(data.allowDIRECT ? 'ON' : 'OFF');
    repeaterContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.auto_disconnect_reflector_g2'))
        .text(data.autoDisconnectFromReflectorOnTxToG2Route ? 'ON' : 'OFF');

}

function createContentsRepeater(repeatertab, functionID) {
    let $repeaterContents =
        $(repeatertab).find(
            "[contents-id=" + selectorEscape(functionID) + "]"
        );
    if(!$repeaterContents.exists()){
        const repeaterContentsConfig =
        {
            "contents_id" : functionID,

            "repeater_callsign_id" : functionID + '.repeater_callsign',
            "repeater_type_id" : functionID + '.repeater_type',
            "routing_service_id" : functionID + '.routing_service',
            "linked_reflector_id" : functionID + '.linked_reflector',
            "scope_id" : functionID + '.scope',
            "name_id" : functionID + '.name',
            "position_id" : functionID + '.position',
            "location_id" : functionID + '.location',
            "frequency_id" : functionID + '.frequency',
            "agl_id" : functionID + '.agl_id',
            "description_id" : functionID + '.description',
            "url_id" : functionID + '.url',
            "allow_direct_id" : functionID + '.allow_direct',
            "auto_disconnect_reflector_g2_id" : functionID + '.auto_disconnect_reflector_g2',

            "reflector_link_control_id" : functionID + '.reflector_link_control',
            "reflector_link_control_label_for" : functionID + '.reflector_link_control_reflector_callsign',
            "reflector_link_control_reflector_callsign_id" : functionID + '.reflector_link_control_reflector_callsign',
            "reflector_link_control_reflector_callsign_aria_descrided_by" : functionID + '.reflector_link_control_reflector_callsign_help',
            "reflector_link_control_reflector_callsign_help_id" : functionID + '.reflector_link_control_reflector_callsign_help',
            "reflector_link_control_link_id" : functionID + '.reflector_link_control_link',
            "reflector_link_control_unlink_id" : functionID + '.reflector_link_control_unlink',

            "reflectorhosts_btn_show" : functionID + '.reflectorhosts_btn_show',
            "reflectorhosts_modal_id" : functionID + '.reflectorhosts_modal',
            "reflectorhosts_id" : functionID + '.reflectorhosts',
            "reflectorhosts_search_input_id" : functionID + '.reflectorhosts_search_input',
            "reflectorhosts_select_btn_id" : functionID + '.reflectorhosts_select_btn'
        };
//        if(!isEnableRepeaterReflectorLinkControlConfig)
//            repeaterContentsConfig.reflector_link_control_btn_link_enable = 'disabled';

        $repeaterContents =
            repeatertab.loadTemplate($("#template_repeater"), repeaterContentsConfig);

        const $reflectorLinkControl =
            $("#" + selectorEscape(functionID + '.reflector_link_control'));
        if(loginUserGroup === 'Users' || loginUserGroup === 'Administrators'){
            $reflectorLinkControl.show();

            $repeaterContents.find(
                '#' + selectorEscape(functionID + '.reflector_link_control_link')
            ).on('click', () =>{
                logger.trace("Reflector control(link) clicked");
    
                const reflectorCallsign =
                    $repeaterContents
                        .find(
                            '#' +
                            selectorEscape(functionID + '.reflector_link_control_reflector_callsign')
                        ).val().toUpperCase();
    
                const repeaterInfo = findRepeaterInfoByRepeaterFunctionID(functionID);
                if(repeaterInfo && reflectorCallsign){
                    noraapi.request(
                        'request_reflector_link_' + functionID,
                        'response_reflector_link_' + functionID,
                        {
                            'reflectorCallsign' : reflectorCallsign,
                            'repeaterCallsign' : repeaterInfo.repeaterCallsign
                        }
                    ).then((data) =>{
                        logger.info(
                            "Reflector control function returned " +
                            (data.success ? "success" : "failed") + "."
                        );
                    });
                }
            });
    
            $repeaterContents.find(
                '#' + selectorEscape(functionID + '.reflector_link_control_unlink')
            ).on('click', () =>{
                logger.trace("Reflector control(unlink) clicked");
    
                const repeaterInfo = findRepeaterInfoByRepeaterFunctionID(functionID);
                if(repeaterInfo){
                    noraapi.request(
                        'request_reflector_unlink_' + functionID,
                        'response_reflector_unlink_' + functionID,
                        {
                            'repeaterCallsign' : repeaterInfo.repeaterCallsign
                        }
                    ).then((data) =>{
                        logger.info(
                            "Reflector control function returned " +
                            (data.success ? "success" : "failed") + "."
                        );
                    });
                }
            });
    
            const tab = repeatertab;
    
            const $reflectorHostsBtn =
                $('#' + selectorEscape(functionID + '.reflectorhosts_btn_show'));
            $reflectorHostsBtn.on('click', function(){
                $('#' + selectorEscape(functionID + '.reflectorhosts')).empty();
                $('#' + selectorEscape(functionID + '.reflectorhosts_modal')).modal({'show':true});
            });
    
            const $reflectorHostsSearchInput =
                $("#" + selectorEscape(functionID + '.reflectorhosts_search_input'));
            $reflectorHostsSearchInput.off('keyup');
            $reflectorHostsSearchInput.on('keyup', ()=>{
                noraapi.request(
                    'request_query_reflector_' + functionID,
                    'response_query_reflector_' + functionID,
                    {"query_text": $reflectorHostsSearchInput.val()}
                ).then((data) => {
                    updateContentsRepeaterReflectorHosts(tab, JSON.parse(data.query_result), false);
                });
            });
    
            const $reflectorHostsSelectBtn =
                $("#" + selectorEscape(functionID + '.reflectorhosts_select_btn'));
            $reflectorHostsSelectBtn.off('click');
            $reflectorHostsSelectBtn.on('click', () => {
                const $reflectorHosts = $("#" + selectorEscape(functionID + '.reflectorhosts'));
                const $selectedRadio = $reflectorHosts.find('input[type="radio"]:checked');
                if(!$selectedRadio.exists()){return;}
    
                const $selectedEntry = $selectedRadio.parent().parent();
                
                $("#" + selectorEscape(functionID + '.reflector_link_control_reflector_callsign')).val(
                    $selectedEntry.find('[name="callsign"]').text().trim()
                );
            });
        }
        else{
            $reflectorLinkControl.hide();
        }

        
        localizeContent(repeatertab);
    }

    return $repeaterContents;
}

function updateContentsRepeaterReflectorHosts(repeatertab, data, notifyUpdate) {

    const functionID = repeatertab.find('[contents-id]').attr('contents-id');

    const repeaterContents =
        $(repeatertab).find(
            "[contents-id=" + selectorEscape(functionID) + "]"
        );

    const $reflectorHosts =
        repeaterContents.find('#' + selectorEscape(functionID + '.reflectorhosts'));
    if(!$reflectorHosts.exists()){
        logger.error("ReflectorHosts not found.");

        return;
    }

    if(!notifyUpdate)
        $reflectorHosts.empty();

    const newHosts = [];

    $(data).each((index, host) =>{
        let $entry =
            !notifyUpdate ?
            (
            $reflectorHosts.find("tr")
            .filter(function() {
                return $(this).find('td[name=callsign]') === host.reflectorCallsign &&
                $(this).find('td[name=dataSource]') === host.dataSource;
            }).parent()
            ) : null;

        if($entry != null && $entry.exists()){
            $entry.find('[name="protocol"]').text(host.reflectorProtocol);
            $entry.find('[name="address"]').text(
                host.reflectorAddress + ' : ' + host.reflectorPort
            );
            $entry.find('[name="priority"]').text(host.priority);
            $entry.find('[name="updateTime"]').text(
                moment.unix(host.updateTime).format("MM/DD HH:mm:ss")
            );
        }
        else {
            $entry =
                $('<tr />', {"class" : "text-white"}).append([
                    $('<th />', {
                        "name" : "state",
                        "scope" : "row"
                    }).append($('<input />',{
                        "type" : "radio",
                        "name" : functionID
                    })),
                    $('<td />', {
                        "name" : "index",
                        "html" : 0
                    }),
                    $('<td />', {
                        "name" : "callsign",
                        "html" : host.reflectorCallsign
                    }),
                    $('<td />', {
                        "name" : "name",
                        "html" : host.name
                    }),
                    $('<td />', {
                        "name" : "protocol",
                        "html" : host.reflectorProtocol
                    }),
                    $('<td />', {
                        "name" : "address",
                        "html" : host.reflectorAddress + ' : ' + host.reflectorPort
                    }),
                    $('<td />', {
                        "name" : "priority",
                        "html" : host.priority
                    }),
                    $('<td />', {
                        "name" : "updateTime",
                        "html" : moment.unix(host.updateTime).format("MM/DD HH:mm:ss")
                    }),
                    $('<td />', {
                        "name" : "dataSource",
                        "html" : host.dataSource
                    })
                ]);

            newHosts.push($entry);
        }
    });

    if(newHosts.length >= 1){
        newHosts.sort((a, b) =>{
            if($(a).find('[name="callsign"]').text() > $(b).find('[name="callsign"]').text())
                return 1;
            else
                return -1;
        });

        $reflectorHosts.append(newHosts);
    }

    $reflectorHosts.children().each((index, entry) =>{
        $(entry).find('[name="index"]').text(index + 1);
    });

    const modal = $('#' + selectorEscape(functionID + '.reflectorhosts_modal'));
    if(modal.exists() && modal.attr('aria-modal')){modal.modal({'handleUpdate' : true});}
}
