'use strict';

class NoraAPI{
    constructor(serverAddress, context, logLvl = 'info'){

        this._logger = log.getLogger(this.constructor.name);
        this._logger.setLevel(logLvl);

        this._serverAddress = serverAddress;
        this._context = context;

        this._socket = null;
        this._connected = false;

        this._onConnect = null;
        this._onDisconnect = null;
        this._onConnectTimeout = null;
        this._onConnectError = null;
        this._onNotifyLogEvent = null;

        this._passwordToken = null;
    }

    get isConnected(){
        return this._socket != null && this._connected;
    }

    set onNotifyLogEvent(event){
        if(
            Utils.isFunction(event) &&
            this._onNotifyLogEvent == null &&
            this.isConnected &&
            this._socket != null
        ){
            this._onNotifyLogEvent = event;

            this._socket.on(
                'notify_logevent',
                event
            );
        }
    }

    connect(
        onConnect, onDisconnect,
        onConnectTimeout, onConnectError
    ){
        this._onConnect = onConnect;
        this._onDisconnect = onDisconnect;
        this._onConnectTimeout = onConnectTimeout;
        this._onConnectError = onConnectError;

        this._socket = io(
            this._serverAddress,
            {
                path : this._context
            }
        );
        
        this._socket.off('connect');
        this._socket.on('connect', () => {this._onConnectedEvent();});
        this._socket.off('disconnect');
        this._socket.on('disconnect', () => {this._onDisconectedEvent();});
        this._socket.off('connect_error');
        this._socket.on('connect_error', (err) => {this._onConnectError(err);});
        this._socket.off('connect_timeout');
        this._socket.on('connect_timeout', () => {this._onConnectTimeout();});
    }

    disconnect(){
        if(this._socket){this._socket.disconnect();}
    }

    getDashboardInfo() {
        return new Promise((resolve, reject) =>{
            if(this.isConnected){
                this._socket.on('response_dashboardinfo', (data) => {
                    resolve(data);
                });

                this._socket.emit("request_dashboardinfo", {});
    
                this._logger.trace('sending request_dashboardinfo request.');
            }
            else{
                this._logger.warn('getDashboardInfo() was called even though it was not connected.');
    
                reject(new Error('socket is not connected.'));
            }
        });
    }

    getHeardLog(id) {
        return new Promise((resolve, reject) => {
            if(this.isConnected){
                const responseID = 'response_heardlog_' + id;
                this._socket.on(responseID, (data) => {
                    this.removeEventHandler(responseID);

                    resolve(data);
                });

                this._socket.emit("request_heardlog_" + id, {});
    
                this._logger.trace('sending request_heardlog_' + id + ' request.');
            }
            else{
                this._logger.warn('getHeardLog() was called even though it was not connected.');
    
                reject(new Error('socket is not connected.'));
            }
        });
    }

    getReflectorHosts(id) {
        return new Promise((resolve, reject) => {
            if(this.isConnected){
                const responseID = 'response_reflectorhosts_' + id;
                this._socket.on(responseID, (data) => {
                    this.removeEventHandler(responseID);

                    resolve(data);
                });

                this._socket.emit("request_reflectorhosts_" + id, {});
    
                this._logger.trace('sending response_reflectorhosts_' + id + ' request.');
            }
            else{
                this._logger.warn('getReflectorHosts() was called even though it was not connected.');
    
                reject(new Error('socket is not connected.'));
            }
        });
    }

    getStatus(functionId){
        return new Promise((resolve, reject) => {
            if(this.isConnected){
                const responseId = 'response_status_' + functionId;
                const requestId = 'request_status_' + functionId;

                this._socket.on(responseId, (data) => {
                    this.removeEventHandler(responseId);

                    resolve(data);
                });

                this._socket.emit(requestId, {});
    
                this._logger.trace('sending ' + requestId + ' request.');
            }
            else{
                this._logger.warn('getStatus() was called even though it was not connected.');
    
                reject(new Error('socket is not connected.'));
            }
        });
    }

    login(username, password, token){
        return this.request("request_password_token", "response_password_token", {})
        .then((data) => {
            this._passwordToken = data.password_token;

            const shaCalc = new jsSHA("SHA-256", "TEXT", {encoding: "UTF8"});
            shaCalc.update(this._passwordToken);
            shaCalc.update(password);

            return this.request("request_login", "response_login", {
                "username" : username,
                "password" : shaCalc.getHash("HEX"),
                "token" : token
            });
        });
    }

    logout(){
        return this.request("request_logout", "response_logout", {});
    }

    request(requestId, responseId, requestData, config){
        return new Promise((resolve, reject) => {
            if(!config){config = {};}

            if(this.isConnected){
                const timer = setTimeout(() => {
                    reject(new Error('request timeout.'));
                }, config.timeout_millis ? config.timeout_millis : 5000);

                this._socket.on(responseId, (data) => {
                    clearInterval(timer);

                    this.removeEventHandler(responseId);

                    resolve(data);
                });

                this._socket.emit(requestId, requestData != null ? requestData : {});
    
                this._logger.trace('sending ' + requestId + ' request.');
            }
            else{
                this._logger.warn('request() was called even though it was not connected.');
    
                reject(new Error('socket is not connected.'));
            }
        });
    }

    requestStatus(id) {
        if(!this.isConnected){return false;}

        this._socket.emit('request_status_' + id, null);

        return true;
    }

    enableDashboard(id, enable){
        if(!this.isConnected){return false;}

        this._socket.emit(
            'enable_dashboard',
            {
                roomId : id,
                enable : enable
            }
        );

        return true;
    }

    updateUplinkHeader(
        id,
        flags,
        repeater1Callsign, repeater2Callsign,
        yourCallsign, myCallsignLong, myCallsignShort
    ){
        if(!this.isConnected){return false;}

        this._socket.emit(
            "update_uplink_header_" + id,
            {
                "flags"             : flags,
                "repeater1Callsign" : repeater1Callsign,
                "repeater2Callsign" : repeater2Callsign,
                "yourCallsign"      : yourCallsign,
                "myCallsignLong"    : myCallsignLong,
                "myCallsignShort"   : myCallsignShort
            }
        );

        this._logger.trace("sending update_uplink_header_" + id + " request.");

        return true;
    }

    addEventHandler(eventName, handler){
        if(this.isConnected){
            this._socket.on(eventName, (data) => {
                handler(data);
            });

            this._logger.trace('add event handler name=' + eventName);

            return true;
        }
        else{
            this._logger.warn('addEventHandler() was called even though it was not connected.');

            return false;
        }
    }

    removeEventHandler(eventName){
        if(this.isConnected){
            this._socket.removeListener(eventName);

            this._logger.trace('remove event handler name=' + eventName);

            return true;
        }
        else{
            this._logger.warn('removeEventHandler() was called even though it was not connected.');

            return false;
        }
    }

    _onConnectedEvent() {
        this._connected = true;

        this._logger.trace("socket connected.");

        if(Utils.isFunction(this._onConnect)){this._onConnect();}
    }

    _onDisconectedEvent() {
        this._connected = false;

        this._logger.trace("socket disconnected.");

        if(Utils.isFunction(this._onDisconnect)){this._onDisconnect();}
    }

    _onConnectTimeoutEvent() {
        this._logger.trace("socket connect timeouted.");

        if(Utils.isFunction(this._onConnectTimeout)){this._onConnectTimeout();}
    }

    _onConnectError(err) {
        this._logger.trace("socket error.");

        if(Utils.isFunction(this._onConnectError)){this._onConnectError();}
    }
}
