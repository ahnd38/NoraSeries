'use strict';

function onReceiveNotifyLog(data) {
    let level;
    if(data.level.includes("TRACE"))
        level = "trace";
    else if(data.level.includes("DEBUG"))
        level = "debug";
    else if(data.level.includes("INFO"))
        level = "info";
    else if(data.level.includes("WARN"))
        level = "warn";
    else if(data.level.includes("ERROR"))
        level = "error";
    else
        level = "info";

    appendLogConsole(level, data);
}

function appendLogConsole(category, message) {
    const logConsole = $('#log_console');
    if(!logConsole.exists()){return;}

    while(logConsole.children().length >= 100)
        logConsole.children().last().remove();

    let logType;
    switch(category){
        case "trace":
            logType = "log-trace-msg";
            break;
        case "debug":
            logType = "log-debug-msg";
            break;
        case "info":
            logType = "log-info-msg";
            break;
        case "warn":
            logType = "log-warn-msg";
            break;
        case "error":
            logType = "log-error-msg";
            break;
        default:
            logType = "log-info-msg";
            break;
    }

    const element =
        $('<div />').addClass('d-table-row').loadTemplate($("#template_LogEntry"), {
            "levelClass":logType,
            "level":message.level ? message.level : category.toUpperCase(),
            "time":moment.unix(message.timestamp ? message.timestamp : moment().unix()).format("YYYY/MM/DD HH:mm:ss"),
            "message":message.message ? message.message.replace(/\r?\n/g, '<br>') : message
        });

    logConsole.prepend(element);
}
