'use strict';

var topMessageTimer;

function showTopMessageAfterMillis(type, message, timeMillis) {
    if(topMessageTimer){clearTimeout(topMessageTimer);}

    topMessageTimer =
        setTimeout(()=>{showTopMessage(type, message);}, timeMillis);
}

function showTopMessage(type, message, showtimeMillis) {
    if(topMessageTimer){clearTimeout(topMessageTimer);}
    topMessageTimer = null;

    let alertType;
    let messageHeader;
    switch(type){
        case "success":
            alertType = "alert alert-success";
            messageHeader = "Well done!";
            break;
        case "info":
            alertType = "alert alert-info";
            messageHeader = "Okey!";
            break;
        case "warn":
            alertType = "alert alert-warning";
            messageHeader = "Warning!";
            break;
        case "danger":
        case "error":case "err":
            alertType = "alert alert-danger";
            messageHeader = "Error!";
            break;
        default:
            alertType = "alert alert-info";
            messageHeader = "";
            break;
    }

    clearTopMessage();

    $('body').prepend(
        $('<div/>',{
            "class":alertType,
            "role":"alert"
        }).html("<strong>" + messageHeader + "</strong><p>" + message + "</p>")
    );

    if(showtimeMillis){clearTopMessageAfterMillis(showtimeMillis);}
}

function clearTopMessageAfterMillis(timeMillis) {
    if(topMessageTimer){clearTimeout(topMessageTimer);}

    topMessageTimer =
        setTimeout(()=>{clearTopMessage();}, timeMillis);
}

function clearTopMessage() {
    if(topMessageTimer){clearTimeout(topMessageTimer);}
    topMessageTimer = null;

    $('body').children().remove('[class*=alert]');
}

