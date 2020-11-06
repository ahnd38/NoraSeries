'use strict';

const dashboardVersion = '0.3.1a';

let serverAddress;
let context;
let lastHeardLimit;
let dstarnowLimit;
let logLevel;

let logger;

let noraapi;
let kdkapi;
let currentSelectedFunction = 'home';
let dashboardInfo;

let currentLanguage;

let loggedin;

let isTokenLogin;
let loginToken;
let isloginError;

let firstLogin;

let loginUserGroup;
let loginUserName;

$(document).ready(()=>{
    isloginError = false;
    loggedin = false;
    firstLogin = true;

    initializeLocalization(()=>{initialize();});
});

function onClickLoginBtn() {
    if(Utils.isUseLocalFileLocation()){
        const isa = $("#login_serveraddress_input").val();
        const isar = isa.match(
            /^([a-zA-Z0-9\.\_\-]+)(\:([0-9]+)){0,1}(\/(\S+)){0,1}/
        );
        if(!isar || isar.length < 2){return;}

        const serverAddressInput = isar[1];
        Utils.saveConfig('server_address', serverAddressInput);

        let serverPort = 0;
        if(isar[3]){
            serverPort = isar[3];
            Utils.saveConfig('server_port', serverPort);
        }
        else{
            serverPort = 3000;
            Utils.removeConfig('server_port');
        }
        if(isar[5]){
            const contextInput = context = isar[5];
            context = "/" + contextInput;
            Utils.saveConfig('context', contextInput);
        }
        else{
            context = "/nora";
            Utils.removeConfig('context');
        }

        serverAddress = 'http://' + serverAddressInput + ':' + serverPort;
    }

    const $guestLoginCheckbox = $("#login_guestlogin_checkbox");
    const isGuestLoginChecked = $guestLoginCheckbox.prop('checked');
    const $rememberMeCheckbox = $("#login_rememberme_checkbox");
    $guestLoginCheckbox.prop({'disabled':true});
    $rememberMeCheckbox.prop({'disabled':true});

    const username = isGuestLoginChecked ? 'guest' : $("#login_username_input").val();
    Utils.saveConfig('username', username);
    const password = isGuestLoginChecked ? 'guest' : $("#login_password_input").val();

    showModalPopup("Connecting...", "Please wait...", true, false, () => {
        connectToNoraAPI(serverAddress, context, username, password, loginToken);
    });
}

function initialize() {
    Utils.injectStringIncludes();
    NoraUtils.injectValidateRules();

    if(!logLevelConfig || "" === logLevelConfig)
        logLevel = 'info';
    else
        logLevel = logLevelConfig;

    log.setDefaultLevel(logLevel);
    logger = log.getLogger(Utils.getFileName());
    logger.setLevel(logLevel);

    initializeConfig();

    const $topMenuUserIcon = $("#top_menu_user_icon");
    $topMenuUserIcon.off('click');
    $topMenuUserIcon.on('click', function() {

    });

    const $topMenuLogoutIcon = $("#top_menu_logout_icon");
    $topMenuLogoutIcon.off('click');
    $topMenuLogoutIcon.on('click', function() {
        if(loggedin && noraapi){
            noraapi.logout().then(() => {
                noraapi.disconnect();

                const $topMenuUserName = $("#top_menu_user_name");
                $topMenuUserName.text("");
                const $topMenuGroupName = $("#top_menu_group_name");
                $topMenuGroupName.text("");

                finalizeContents();

                hideMainContents();
                showLoginContents();
            });
        }
    });

    showLoginContents();
}

function finalizeContents() {
    ConfigView.finalizeConfigContents();
}

function showLoginContents() {
    const $topMenu = $("#top_menu");
    $topMenu.hide();

    const $loginContents = $("#login_contents");
    $loginContents.empty();
    $loginContents.loadTemplate($("#template_LoginContents"));
    $loginContents.show();

    const serverAddressConfig = Utils.loadConfig('server_address');
    const serverPortConfig = Utils.loadConfig('server_port');
    const contextConfig = Utils.loadConfig('context');
    const rememberMeConfig = Utils.loadConfig('remember_me');
    const guestLoginConfig = Utils.loadConfig('guest_login');
    const tokenConfig = Utils.loadConfig('token');

    const loginServerAddress = $("#login_serveraddress");
    if(Utils.isUseLocalFileLocation()){
        loginServerAddress.show();

        let serverAddressInputValue = "";
        if(serverAddressConfig && serverPortConfig && contextConfig)
            serverAddressInputValue = serverAddressConfig + ':' + serverPortConfig + "/" + contextConfig;
        else if(serverAddressConfig && serverPortConfig)
            serverAddressInputValue = serverAddressConfig + ':' + serverPortConfig;
        else if(serverAddressConfig)
            serverAddressInputValue = serverAddressConfig;
        else
            serverAddressInputValue = "localhost";

        $("#login_serveraddress_input").val(serverAddressInputValue);
    }
    else{loginServerAddress.hide();}

    const usernameConfig = Utils.loadConfig('username');
    if(usernameConfig){$("#login_username_input").val(usernameConfig);}

    const $guestLoginCheckbox = $("#login_guestlogin_checkbox");
    if(guestLoginConfig){$guestLoginCheckbox.prop({'checked':guestLoginConfig});}
    const $loginUserNameInput = $("#login_username_input");
    $loginUserNameInput.prop('disabled', guestLoginConfig);
    const $loginPasswordInput = $("#login_password_input");
    $loginPasswordInput.prop('disabled', guestLoginConfig);
    
    const $rememberMeCheckbox = $("#login_rememberme_checkbox");
    if($rememberMeCheckbox){$rememberMeCheckbox.prop({'checked':rememberMeConfig});}
    
    const $loginBtn = $("#login_btn");

    $loginBtn.off('click');
    $loginBtn.on('click', function(){onClickLoginBtn();});

    $(document).off('click', '#login_guestlogin_checkbox');
    $(document).on('click', '#login_guestlogin_checkbox', function(){
        const isChecked = $guestLoginCheckbox.prop('checked');
        Utils.saveConfig('guest_login', isChecked);

        const $loginUserNameInput = $("#login_username_input");
        $loginUserNameInput.prop('disabled', isChecked);
        const $loginPasswordInput = $("#login_password_input");
        $loginPasswordInput.prop('disabled', isChecked);
    });

    $(document).off('click', '#login_rememberme_checkbox');
    $(document).on('click', '#login_rememberme_checkbox', function(){
        Utils.saveConfig('remember_me', $rememberMeCheckbox.prop('checked'));
    });

    isTokenLogin = tokenConfig && rememberMeConfig && !isloginError && firstLogin;
    if(isTokenLogin) {
        loginToken = tokenConfig;
        onClickLoginBtn();
    }
    else{
        loginToken = undefined;
    }

    localizeAll();
}

function showModalPopup(
    headerMessage, bodyMessage,
    isShowSpinner, isShowCloseButton,
    shownEventFunc
){
    const $modalPopup = $("#modal_popup");
    if(!$modalPopup.exists()){return false;}

    $modalPopup.find("#modal_popup_header").text(headerMessage);

    if(isShowSpinner)
        $modalPopup.find("#modal_popup_spinner").show();
    else
        $modalPopup.find("#modal_popup_spinner").hide();

    $modalPopup.find("#modal_popup_message").text(bodyMessage);

    if(isShowCloseButton)
        $modalPopup.find("#modal_popup_close").show();
    else
        $modalPopup.find("#modal_popup_close").hide();

    if(Utils.isFunction(shownEventFunc)){
        $modalPopup.one('shown.bs.modal', function(){
            shownEventFunc(this);
        });
    }

    $modalPopup.modal('show');
}

function closeModalPopup() {
    const $modalPopup = $("#modal_popup");
    if(!$modalPopup.exists()){return false;}

    $modalPopup.modal('hide');
}

function hideLoginContents() {
    const $loginContents = $("#login_contents");
    $loginContents.hide();
}

function showMainContents() {
    const $topMenu = $("#top_menu");
    $topMenu.show();

    const $mainContents = $("#main_contents");
    $mainContents.empty();
    $mainContents.loadTemplate($("#template_MainContents"));
    $mainContents.show();

    const navbarText = $('#main_navbar_text');
    if(navbarText.exists()){
        navbarText.html(
            'dashboard v' + dashboardVersion +
            '<br />' + serverAddress + context
        );
    }

    if(loginUserGroup === "Administrators")
        $("#main_navbar_item_config").show();
    else
        $("#main_navbar_item_config").hide();

    if(loginUserGroup === "Administrators" || loginUserGroup === "Users")
        $("#log_console_area").show();
    else
        $("#log_console_area").hide();

    localizeAll();
}

function hideMainContents() {
    const $mainContents = $("#main_contents");
    $mainContents.hide();    
}

function initializeLocalization(callbackFunc){

    const langCookie = loadConfigLanguage();

    const lng = langCookie ? langCookie : defaultLanguage;

    i18next
    .use(i18nextXHRBackend)
    .init({
        backend: {
            loadPath: './locales/{{lng}}.json?v=w36levR594VSIh3m'
        },
        debug: isDebugEnabled,
        defaultLng: defaultLanguage,
        fallbackLng: false,
        lng: lng,
    }, function (err, t) {
        if(!err){
            jqueryI18next.init(i18next, $);

            currentLanguage = lng;

            callbackFunc();
        }else{
            if(!i18next.hasResourceBundle('default', 'translation')){
                i18next.addResourceBundle('default', 'translation', JSON.parse($('#language_enUS_json').text()), true, true);
            }

            i18next.changeLanguage('default', function(err, t){
                if(!err){
                    jqueryI18next.init(i18next, $);

                    currentLanguage = lng;
        
                    callbackFunc();
                }else{onLocalizationFail(err);}
            });
        }
    });

    const languageSelector = $('#language_selector');
    if(languageSelector.exists()){
        languageSelector.empty();

        if(!Utils.isUseLocalFileLocation()){
            for(var lang of languages){
                languageSelector.append(
                    $('<a />',{
                        'class' : 'dropdown-item',
                        'text' : lang
                    })
                );
            }
        }
    }
}

function localizeAll(){
    localizeContent(null);
}

function localizeContent(element){
    const $element = $(element);
    if($element && $element.exists())
        $element.find('[data-i18n]').localize();
    else
        $('[data-i18n]').localize();
}

function initializeConfig(){
    let serverPort = 0;
    if(!serverPortConfig || serverPortConfig <= 0)
        serverPort = 3000;
    else
        serverPort = serverPortConfig;

    if(serverAddressConfig && "" !== serverAddressConfig)
        serverAddress = 'http://' + serverAddressConfig + ':' + serverPort;
    else if(Utils.isUseLocalFileLocation())
        serverAddress = 'http://localhost:' + serverPort;
    else
        serverAddress = location.host;

    if(!contextConfig || "" === contextConfig)
        context = "/nora";
    else
        context = contextConfig;

    if(!lastHeardLimitConfig)
        lastHeardLimit = 20;
    else if(lastHeardLimitConfig <= 0)
        lastHeardLimit = 1;
    else
        lastHeardLimit = lastHeardLimitConfig;

    if(!dstarnowLimitConfig)
        dstarnowLimit = 15;
    else if(dstarnowLimitConfig <= 0)
        dstarnowLimit = 1;
    else
        dstarnowLimit = dstarnowLimitConfig;
}

function connectToKdkAPI(serverAddress, context) {
    if(kdkapi){kdkapi.disconnect();}
    kdkapi = new KdkAPI(serverAddress, context, 'error');

    kdkapi.connect(
        () => {
            initializeDSTARNow(kdkapi);
        },
        () => {
        },
        () => {
            if(kdkapi){kdkapi.disconnect();}

            showTopMessage(
                "error", i18next.t("kdkapi.tm_connection_timeout"),
                5000
            );

            connectToKdkAPI(kdkAPIServerAddress, kdkAPIServerContext);
        },
        (err) => {
            if(kdkapi){kdkapi.disconnect();}

            showTopMessage(
                "error",
                i18next.t("kdkapi.tm_connection_error") + " @" + err,
                5000
            );

            connectToKdkAPI(kdkAPIServerAddress, kdkAPIServerContext);
        }
    );
}

function connectToNoraAPI(serverAddress, context, username, password, token) {
    if(noraapi){
        noraapi.disconnect();
        noraapi = undefined;
    }
    noraapi = new NoraAPI(serverAddress, context, logLevel);

    const $topMenuUserName = $("#top_menu_user_name");
    const $topMenuGroupName = $("#top_menu_group_name");

    noraapi.connect(
        () =>{  // Connect
            const $modalPopup = $("#modal_popup");
            isloginError = false;
            firstLogin = false;
            noraapi.login(username, password, token)
            .then((data) =>{
                if(data.result.toLowerCase() === "true"){
                    loggedin = true;

                    Utils.saveConfig('token', data.token);

                    const tokenPayload = Utils.decodeJwt(data.token);
                    loginUserName = tokenPayload.username;
                    $topMenuUserName.text(tokenPayload.username);
                    loginUserGroup = tokenPayload.group;
                    $topMenuGroupName.text(tokenPayload.group);

                    closeModalPopup();
                    hideLoginContents();
                    showMainContents();

                    noraapi.onNotifyLogEvent = onReceiveNotifyLog;
        
                    noraapi.getDashboardInfo().then(onReceiveDashboardInfo);
    
                    clearTopMessage();
    
                    showTopMessage(
                        "success",
                        i18next.t("noraapi.tm_connected") + " " + serverAddress + ".",
                        5000
                    );

                    appendLogConsole("info", i18next.t("noraapi.log_client_connected"));

                    connectToKdkAPI(kdkAPIServerAddress, kdkAPIServerContext);
                }
                else{
                    loggedin = false;
                    isloginError = true;

                    $topMenuUserName.text("");
                    $topMenuGroupName.text("");

                    noraapi.disconnect();

                    finalizeContents();
                    hideMainContents();
                    showLoginContents();

                    showModalPopup(
                        "Login Error",
                        data.error_message,
                        false, true
                    );
                }
            },(error) => {  // Login Error
                loggedin = false;
                isloginError = true;

                $topMenuUserName.text("");
                $topMenuGroupName.text("");

                showModalPopup("Login Error", error, false, true);

                noraapi.disconnect();
            });
        },
        () => { // Disconnected
            $topMenuUserName.text("");
            $topMenuGroupName.text("");
 
            finalizeContents();
            hideMainContents();
            showLoginContents();

            clearTopMessage();
        },
        () => { // Connect timeout
            loggedin = false;
            isloginError = true;

            noraapi.disconnect();

            finalizeContents();
            hideMainContents();
            showLoginContents();

            showModalPopup(
                "Connection Error",
                "Connection timeout from " + serverAddress,
                false, true
            );
            
            showTopMessage(
                "error",
                i18next.t("noraapi.tm_connection_timeout") + " @" + serverAddress,
                10000
            );
        },  // Connect error
        (err) => {
            loggedin = false;
            isloginError = true;

            noraapi.disconnect();

            finalizeContents();
            hideMainContents();
            showLoginContents();

            showModalPopup(
                "Connection Error",
                "Could not connect to " + serverAddress, false, true
            );

            showTopMessage(
                "error",
                i18next.t("noraapi.tm_connection_error") + " @" + serverAddress + "<br />" + err,
                10000
            );
        }
    );
}

function onReceiveDashboardInfo(info) {

    if(!checkDashboardVersion(info))
        window.alert(i18next.t("dashboard.alert_version_too_old"));

    dashboardInfo = info;

    createNavbar(info);
    createContents(info);

    onFunctionChange('home', 'home');
}

function getDashboardTitle(){
    const gatewayInfo = dashboardInfo.gatewayInfo;

    const gatewayCallsign =
        gatewayInfo.gatewayCallsign
        .substr(0, gatewayInfo.gatewayCallsign.length - 1).trim();

    return gatewayCallsign + " " + i18next.t("dashboard.navbar.title_dashboard");
}

function createNavbar(data) {

    const gatewayInfo = data.gatewayInfo;
    const repeaterInfos = data.repeaterInfos;
    const reflectorInfos = data.reflectorInfos;
    const routingInfos = data.routingInfos;

    setPageTitle(getDashboardTitle());

    const navbarText = $('#main_navbar_text');
    if(navbarText.exists()){
        navbarText.html(
            data.applicationName +
            " v" + data.applicationVersion +
            "@" + data.applicationRunningOS +
            ' / ' +
            'dashboard v' + dashboardVersion +
            '<br />' + serverAddress + context
        );
    }

    $('#main_navbar_item_gateway_link').attr('href', "#" + gatewayInfo.webSocketRoomId);

    const navbarItemReflectors = $('#main_navbar_item_reflectors_ul');
    if(navbarItemReflectors.exists()){
        navbarItemReflectors.empty();

        reflectorInfos.forEach(reflectorInfo => {
            const reflectorItem = $("<li/>",{"class":"nav-item dropdown"});
            reflectorItem.append(
                $("<a/>",{
                    "class":"dropdown-item",
                    href : "#" + reflectorInfo.webSocketRoomId,
                    text : reflectorInfo.reflectorType
                })
            );

            navbarItemReflectors.append(reflectorItem);
        });
    }

    const navbarItemRepeaters = $('#main_navbar_item_repeaters_ul');
    if(navbarItemRepeaters.exists()){
        navbarItemRepeaters.empty();

        repeaterInfos.forEach(repeaterInfo => {
            const repeaterItem = $("<li/>",{"class":"nav-item dropdown"});
            repeaterItem.append(
                $("<a/>",{
                    "class":"dropdown-item dropdown-toggle",
                    "data-toggle":"dropdown",
                    "aria-haspopup":true,
                    "aria-expanded":false,
                    href : "#" + repeaterInfo.webSocketRoomId,
                    text : repeaterInfo.repeaterCallsign
                })
            );
            
            const modemItems =
                $('<ul/>', {
                    "class":"dropdown-menu"
                });
            
            modemItems.append(
                $('<li/>').append(
                    $('<a/>', {
                            "class":"dropdown-item",
                            "data-toggle" : "tab",
                            href:"#" + repeaterInfo.webSocketRoomId,
                            text:repeaterInfo.repeaterCallsign
                        })
                )	
            );
    
            modemItems.append(
                $('<div/>', {"class":"dropdown-divider"})
            );
    
            repeaterInfo.modemInfos.forEach(modemInfo => {
                modemItems.append(
                    $('<li/>').append(
                        $('<a/>', {
                            "class":"dropdown-item",
                            href:"#" + modemInfo.webSocketRoomId,
                            text:modemInfo.modemType + "(" + modemInfo.modemId + ")"
                        })
                    )
                );
    
            });
            
            repeaterItem.append(modemItems);
            
            navbarItemRepeaters.append(repeaterItem);
        });
    }

    const navbarItemRoutingServices = $('#main_navbar_item_routingservices_ul');
    if(navbarItemRoutingServices.exists()){
        navbarItemRoutingServices.empty();

        routingInfos.forEach(routingInfo => {
            const routingItem = $("<li/>",{"class":"nav-item dropdown"});
            routingItem.append(
                $("<a/>",{
                    "class":"dropdown-item",
                    href : "#" + routingInfo.webSocketRoomId,
                    text : routingInfo.routingType
                })
            );

            navbarItemRoutingServices.append(routingItem);
        });
    }

    localizeContent($("#main_navbar"));

    $('#main_navbar_brand').text(getDashboardTitle());

    $('#main_navbar').bootnavbar();
}

function createContents(data) {
    setupHome(data);

    const maintab = $('#maintab');

    if(data.gatewayInfo.webSocketRoomId){
        const gatewayTab =
        $('<div/>',{"id":"maintab_" + data.gatewayInfo.webSocketRoomId,"class":"tab-pane"});
        maintab.append(gatewayTab);

        setupContentsGateway(maintab, data.gatewayInfo, data);
    }

    data.repeaterInfos.forEach(repeaterInfo => {
        if(repeaterInfo.webSocketRoomId){
            const repeatertab =
                $(
                    '<div/>',
                    {"id":"maintab_" + repeaterInfo.webSocketRoomId,"class":"tab-pane"}
                );
            
            maintab.append(repeatertab);

            setupContentsRepeater(repeatertab, repeaterInfo, data);
        }

        repeaterInfo.modemInfos.forEach(modemInfo =>{
            if(modemInfo.webSocketRoomId){
                const modemtab = 
                    $('<div/>',{
                        "id":"maintab_" + modemInfo.webSocketRoomId,
                        "class":"tab-pane"
                    });

                maintab.append(modemtab);

                setupContentsModem(modemtab, modemInfo, repeaterInfo, data);
            }
        });
    });

    data.reflectorInfos.forEach(reflectorInfo => {
        if(reflectorInfo.webSocketRoomId){
            const reflectorTab =
            $('<div/>',{"id":"maintab_" + reflectorInfo.webSocketRoomId,"class":"tab-pane"});

            maintab.append(reflectorTab);

            setupContentsReflector(reflectorTab, reflectorInfo);
        }
    });

    data.routingInfos.forEach(routingInfo => {
        if(routingInfo.webSocketRoomId){
            const routingTab =
            $('<div/>',{"id":"maintab_" + routingInfo.webSocketRoomId,"class":"tab-pane"});

            maintab.append(routingTab);

            setupContentsRoutingService(routingTab, routingInfo);
        }
    });

    localizeContent(maintab);
}

function findGatewayRepeaterInfoByModemWebSocketRoomID(webSocketRoomId) {
    const gatewayInfo = dashboardInfo.gatewayInfo;
    let repeaterInfo, modemInfo;
    repeaterInfo = dashboardInfo.repeaterInfos.find(repeaterInfo => {
        modemInfo = repeaterInfo.modemInfos.find(modemInfo => {
            return modemInfo.webSocketRoomId === webSocketRoomId;
        });
        return modemInfo;
    });

    if(gatewayInfo && repeaterInfo && modemInfo)
        return {'gatewayInfo':gatewayInfo,'repeaterInfo':repeaterInfo,'modemInfo':modemInfo};
    else{
        logger.error("Could not found websocketroomid = " + webSocketRoomId);

        return null;
    }
}

function findRepeaterInfoByRepeaterFunctionID(repeaterFunctionID) {
    const repeaterInfo = dashboardInfo.repeaterInfos.find(repeaterInfo => {
        return repeaterInfo.webSocketRoomId == repeaterFunctionID;
    });

    return repeaterInfo;
}

function updateView() {
    if(currentSelectedFunction === 'home')
        $('#main_navbar_item_home').addClass('active');
    else
        $('#main_navbar_item_home').removeClass('active');

    if(currentSelectedFunction === dashboardInfo.gatewayInfo.webSocketRoomId)
        $('#main_navbar_item_gateway').addClass("active");
    else
        $('#main_navbar_item_gateway').removeClass("active");

    if(dashboardInfo.repeaterInfos.find((repeaterInfo) => {
        const result =
            currentSelectedFunction === repeaterInfo.webSocketRoomId ||
            repeaterInfo.modemInfos.find((modemInfo) => {
                return currentSelectedFunction === modemInfo.webSocketRoomId
            });

        return result;
    }))
        $('#main_navbar_item_repeaters').addClass("active");
    else
        $('#main_navbar_item_repeaters').removeClass("active");	
        
    if(dashboardInfo.reflectorInfos.find((reflectorInfo) => {
        return currentSelectedFunction === reflectorInfo.webSocketRoomId;
    }))
        $('#main_navbar_item_reflectors').addClass("active");
    else
        $('#main_navbar_item_reflectors').removeClass("active");

    if(dashboardInfo.routingInfos.find((routingInfo) => {
        return currentSelectedFunction === routingInfo.webSocketRoomId;
    }))
        $('#main_navbar_item_routingservices').addClass("active");
    else
        $('#main_navbar_item_routingservices').removeClass("active");

    const $breadcrumb = $('#breadcrumb');
    $breadcrumb.empty();
    $breadcrumb.append(
        $('<li/>',{
            "class":"breadcrumb-item"
        }).append($('<a/>',{
            "class" : "text-white font-weight-bold",
            href:"#home",
            text:"HOME >"
        }))
    );
    if(currentSelectedFunction.match(dashboardInfo.gatewayInfo.webSocketRoomId)){
        $breadcrumb.append(
            $('<li/>',{
                "class":"breadcrumb-item"
            }).append($('<a/>',{
                "class" : "text-white font-weight-bold",
                href:"#" + dashboardInfo.gatewayInfo.webSocketRoomId,
                text:dashboardInfo.gatewayInfo.gatewayCallsign + " >"
            }))
        );
    }
    dashboardInfo.repeaterInfos.forEach((repeaterInfo) => {
        if(currentSelectedFunction.match(repeaterInfo.webSocketRoomId)){
            $breadcrumb.append(
                $('<li/>',{
                    "class":"breadcrumb-item"
                }).append($('<a/>',{
                    "class" : "text-white font-weight-bold",
                    href:"#" + repeaterInfo.webSocketRoomId,
                    text:repeaterInfo.repeaterCallsign + " >"
                }))
            );
        }

        const modem =
            repeaterInfo.modemInfos.find((modemInfo) =>{
                return currentSelectedFunction === modemInfo.webSocketRoomId;
            });
        if(modem) {
            $breadcrumb.append(
                $('<li/>',{
                    "class":"breadcrumb-item"
                }).append($('<a/>',{
                    "class" : "text-white font-weight-bold",
                    href:"#" + modem.webSocketRoomId,
                    text:modem.modemType + "(" + modem.modemId + ")" + " >"
                }))
            );
            return false;
        }
    });

    dashboardInfo.reflectorInfos.forEach((reflectorInfo) => {
        if(currentSelectedFunction.match(reflectorInfo.webSocketRoomId)){
            $breadcrumb.append(
                $('<li/>',{
                    "class":"breadcrumb-item"
                }).append($('<a/>',{
                    "class" : "text-white font-weight-bold",
                    href:"#" + reflectorInfo.webSocketRoomId,
                    text:reflectorInfo.reflectorType + " >"
                }))
            );
        }
    });

    dashboardInfo.routingInfos.forEach((routingInfo) => {
        if(currentSelectedFunction.match(routingInfo.webSocketRoomId)){
            $breadcrumb.append(
                $('<li/>',{
                    "class":"breadcrumb-item"
                }).append($('<a/>',{
                    "class" : "text-white font-weight-bold",
                    href:"#" + routingInfo.webSocketRoomId,
                    text:routingInfo.routingType + " >"
                }))
            );
        }
    });

    if(currentSelectedFunction.match('config')){
        $breadcrumb.append(
            $('<li/>',{
                "class":"breadcrumb-item"
            }).append($('<a/>',{
                "class" : "text-white font-weight-bold",
                href:"#config",
                text:'Config >'
            }))
        );
    }

    $('#maintab').children('div[class*="tab-pane"]').each((index, tabPanel) =>{
        if(tabPanel.id === ("maintab_" + currentSelectedFunction))
            $(tabPanel).addClass('active');
        else
            $(tabPanel).removeClass('active');
    });
}

function getContentsTab(fuctionID) {
    const tabID = "#maintab_" + selectorEscape(fuctionID);
    const tab = $(tabID);
    if(tab.exists())
        return tab;
    else
        logger.error("Tab not found = " + tabID + ".");

    return false;
}

function onFunctionChange(newFunc, prevFunc) {
    if(newFunc !== prevFunc)
        noraapi.enableDashboard(prevFunc, false);

    noraapi.enableDashboard(newFunc, true);

    logger.debug("Change room to " + newFunc + '<-' + prevFunc);

    currentSelectedFunction = newFunc;

    updateView();

    if(dashboardInfo.gatewayInfo.webSocketRoomId === newFunc){
        const funcID = dashboardInfo.gatewayInfo.webSocketRoomId;
        
        const gatewayTab =
            getContentsTab(dashboardInfo.gatewayInfo.webSocketRoomId);
        if(gatewayTab){
            noraapi.getStatus(newFunc).then((data) =>{
                updateContentsGateway(gatewayTab, data);
            });
    
            noraapi.getHeardLog(funcID).then((data) => {
                updateContentsGatewayHeardLog(gatewayTab, data);
            });
        }
    }
    else if("config" === newFunc) {
        ConfigView.updateContentsConfig();
    }
    else{
        noraapi.getStatus(newFunc).then((data) =>{
            $(dashboardInfo.repeaterInfos).filter((i, v, a) =>{
                return v.webSocketRoomId === newFunc;
            }).each((i, v) =>{
                const tab = getContentsTab(v.webSocketRoomId);
                if(tab){updateContentsRepeater(tab, data);}
            });
    
            $(dashboardInfo.reflectorInfos).filter((i, v) =>{
                return v.webSocketRoomId === newFunc;
            }).each((i, v) =>{
                const tab = getContentsTab(v.webSocketRoomId);
                if(tab){updateContentsReflector(tab, data);}
            });

            $(dashboardInfo.routingInfos).filter((i, v) =>{
                return v.webSocketRoomId === newFunc;
            }).each((i, v) =>{
                const tab = getContentsTab(v.webSocketRoomId);
                if(tab){updateContentsRoutingService(tab, data);}
            });
        });
    }

}

function checkDashboardVersion(data) {
    const requiredDashboardVersion = data.requiredDashboardVersion;

    const valid =
        requiredDashboardVersion && requiredDashboardVersion <= dashboardVersion;

    logger.trace(
        'Check dashboard version...(Required:' + requiredDashboardVersion +
        '/Current:' + dashboardVersion + ')' +
        (valid ? '[OK]' : '[TOO OLD]')
    );
    
    return valid;
}

function onLocalizationFail(err){
    log.error("Localization error = " + err);

    window.alert("Localization error = " + err);

    saveConfigLanguage(defaultLanguage);

    setTimeout(() =>{
        location.reload();
    }, 1000);
}

function loadConfigLanguage(){
    return Utils.loadConfig('language');
}

function saveConfigLanguage(language){
    Utils.saveConfig('language', language);
}

$(document).on('click', '#main_navbar a, #breadcrumb a', function(){
    const selectedFunction = $(this).attr("href").replace(/[^a-z0-9_.]/g, '');
    logger.trace("Navbar clicked = " + selectedFunction);

    if(selectedFunction){
        const roomChanged = currentSelectedFunction !== selectedFunction;
        if(roomChanged)
            onFunctionChange(selectedFunction, currentSelectedFunction);
    }
});

$(document).on('click', '#language_selector.dropdown-menu .dropdown-item', function(){
    const selectedLanguage = $(this).text();
    
    logger.trace("language selector clicked = " + selectedLanguage);

    //if(currentLanguage === selectedLanguage){return;}

    saveConfigLanguage(selectedLanguage);

    i18next.changeLanguage(selectedLanguage, function(err, t){
        if(!err){
            localizeAll();

            setPageTitle(getDashboardTitle());
            $('#main_navbar_brand').text(getDashboardTitle());
        }
        else{onLocalizationFail(err);}
    });
});
