'use strict';

class KdkAPI{
    constructor(serverAddress, context, logLvl = 'info'){

        this._logger = log.getLogger(this.constructor.name);
        this._logger.setLevel(logLvl);

        this.requestHeardLog.bind(this);

        this._serverAddress = serverAddress;
        this._context = context;

        this._socket = null;
        this._connected = false;

        this._onConnect = null;
        this._onDisconnect = null;
        this._onConnectTimeout = null;
        this._onConnectError = null;
        this._onUpdateHeardEntry = null;
        this._onUpdateHeardEntries = null;
    }

    get isConnected(){
        return this._connected;
    }

    set enableDSTARNow(enable) {
        if(this.isConnected && this._socket != null){
            this._socket.emit("enable_function",{"name":"DSTARNow"});
        }
    }

    set onUpdateHeardEntryEvent(event){
        if(
            Utils.isFunction(event) &&
            this._onUpdateHeardEntry == null &&
            this.isConnected &&
            this._socket != null
        ){
            this._onUpdateHeardEntry = event;

            this._socket.on(
                'DSTARNow.update_heard_entry',
                event
            );
        }
    }

    set onUpdateHeardEntriesEvent(event){
        if(
            Utils.isFunction(event) &&
            this._onUpdateHeardEntries == null &&
            this.isConnected &&
            this._socket != null
        ){
            this._onUpdateHeardEntries = event;

            this._socket.on(
                'DSTARNow.update_heard_entries',
                event
            );
        }
    }

    connect(
        onConnect, onDisconnect,
        onConnectTimeout, onConnectError
    ){
        this._onConnect = onConnect;
        this._onConnect
        this._onDisconnect = onDisconnect;
        this._onConnectTimeout = onConnectTimeout;
        this._onConnectError = onConnectError;

        this._socket = io(this._serverAddress,{
            path : this._context
        });
        
        this._socket.on('connect', () => {this._onConnectedEvent();});
        this._socket.on('disconnect', () => {this._onDisconectedEvent();});
        this._socket.on('connect_error', (err) => {this._onConnectError(err);});
        this._socket.on('connect_timeout', () => {this._onConnectTimeout();});
    }

    requestHeardLog() {
        if(this.isConnected && this._socket != null){
            this._socket.emit("DSTARNow.request_heardlog", {});

            this._logger.trace('sending request_heardlog request.');

            return true;
        }
        else{
            this._logger.warn('requestHeardLog() was called even though it was not connected.');

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

        if(Utils.isFunction(this.onDisconnect)){this.onDisconnect();}
    }

    _onConnectTimeoutEvent() {
        this._connected = false;

        this._logger.trace("socket connect timeouted.");

        if(Utils.isFunction(this.onConnectTimeout)){this.onConnectTimeout();}
    }

    _onConnectError(err) {
        this._connected = false;

        this._logger.trace("socket error.");

        if(Utils.isFunction(this.onConnectError)){this.onConnectError();}
    }
}