'use strict';

function setupHome(data) {    
    noraapi.addEventHandler(
        'update_status.home',
        onReceiveStatusInformation
    );
}

function onReceiveStatusInformation(status) {
    logger.trace(JSON.stringify(status));

    const applicationInfo = $('#' + selectorEscape('home.application_info'));
    if(applicationInfo.exists()){
        applicationInfo.html(
            '<h5>' +
            htmlEntities(status.applicationName) + '@' +
            htmlEntities(status.applicationRunningOS) + '<br />v' +
            htmlEntities(status.applicationVersion) + '<br /><br />' +
            'on ' +
            htmlEntities(status.cpuUsageReport.operatingSystemName) + '<br />' +
            htmlEntities(status.cpuUsageReport.operatingSystemVersion) + '(' +
            htmlEntities(status.cpuUsageReport.arch) + ')' +
            '</h5>'
        );
    }

    const applicationUptime = $('#' + selectorEscape('home.application_uptime'));
    if(applicationUptime.exists()){

        const uptimeSeconds = Number(status.applicationUptime);
        const uptimeDays = Math.floor(uptimeSeconds / 86400);
        const uptimeHours = Math.floor((uptimeSeconds % 86400)  / 3600);
        const uptimeMinutes = Math.floor((uptimeSeconds % 86400 % 3600) / 60);
        applicationUptime.html(
            uptimeDays + 'd ' + uptimeHours + 'h ' + uptimeMinutes + 'm'
        );
    }

    const cpuLoadApp = $('#' + selectorEscape('home.cpu_load.app'));
    if(cpuLoadApp.exists()){
        let appLoadCurrent = 0.0;
        $.each(status.cpuUsageReport.threadUsageReport, (index, threadUsage) =>{
            appLoadCurrent += Number(threadUsage.cpuUsageCurrent);
        });
        appLoadCurrent = Math.floor(appLoadCurrent * 100);

        cpuLoadApp.text(appLoadCurrent + '%');
    }

    const cpuLoadSys = $('#' + selectorEscape('home.cpu_load.sys'));
    if(cpuLoadSys.exists()){
        const systemLoadAverage =
        Math.floor(Number(status.cpuUsageReport.systemLoadAverage) * 100);

        cpuLoadSys.text(systemLoadAverage + '%');
    }

    const currentViewers = $('#' + selectorEscape('home.viewers.current_viewers'));
    if(currentViewers.exists()){currentViewers.text(status.currentViewers);}

    const gatewayOverview = $('#' + selectorEscape('home.gateway_overview'));
    if(gatewayOverview.exists()){
        gatewayOverview.empty();

        const gatewayStatus = status.gatewayStatusReport;
        const isScopePrivate = String(gatewayStatus.scope).toLowerCase() === 'private';
        const isScopePublic = String(gatewayStatus.scope).toLowerCase() === 'public';
        const isScopUnknown = String(gatewayStatus.scope).toLowerCase() === 'unknown';
        const isUseProxy = String(gatewayStatus.useProxy).toLowerCase() === 'true';

        const gateway = $('<tr />');
        gatewayOverview.append(gateway);
        gateway.append([
            $('<td />',{
                "html" : '<b>' + htmlEntities(gatewayStatus.gatewayCallsign) + '</b>'
            }),
            $('<td />', {
                "html" :
                    (
                        isUseProxy ?
                            htmlEntities(gatewayStatus.proxyServerAddress) + ' : ' +
                            htmlEntities(gatewayStatus.proxyServerPort) : 'DISABLED'
                    ),
                "class" : (isUseProxy ? 'text-warning' : 'text-primary')
            }),
            $('<td />',{"html" : htmlEntities(gatewayStatus.lastHeardCallsign)}),
            $('<td />',{
                "html" : htmlEntities(gatewayStatus.scope),
                "class" :
                    (isScopePrivate ? 'text-success' : '') +
                    (isScopePublic ? 'text-warning' : '') +
                    (isScopUnknown ? 'text-danger' : '')
            }),
            $('<td />',{"html" : htmlEntities(gatewayStatus.name)}),
            $('<td />',{"html" : htmlEntities(gatewayStatus.location)}),
            $('<td />',{"html" : htmlEntities(gatewayStatus.description1)}),
            $('<td />',{
                "html" :
                    '<a class="text-white" href="' + gatewayStatus.url + '">' +
                    htmlEntities(gatewayStatus.url) + '</b>'
            })
        ]);
    }

    const repeaterOverview = $('#' + selectorEscape('home.repeater_overview'));
    if(repeaterOverview.exists()){
        repeaterOverview.empty();

        $.each(status.repeaterStatusReports, (index, repeaterStatus) =>{
            const isReflectorLinked =
                String(repeaterStatus.linkedReflectorCallsign).match(/[^ ]+/g);

            const isScopePrivate = String(repeaterStatus.scope).toLowerCase() === 'private';
            const isScopePublic = String(repeaterStatus.scope).toLowerCase() === 'public';
            const isScopUnknown = String(repeaterStatus.scope).toLowerCase() === 'unknown';

            const repeater = $('<tr />');
            repeaterOverview.append(repeater);
            repeater.append([
                $('<td />',{
                    "name" : 'repeaterCallsign',
                    "html" : '<b>' + htmlEntities(repeaterStatus.repeaterCallsign) + '</b>'
                }),
                $('<td />' ,{
                    "html" : htmlEntities(repeaterStatus.repeaterType)
                }),
                $('<td />' ,{
                    "html" : htmlEntities(repeaterStatus.routingService)
                }),
                $('<td />' ,{
                    "html" :
                        (isReflectorLinked ? htmlEntities(repeaterStatus.linkedReflectorCallsign) : '-'),
                    "class" : (isReflectorLinked ? 'text-danger table-success font-weight-bold' : '')
                }),
                $('<td />' ,{
                    "html" : htmlEntities(repeaterStatus.lastHeardCallsign)
                }),
                $('<td />' ,{
                    "html" : htmlEntities(repeaterStatus.scope),
                    "class" :
                        (isScopePrivate ? 'text-success' : '') +
                        (isScopePublic ? 'text-warning' : '') +
                        (isScopUnknown ? 'text-danger' : '')
                }),
                $('<td />' ,{
                    "html" : htmlEntities(repeaterStatus.name)
                }),
                $('<td />' ,{
                    "html" : htmlEntities(repeaterStatus.location)
                }),
                $('<td />' ,{
                    "html" : htmlEntities(repeaterStatus.description1)
                }),
                $('<td />' ,{
                    "html" : '<a class="text-white" href="' + repeaterStatus.url + '">' + htmlEntities(repeaterStatus.url) + '</b>'
                })
            ]);
        });

        repeaterOverview.children().sort((a, b) =>{
            if($(a).find('td[name="repeaterCallsign"]').text() > $(b).find('td[name="repeaterCallsign"]').text())
                return 1;
            else
                return -1;
        }).appendTo(repeaterOverview);
    }

    const reflectorOverview = $('#' + selectorEscape('home.reflector_overview'));
    if(reflectorOverview.exists()){
        reflectorOverview.empty();

        $(status.reflectorStatusReports).each((index, reflectorStatus) =>{
            const isIncomingEnabled =
                String(reflectorStatus.enableIncomingLink).toLowerCase() === 'true';
            const isOutgoingEnabled =
                String(reflectorStatus.enableOutgoingLink).toLowerCase() === 'true';
            const reflector = $('<tr />');
            reflectorOverview.append(reflector);
            reflector.append([
                $('<td />' ,{
                    "name" : 'reflectorType',
                    "html" : '<b>' + htmlEntities(reflectorStatus.reflectorType) + '</b>'
                }),
                $('<td />' ,{
                    "html" : htmlEntities(reflectorStatus.serviceStatus)
                }),
                $('<td />' ,{
                    "html" :
                        (isIncomingEnabled ? "ENABLED" : "DISABLED") +
                        ' : ' + htmlEntities(reflectorStatus.incomingLinkPort),
                    "class" : (isIncomingEnabled ? 'text-warning' : 'text-success')
                }),
                $('<td />' ,{
                    "html" : htmlEntities(reflectorStatus.connectedIncomingLink)
                }),
                $('<td />' ,{
                    "html" : (isOutgoingEnabled ? "ENABLED" : "DISABLED"),
                    "class" : (isOutgoingEnabled ? 'text-warning' : 'text-success')
                }),
                $('<td />' ,{
                    "html" : htmlEntities(reflectorStatus.connectedOutgoingLink)
                })
            ]);
        });

        reflectorOverview.children().sort((a, b) =>{
            if($(a).find('[name="reflectorType"]').text() > $(b).find('[name="reflectorType"]').text())
                return 1;
            else
                return -1;
        }).appendTo(reflectorOverview);
    }

    const $routingOverview = $('#' + selectorEscape('home.routing_overview'));
    if($routingOverview.exists()){
        $routingOverview.empty();

        $(status.routingStatusReports).each((index, report) => {
            $(report.serviceStatus).each((i, routingStatus) => {
                const $routing = $('<tr />');
                $routingOverview.append($routing);

                $routing.append([
                    $('<td />', {
                        "html" : '<b>' + htmlEntities(routingStatus.serviceType) + '</b>'
                    }),
                    $('<td />', {
                        "html" : htmlEntities(routingStatus.serviceStatus)
                    }),
                    $('<td />', {
                        "html" :
                            htmlEntities(routingStatus.serverAddress) + ' : ' +
                            htmlEntities(routingStatus.serverPort)
                    })
                ]);
            });
        });
    }
}

