'use strict';

let reloadTimer;

class Utils{
    static injectStringIncludes() {
        if (!String.prototype.includes) {
            String.prototype.includes = function(search, start) {
                'use strict';
                if (typeof start !== 'number') {
                    start = 0;
                }
                
                if (start + search.length > this.length) {
                    return false;
                } else {
                    return this.indexOf(search, start) !== -1;
                }
            };
        }
    }

    static isFunction(obj, notArrow) {
        return toString.call(obj) === '[object Function]' && (!notArrow || 'prototype' in obj);
      }

    static getFileName() {
        return window.location.href.match(".+/(.+?)([\?#;].*)?$")[1];
    }

    static execReloadAfterMillis(millis, func){
        Utils.clearReload();
    
        reloadTimer = setTimeout(() =>{
            if(func){func();}

            window.location.reload();

        }, millis);
    }

    static clearReload(){
        if(reloadTimer){clearTimeout(reloadTimer);}
        reloadTimer = null;
    }
	
	static isUseLocalFileLocation(){
		return location.protocol === 'file:';
    }
    
    static saveConfig(key, value) {
        store.set(key, value);
    }

    static loadConfig(key) {
        return store.get(key);
    }

    static removeConfig(key) {
        store.remove(key);
    }

    static decodeJwt(token) {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        return JSON.parse(decodeURIComponent(escape(window.atob(base64))));
    };  
}
