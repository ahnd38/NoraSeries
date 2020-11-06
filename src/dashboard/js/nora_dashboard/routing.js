'use strict';

function setupContentsRoutingService(routingTab, routingInfo) {
    noraapi.addEventHandler(
        "update_status_" + routingInfo.webSocketRoomId,
        (data) => {
            updateContentsRoutingService(routingTab, data);
        }
    );
}

function updateContentsRoutingService(routingTab, data) {
    logger.trace(
        "Update contents routing service.\n" +
        JSON.stringify(data)
    );

    const routingServiceContents =
        getContentsRoutingService(routingTab, data.webSocketRoomId, data);

    routingServiceContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.routing_type'))
        .text(data.routingServiceType);

    const $servers = routingServiceContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.servers'));
    if($servers.exists()){
        $servers.empty();

        $.each(data.routingServiceStatus, (index, status) =>{
            const $serverEntries = $('<tr />');

            $serverEntries.append([
                $('<th />',{
                    "name" : 'index',
                    "scope" : "row",
                    "html" : index + 1
                }),
                $('<td />' ,{
                    "name" : "address",
                    "html" : htmlEntities(status.serverAddress) + ":" + htmlEntities(status.serverPort)
                }),
                $('<td />' ,{
                    "name" : "status",
                    "html" : htmlEntities(status.serviceStatus)
                })
            ]);

            $servers.append($serverEntries);
        });
    }

    if(data.routingServiceType === 'ircDDB'){
        updateContentsRoutingServiceIrcDDB(routingTab, data);
    }
}

function updateContentsRoutingServiceIrcDDB(routingServiceContents, data) {
    routingServiceContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.user_records'))
        .text(data.userRecords);

    routingServiceContents
        .find('#' + selectorEscape(data.webSocketRoomId + '.repeater_records'))
        .text(data.repeaterRecords);
}

function getContentsRoutingService(routingtab, functionID, data) {
    let routingServiceContents =
        $(routingtab).find(
            "[contents-id=" + selectorEscape(functionID) + "]"
        );
    if(!routingServiceContents.exists()){
        routingServiceContents =
            routingtab.loadTemplate($("#template_routing_service"),{
                "contents_id" : functionID,
                "routing_type_id" : functionID + '.routing_type',
                "status_id" : functionID + '.status',
                "addtional_information_id" : functionID + '.addtional_information',
                "servers_id" : functionID + '.servers',
                "query_form_id" : functionID + '.query.form',
                "query_form_mode_id" : functionID + '.query.form.mode',
                "query_form_callsign_id" : functionID + '.query.form.callsign',
                "query_form_btn_id" : functionID + '.query.form.btn',
                "query_modal_id" : functionID + '.query.modal',
                "query_modal_spinner_id" : functionID + '.query.modal.spinner',
                "query_modal_result_id" : functionID + '.query.modal.result'
            });

        const $queryFormCallsignInput =
            routingServiceContents.find(
                "#" + selectorEscape(functionID + '.query.form.callsign')
            );
        $queryFormCallsignInput.keydown(function(e){
            if(e.keyCode == 8 || e.keyCode == 46){return true;}

            const inputString = String.fromCharCode(e.keyCode);
            if(!inputString.match("[a-zA-Z0-9/ ]")){return false;}
        });
        $queryFormCallsignInput.keyup(function(e){
            $(this).val($(this).val().toUpperCase());
        });

        const $queryForm =
            routingServiceContents.find(
                "#" + selectorEscape(functionID + '.query.form')
            );
        const queryFormValidator =
            $queryForm.validate({
                debug : true,
                errorElement: "span",
                errorClass: "alert",
                rules:{
                    queryCallsign : {
                        required : true,
                        isValidUserCallsign : true,
                        minlength : 4
                    }
                },
                messages:{
                    queryCallsign :{
                        required : i18next.t("routing_services.query.form.query_callsign.required")
                    }
                },
                highlight: function(element, errorClass) {
                    $(element).fadeOut(function() {
                        $(element).fadeIn()
                    })
                },
                errorPlacement: function (err, element) {
                    err.addClass('text-warning');
                    element.after(err);
                }
            });


        routingServiceContents.find(
            "#" + selectorEscape(functionID + '.query.form.btn')
        ).on('click', function(){
            if(!queryFormValidator.form()){return;}

            const queryMode =
                routingServiceContents
                    .find('#' + selectorEscape(functionID + '.query.form.mode')).val();
            let isUserQuery = false;
            let isRepeaterQuery = false;
            if(queryMode === 'user'){isUserQuery = true;}
            else if(queryMode === 'repeater'){isRepeaterQuery = true;}
            else{return;}

            const $resultArea =  $('#' + selectorEscape(functionID + '.query.modal.result'));
            $resultArea.empty();

            routingServiceContents.find(
                '#' + selectorEscape(functionID + '.query.modal.spinner')
            ).show();
            routingServiceContents
                .find('#' + selectorEscape(functionID + '.query.modal')).modal({'show':true});
            const queryCallsign =
                routingServiceContents
                .find('#' + selectorEscape(functionID + '.query.form.callsign')).val();

            noraapi.request(
                (isUserQuery ? "request_query_user_" : "request_query_repeater_") + functionID,
                (isUserQuery ? "response_query_user_" : "response_query_repeater_") + functionID,
                {
                    "queryCallsign" : queryCallsign,
                    "routingServiceType" : data.routingServiceType
                }
            ).then((data) => {
                logger.trace(
                    "Response query\n" +
                    JSON.stringify(data)
                );

                routingServiceContents.find(
                    '#' + selectorEscape(functionID + '.query.modal.spinner')
                ).hide();
                routingServiceContents.find(
                    '#' + selectorEscape(functionID + '.query.modal')
                ).modal({'show':true});
                
                if(data.result === "Success"){
                    $resultArea.loadTemplate($("#template_routing_service_query_modal_result"),{
                        "title_text" : "Query Callsign",
                        "content_text" : queryCallsign,
                        "class" : "bg-success"
                    },{'append' : true});

                    $resultArea.loadTemplate($("#template_routing_service_query_modal_result"),{
                        "title_text" : "Repeater Callsign",
                        "content_text" : data.areaRepeaterCallsign + "/" + data.zoneRepeaterCallsign +
                            (data.repeaterName ? ("(" + data.repeaterName + ")") : ""),
                        "class" : "bg-success"
                    },{'append' : true});

                    $resultArea.loadTemplate($("#template_routing_service_query_modal_result"),{
                        "title_text" : "Gateway Address",
                        "content_text" : data.gatewayIpAddress + "(" + data.gatewayHostName + ")",
                        "class" : "bg-success"
                    },{'append' : true});

                    $resultArea.loadTemplate($("#template_routing_service_query_modal_result"),{
                        "title_text" : "Record time",
                        "content_text" : data.timestamp > 0 ? moment.unix(data.timestamp).format("YYYY-MM-DD HH:mm:ss Z") : "- unavailable -",
                        "class" : "bg-success"
                    },{'append' : true});
                }
                else if(data.result === "NotFound"){
                    $resultArea.loadTemplate($("#template_routing_service_query_modal_result"),{
                        "title_text" : "Not Found",
                        "content_text" : queryCallsign + " is not found.",
                        "class" : "bg-danger"
                    },{'append' : true});
                }
                else if(data.result === "Failed"){
                    $resultArea.loadTemplate($("#template_routing_service_query_modal_result"),{
                        "title_text" : "System Error",
                        "content_text" : data.message,
                        "class" : "bg-danger"
                    },{'append' : true});
                }
            });

        });

        const $addinfo = routingServiceContents.find(
            "#" + selectorEscape(functionID + '.addtional_information')
        );

        if($addinfo.exists()){
            if(data.routingServiceType === 'ircDDB'){
                $addinfo.loadTemplate(
                    $('#template_routing_service_addtional_information'),{
                        'title' : 'User Records',
                        'content_id' : functionID + '.user_records'
                    }
                );
                $addinfo.loadTemplate(
                    $('#template_routing_service_addtional_information'),{
                        'title' : 'Repeater Records',
                        'content_id' : functionID + '.repeater_records'
                    },
                    {'append':true}
                );
            }
        }

        localizeContent(routingtab);
    }

    return routingServiceContents;
}
