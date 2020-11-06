'use strict';

function setupContentsGateway(gatewaytab, gatewayInfo, data) {
    noraapi.addEventHandler(
        "update_status_" + gatewayInfo.webSocketRoomId,
        (data) => {
            updateContentsGateway(gatewaytab, data);
        }
    );

    noraapi.addEventHandler(
        "notify_updateheard_" + data.gatewayInfo.webSocketRoomId,
        (data) => {
            const gatewayTabID =
                "#maintab_" + selectorEscape(dashboardInfo.gatewayInfo.webSocketRoomId);
            const gatewayTab = $(gatewayTabID);
            if(gatewayTab.exists())
                updateContentsGatewayHeardEntry(gatewayTab, data, true);
            else
                logger.error("Gateway tab not found = " + gatewayTabID + ".");
        }
    );

    noraapi.addEventHandler(
        "notify_update_reflectorhosts_" + data.gatewayInfo.webSocketRoomId,
        (data) => {
            const gatewayTabID =
                "#maintab_" + selectorEscape(dashboardInfo.gatewayInfo.webSocketRoomId);
            const gatewayTab = $(gatewayTabID);
            if(gatewayTab.exists())
                updateContentsGatewayReflectorHosts(gatewayTab, data, true);
            else
                logger.error("Gateway tab not found = " + gatewayTabID + ".");
        }
    );
}

function updateContentsGateway(gatewaytab, data) {
    logger.trace(
        "Update contents gateway.\n" +
        JSON.stringify(data)
    );

    const gatewayContents =
        getContentsGateway(gatewaytab, data.webSocketRoomId);

    const isScopePrivate = String(data.scope).toLowerCase() === 'private';
    const isScopePublic = String(data.scope).toLowerCase() === 'public';
    const isScopUnknown = String(data.scope).toLowerCase() === 'unknown';

    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.gateway_callsign'))
    .text(data.gatewayCallsign);
    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.scope'))
    .text(data.scope)
    .removeClass('text-success text-warning text-danger')
    .addClass(
        (isScopePrivate ? 'text-success' : '') +
        (isScopePublic ? 'text-warning' : '') +
        (isScopUnknown ? 'text-danger' : '')
    );
    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.name'))
    .text(data.name);
    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.position'))
    .text('[Map]')
    .addClass('text-white')
    .attr(
        'href',
        'https://www.google.com/maps?q=' + data.latitude + ',' + data.longitude
    );
    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.location'))
    .text(data.location);
    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.description'))
    .text(data.description1);
    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.url'))
    .text(data.url).attr("href", data.url);
    gatewayContents
    .find('#' + selectorEscape(data.webSocketRoomId + '.proxy_gateway'))
    .text(
        data.useProxy ?
        (data.proxyServerAddress + ':' + data.proxyServerPort) : 'DISABLED'
    );
}

function getContentsGateway(gatewaytab, functionID) {
    let gatewayContents =
        $(gatewaytab).find(
            "[contents-id=" + selectorEscape(functionID) + "]"
        );
    if(!gatewayContents.exists()){
        gatewayContents =
            gatewaytab.loadTemplate($("#template_gateway"),{
                "contents_id" : functionID,

                "gateway_callsign_id" : functionID + '.gateway_callsign',
                "scope_id" : functionID + '.scope',
                "name_id" : functionID + ".name",
                "position_id" : functionID + '.position',
                "location_id" : functionID + '.location',
                "description_id" : functionID + '.description',
                "url_id" : functionID + '.url',
                "proxy_gateway_id" : functionID + '.proxy_gateway_id',
                "contents_id" : functionID,
                "lastheard_id" : functionID + '.lastheard',

                "reflectorhosts_btn_show" : functionID + '.reflectorhosts_btn_show',
                "reflectorhosts_modal_id" : functionID + '.reflectorhosts_modal',
                "reflectorhosts_id" : functionID + '.reflectorhosts'
            });

        const tab = gatewaytab;
        $('#' + selectorEscape(functionID + '.reflectorhosts_btn_show'))
        .on('click', function(){
            $('#' + selectorEscape(functionID + '.reflectorhosts')).empty();
            
            $('#' + selectorEscape(functionID + '.reflectorhosts_modal')).modal({'show':true});

            noraapi.getReflectorHosts(functionID).then((data) => {
                updateContentsGatewayReflectorHosts(tab, data, false);
            });
        });

        localizeContent(gatewaytab);
    }

    return gatewayContents;
}

function updateContentsGatewayReflectorHosts(gatewayTab, data, notifyUpdate) {
    const functionID = dashboardInfo.gatewayInfo.webSocketRoomId;

    const gatewayContents = getContentsGateway(gatewayTab, functionID);

    const $reflectorHosts =
        gatewayContents.find('#' + selectorEscape(functionID + '.reflectorhosts'));
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
                        "name" : "index",
                        "scope" : "row",
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

function updateContentsGatewayHeardEntry(gatewayTab, data, update) {
    const functionID = dashboardInfo.gatewayInfo.webSocketRoomId;

    const gatewayContents = getContentsGateway(gatewayTab, functionID);

    const heardlog =
        gatewayContents.find('#' + selectorEscape(functionID + '.lastheard'));
    if(!heardlog.exists()){
        logger.error("HeardLog not found.");
        return;
    }

    if(data.packetDropRate != undefined)
        data.packetDropRate = Math.floor(data.packetDropRate * 100) / 100;
    else
        data.packetDropRate = 0.0;

    if(data.bitErrorRate != undefined)
        data.bitErrorRate = Math.floor(data.bitErrorRate * 100) / 100;
    else
        data.bitErrorRate = 0.0;

    let entry =
        $(heardlog).find("td[name='myLong']")
        .find("a")
        .filter(function() {
            return $(this).text() === data.myCallsignLong;
        }).parent();
    if(entry.exists()){
        entry = entry.parent();
        
        entry.find("[name='time']").text(
            moment.unix(data.heardTime).format("MM/DD HH:mm:ss")
        );
        entry.find("[name='txtime']").text(
            (data.packetCount ? data.packetCount / 50 : 0).toFixed(1) + "s"
        );
        entry.find("[name='dir']").text(data.direction);
        entry.find("[name='rpt1']").text(data.repeater1Callsign);
        entry.find("[name='rpt2']").text(data.repeater2Callsign);
        entry.find("[name='your']").text(data.yourCallsign);

        entry.find("[name='myShort']").text(data.myCallsignShort);
        entry.find("[name='from']").text(data.from);
        entry.find("[name='dest']").text(data.destination);
        entry.find("[name='message']").text(data.shortMessage);
        entry.find("[name='pos']>a")
            .text(data.locationAvailable ? '[Map]' : '')
            .attr('href', ('https://www.google.com/maps?q=' + data.latitude + ',' + data.longitude));
        entry.find("[name='proto']").text(data.protocol);

        entry.find("[name='packetdroprate']").text(
            data.packetDropRate > 0.0 ? (data.packetDropRate.toFixed(2) + "%") : "GOOD"
        );
        entry.find("[name='biterrorrate']").text(
            data.bitErrorRate > 0.0 ? (data.bitErrorRate.toFixed(2) + "%") : "GOOD"
        );

        $(entry).prependTo($(entry).parent());
    }
    else if(!update || (update && data.state === "Start")) {
        const regResult = data.myCallsignLong.match(/^([A-Z0-9]{4,7})/g);
        const myCallsignNoModule = regResult[0] ? regResult[0] : null;
        entry =
            $(
                '<tr />',
                {
                    "class" : "text-light"
                }
            ).loadTemplate($("#template_gateway_lastheard_entry"),{
                "index":0,
                "time":moment.unix(data.heardTime).format("MM/DD HH:mm:ss"),
                "txtime":(data.packetCount ? data.packetCount / 50 : 0).toFixed(1) + "s",
                "dir":data.direction,
                "rpt1":data.repeater1Callsign,
                "rpt2":data.repeater2Callsign,
                "your":data.yourCallsign,
                "myLong":data.myCallsignLong,
                "myLongLink":myCallsignNoModule ? 'https://www.qrz.com/db/' + myCallsignNoModule : "#",
                "myShort":data.myCallsignShort,
                "from":data.from,
                "dest":data.destination,
                "message":data.shortMessage,
                "pos":(data.locationAvailable ? '[Map]' : ''),
                "posLink":(data.locationAvailable ? ('https://www.google.com/maps?q=' + data.latitude + ',' + data.longitude) : ''),
                "proto":data.protocol,
                "packetdroprate":data.packetDropRate > 0.0 ? (data.packetDropRate.toFixed(2) + "%") : "GOOD",
                "biterrorrate":data.bitErrorRate > 0.0 ? (data.bitErrorRate.toFixed(2) + "%") : "GOOD"
            });
        heardlog.prepend(entry);
    }

    const $packetDropRate = entry.find("[name='packetdroprate']");
    if($packetDropRate.exists()){
        $packetDropRate.removeClass("text-warning");
        $packetDropRate.removeClass("text-danger");
        $packetDropRate.removeClass("text-light");
        $packetDropRate.removeClass("text-dark");
        $packetDropRate.removeClass("text-primary");
        $packetDropRate.removeClass("font-weight-bold");

        if(data.packetDropRate >= 5.0){
            $packetDropRate.addClass("text-danger");
            $packetDropRate.addClass("font-weight-bold");
        }
        else if(data.packetDropRate >= 1.0){
            if(data.state === "End")
                $packetDropRate.addClass("text-warning");
            else
                $packetDropRate.addClass("text-primary");

            $packetDropRate.addClass("font-weight-bold");
        }
        else{
            if(data.state === "End")
                $packetDropRate.addClass('text-light');
            else
                $packetDropRate.addClass('text-dark');

            if(data.packetDropRate > 0.0){$packetDropRate.addClass("font-weight-bold");}
        }
    }
    const $bitErrorRate = entry.find("[name='biterrorrate']");
    if($bitErrorRate.exists()){
        $bitErrorRate.removeClass("text-warning");
        $bitErrorRate.removeClass("text-danger");
        $bitErrorRate.removeClass("text-light");
        $bitErrorRate.removeClass("text-dark");
        $bitErrorRate.removeClass("text-primary");
        $bitErrorRate.removeClass("font-weight-bold");

        if(data.bitErrorRate >= 5.0){
            $bitErrorRate.addClass("text-danger");
            $bitErrorRate.addClass("font-weight-bold");
        }
        else if(data.bitErrorRate >= 1.0){
            if(data.state === "End")
                $bitErrorRate.addClass("text-warning");
            else
                $bitErrorRate.addClass("text-primary");

            $bitErrorRate.addClass("font-weight-bold");
        }
        else{
            if(data.state === "End")
                $bitErrorRate.addClass('text-light');
            else
                $bitErrorRate.addClass('text-dark');

            if(data.bitErrorRate > 0.0){$bitErrorRate.addClass("font-weight-bold");}
        }
    }
    
    const myLongCallsignLink = $(entry).find('td[name="myLong"]>a');
    const posLink = $(entry).find("[name='pos']>a");
    if(data.state === "Start" || data.state === "Update"){
        if(data.direction === "OUTGOING")
            $(entry).addClass('table-danger');
        else if(data.direction === "INCOMING")
            $(entry).addClass('table-success');

        $(entry).removeClass("text-light");
        $(entry).addClass("text-dark");
        $(entry).addClass("font-weight-bold");
        if(myLongCallsignLink.exists()){
            $(myLongCallsignLink).removeClass("text-light");
            $(myLongCallsignLink).addClass("text-danger");
        }
        if(posLink.exists()){
            posLink.removeClass('text-light');
            posLink.addClass('text-danger');
        }
    }
    else{
        $(entry).removeClass('table-danger');
        $(entry).removeClass('table-success');

        $(entry).removeClass("text-dark");
        $(entry).addClass("text-light");
        $(entry).removeClass("font-weight-bold");

        if(myLongCallsignLink.exists()){
            $(myLongCallsignLink).removeClass("text-danger");
            $(myLongCallsignLink).addClass("text-light");
        }
        if(posLink.exists()){
            posLink.addClass('text-light');
            posLink.removeClass('text-danger');
        }
    }

    const entries = $(heardlog).find("th[name='index']");
    entries.each(function(index, entry){
        if(index < lastHeardLimit)
            $(entry).text(index + 1);
        else
            $(entry).parent().remove();
    });
}

function updateContentsGatewayHeardLog(gatewayTab, data) {
    logger.trace(
        "Updating contents gateway heardlog.\n" +
        JSON.stringify(data.heardLog)
    );

    data.heardLog.forEach(function(entry, index){
        updateContentsGatewayHeardEntry(gatewayTab, entry, false)
    });
}
