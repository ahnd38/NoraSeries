'use strict';

var heardLog = [];
var isFilterJapanTrust;
var isFilterOpenquad;

var initialDelayTimerID;
const initialDelayStoreEntries = [];

function onDSTARNowReceiveTimerTimeout() {
    if(initialDelayTimerID){
        clearTimeout(initialDelayTimerID);
        initialDelayTimerID = undefined;
    }

    while(initialDelayStoreEntries.length > dstarnowLimit)
        initialDelayStoreEntries.shift();

    while(initialDelayStoreEntries.length > 0)
        updateHeardEntry(initialDelayStoreEntries.shift(), false, true);
}

function initializeDSTARNow(kdkapi) {
    kdkapi.onUpdateHeardEntryEvent = onDSTARNowReceiveUpdateHeardEntry;
    kdkapi.onUpdateHeardEntriesEvent = onDSTARNowReceiveUpdateHeardEntries;
    setTimeout(() =>{
        kdkapi.enableDSTARNow = true;
        kdkapi.requestHeardLog();
    }, 1000);
    
    isFilterJapanTrust = store.get('dstarnow_filter_japantrust');
    isFilterJapanTrust = isFilterJapanTrust != undefined ? isFilterJapanTrust.toLowerCase() === 'true' : false;
    $("#check_dstarnow_japantrust").prop('checked', !isFilterJapanTrust);
    $("#check_dstarnow_japantrust").on('click', function(){
        const isFilter = !$(this).prop('checked');

        store.set('dstarnow_filter_japantrust', String(isFilter));
        isFilterJapanTrust = isFilter;

        updateHeardEntriesViewFromLog();
    });

    isFilterOpenquad = store.get('dstarnow_filter_openquad');
    isFilterOpenquad = isFilterOpenquad != undefined ? isFilterOpenquad.toLowerCase() === 'true' : false;
    $("#check_dstarnow_openquad").prop('checked', !isFilterOpenquad);
    $("#check_dstarnow_openquad").on('click', function(){
        const isFilter = !$(this).prop('checked');

        store.set('dstarnow_filter_openquad', String(isFilter));
        isFilterOpenquad = isFilter;

        updateHeardEntriesViewFromLog();
    });

    if(!initialDelayTimerID){clearTimeout(initialDelayTimerID);}
    initialDelayTimerID = setTimeout(onDSTARNowReceiveTimerTimeout, 600000);
}

function updateHeardEntriesViewFromLog() {
    const $heardlog = $("#dstarnow_heard_entries");
    if(!$heardlog.exists()){
        logger.error("HeardLog not found.");
        return;
    }
    $heardlog.empty();

    heardLog = _.sortBy(heardLog, [function(o){return o.time;}]);

    heardLog.forEach(function(v, i, a){
        updateHeardEntry(v, true, true);
    });
}

function onDSTARNowReceiveUpdateHeardEntries(data) {
    if(initialDelayTimerID){return;}

    $(data).each((index, entry) =>{
        updateHeardEntry(entry, false, false);
    });
}

function onDSTARNowReceiveUpdateHeardEntry(data) {
    if(initialDelayTimerID){
        clearTimeout(initialDelayTimerID);
        initialDelayTimerID = setTimeout(onDSTARNowReceiveTimerTimeout, 5000);
        initialDelayStoreEntries.push(data);
    }
    else
        updateHeardEntry(data, false, true);
}

function updateHeardEntry(data, fromLog, disableAnim) {
    logger.trace(data);

    if(!fromLog){
        _.remove(heardLog, function(e){
            return e.myCallsign === data.myCallsign;
        });
        heardLog.unshift(data);
        while(heardLog.length > lastHeardLimit){heardLog.pop();}
    }

    if(
        (
            isFilterJapanTrust &&
            data.routingNetwork.toUpperCase().includes('JapanTrust'.toUpperCase())
        ) ||
        (
            isFilterOpenquad &&
            data.routingNetwork.toUpperCase().includes('openquad'.toUpperCase())
        )
    ){return;}

    const heardlog = $("#dstarnow_heard_entries");
    if(!heardlog.exists()){
        logger.error("HeardLog not found.");
        return;
    }
	
	const validPos =
		(data.latitude >= -90 && data.latitude <= +90) &&
		(data.longitude >= -180 && data.longitude <= +180);

    let entry =
        $(heardlog).find("td[name='myLong']")
        .find("a")
        .filter(function() {
            return $(this).text() === data.myCallsign;
        }).parent();
    if(entry.exists()){
        entry = entry.parent();
        
        entry.find("[name='time']").text(
            moment.unix(data.time).format("MM/DD HH:mm:ss")
        );
        entry.find("[name='repeaterName']").text(data.repeaterName);
        entry.find("[name='rpt1']").text(data.repeater1Callsign);
        entry.find("[name='rpt2']").text(data.repeater2Callsign);
        entry.find("[name='your']").text(data.yourCallsign);

        entry.find("[name='myShort']").text(data.myCallsignSuffix);
        entry.find("[name='networkDestination']").text(data.networkDestination);
        entry.find("[name='message']").text(data.message);
        entry.find("[name='routingNetwork']").text(data.routingNetwork);
        entry.find("[name='position']>a")
            .text(validPos ? '[Map]' : '')
            .attr('href', ('https://www.google.com/maps?q=' + data.latitude + ',' + data.longitude));

        if(!disableAnim){entry.fadeOut(100, function(){entry.fadeIn(500);});}
        $(entry).prependTo($(entry).parent());
    }
    else {
        const regResult = data.myCallsign.match(/^([A-Z0-9]{4,7})/g);
        const myCallsignNoModule = regResult != null && regResult.length >= 1 && regResult[0] ? regResult[0] : null;
        entry =
            $('<tr />').loadTemplate($("#template_dstarnow_heard_entry"),{
                "index":0,
                "time":moment.unix(data.time).format("MM/DD HH:mm:ss"),
                "rptName":data.repeaterName,
                "rpt1":data.repeater1Callsign,
                "rpt2":data.repeater2Callsign,
                "your":data.yourCallsign,
                "myLong":data.myCallsign,
                "myLongLink":myCallsignNoModule ? 'https://www.qrz.com/db/' + myCallsignNoModule : "#",
                "myShort":data.myCallsignSuffix,
                "networkDestination":data.networkDestination,
                "routingNetwork":data.routingNetwork,
                "message":data.message,
                "position":(validPos ? '[Map]' : ''),
                "positionLink":(validPos ? ('https://www.google.com/maps?q=' + data.latitude + ',' + data.longitude) : '')
            });
        if(!disableAnim){entry.fadeOut(100, function(){entry.fadeIn(500);});}
        heardlog.prepend(entry);
    }

    const myLongCallsignLink = $(entry).find('td[name="myLong"]>a');
    const positionLink = $(entry).find("[name='position']>a");
    if(data.transmitting){
        $(entry).addClass('table-success');
        $(entry).addClass('text-dark');
        $(entry).addClass('font-weight-bold');
        if(myLongCallsignLink.exists()){
            myLongCallsignLink.removeClass('text-light');
            myLongCallsignLink.addClass('text-danger');
        }
        if(positionLink.exists()){
            positionLink.removeClass('text-light');
            positionLink.addClass('text-danger');
        }
    }
    else{
        $(entry).removeClass('table-success');
        $(entry).removeClass('text-dark');
        $(entry).removeClass('font-weight-bold');
        if(myLongCallsignLink.exists()){
            myLongCallsignLink.addClass('text-light');
            myLongCallsignLink.removeClass('text-danger');
        }
        if(positionLink.exists()){
            positionLink.addClass('text-light');
            positionLink.removeClass('text-danger');
        }
    }

    const indexEntries = $(heardlog).find("th[name='index']");
    indexEntries.each(function(index, entry){
        if(index < dstarnowLimit)
            $(entry).text(index + 1);
        else
            $(entry).parent().remove();
    });
}
