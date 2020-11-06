'use strict';

function setupContentsReflector(reflectortab, reflectorInfo) {
    const handler = (data) => {
        updateContentsReflector(reflectortab, data);
    };

    noraapi.addEventHandler(
        "update_status_" + reflectorInfo.webSocketRoomId,
        handler
    );
}

function updateContentsReflector(reflectorTab, data) {
    logger.trace(
        "Update contents reflector.\n" +
        JSON.stringify(data)
    );

    reflectorTab.empty();

    const connectionsId = data.webSocketRoomId + '.connections';
    reflectorTab.loadTemplate(
        $("#template_reflector"),
        {
            "contents_id" : data.webSocketRoomId,
            "reflector_type" : data.reflectorType,
            "connections_id" : connectionsId,
            "connections_summary_id" : connectionsId + "_summary"
        }
    );

    const $connectionsSummary = $('#' + selectorEscape(connectionsId + "_summary"));
    if($connectionsSummary.exists()){
        $connectionsSummary.empty();

        const entries = [];

        data.connections.forEach(connectionInfo => {
            entries.push(
                $('<tr />').loadTemplate(
                    $('#template_reflector_connection_summary_entry'),
                    {
                        "index" : 0,
                        "id" : connectionInfo.connectionId,
                        "direction" : connectionInfo.connectionDirection,
                        "reflector_callsign" : connectionInfo.reflectorCallsign,
                        "repeater_callsign" : connectionInfo.repeaterCallsign
                    }
                )
            );
        });

        entries.sort((a, b) =>{
            const dirA = $(a).find('[name=direction]').text();
            const dirB = $(b).find('[name=direction]').text();
            const repeaterCallsignA = $(a).find('[name=repeaterCallsign]').text();
            const repeaterCallsignB = $(b).find('[name=repeaterCallsign]').text();
            const reflectorCallsignA = $(a).find('[name=reflectorCallsign]').text();
            const reflectorCallsignB = $(b).find('[name=reflectorCallsign]').text();

            if(dirA > dirB){return 1;}
            if(dirA < dirB){return -1;}
            if(repeaterCallsignA > repeaterCallsignB){return 1;}
            if(repeaterCallsignA < repeaterCallsignB){return -1;}
            if(reflectorCallsignA > reflectorCallsignB){return 1;}
            if(reflectorCallsignA < reflectorCallsignB){return -1;}

            return 0;
        });

        $connectionsSummary.append(entries);

        $connectionsSummary.children().each((index, entry) =>{
            $(entry).find('[name="index"]').text(index + 1);
        });
    }

    const connections = $('#' + selectorEscape(connectionsId));
    if(connections.exists()){
        data.connections.forEach(connectionInfo => {
            const extraContentsId = connectionsId + '.' + connectionInfo.connectionId;

            connections.loadTemplate(
                $('#template_reflector_connection'),
                {
                    "title" :
                        connectionInfo.reflectorCallsign + ' <-> ' +
                        connectionInfo.repeaterCallsign,
                    "id" : connectionInfo.connectionId,
                    "direction" : connectionInfo.connectionDirection,
                    "reflector_callsign" : connectionInfo.reflectorCallsign,
                    "repeater_callsign" : connectionInfo.repeaterCallsign,
                    "extra_contents_id" : extraContentsId
                },
                {'append':true}
            );

            const extraContents = $('#' + selectorEscape(extraContentsId));
            if(extraContents.exists()){
                if(data.reflectorType === 'JARLLink')
                    updateReflectorExtraContentsJARLLink(extraContents, connectionInfo);
            }
        });
    }

    localizeContent(reflectorTab);
}

function updateReflectorExtraContentsJARLLink(extraContents, connectionInfo) {
    const callsigns =
        $('<div />', {
            "class":"d-flex flex-column flex-wrap",
            "style":'max-height: 200px;'
        });
    connectionInfo.loginClients.forEach((client) =>{
        callsigns.append($('<p />',{"text":client.callsign}));
    });

    extraContents.append(
        $('<h5 />',{"class" : "card-title", "text" : "Repeater Link State"}),
        $('<p />',{
            "text" : (connectionInfo.extraRepeaterLinked ? "Linked" : "Not Linked"),
            "class" :
                'font-weight-bold ' +
                (connectionInfo.extraRepeaterLinked ? "text-success" : "text-danger")
        }),
        $('<h5 />',{"class" : "card-title", "text" : "Server Software"}),
        $('<p />',{"text" : connectionInfo.serverSoftware}),
        $('<h5 />',{"class" : "card-title", "text" : "Connected Callsigns"}),
        callsigns
    );
}
