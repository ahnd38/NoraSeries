class ConfigView{
    static finalizeConfigContents() {
        if(this._configfileEditor){
            this._configfileEditor.destroy();
            this._configfileEditor = undefined;
        }
        if(this._authUserListFileEditor){
            this._authUserListFileEditor.destroy();
            this._authUserListFileEditor = undefined;
        }
    
        this._configTabSelected = undefined;
    }
    
    static updateContentsConfig() {
    
        this._updateViewConfigContents();
    
        $(document).off('click', "#config_menu a");
        $(document).on('click', "#config_menu a", function() {
            const tabid = $(this).attr("tabid").replace(/[^a-z0-9_.]/g, '');
    
            ConfigView._updateViewConfigContents(tabid);
        });
    
        if(!this._configfileEditor){
            this._configfileEditor = ace.edit(
                "config-configfile-edit-editor",
                {
                    mode:"ace/mode/xml",
                    selectionStyle: "text"
                }
            );
            this._configfileEditor.setOptions({
                enableBasicAutocompletion: true
            });
            this._configfileEditor.setTheme("ace/theme/monokai");
            this._configfileEditor.setHighlightActiveLine(true);
            this._configfileEditor.session.setTabSize(4);
            this._configfileEditor.session.setUseWrapMode(true);
            this._configfileEditor.session.setOptions({
                newLineMode:"auto"
            });
        }
    
        noraapi.request(
            'request_read_configuration_file',
            'response_read_configuration_file',
            {}
        ).then((data) =>{
            this._configfileEditor.setValue(data.config_file);
            this._configfileEditor.selection.clearSelection();
            this._configfileEditor.resize();
        }, (error) =>{
            logger.error(error);
        });
    
        $(document).off('click', "#config_configfile_edit_save_btn");
        $(document).on('click', "#config_configfile_edit_save_btn", () => {
            showModalPopup("Saving...", "Please wait...", true, false, ()=>{
                noraapi.request(
                    'request_save_configuration_file',
                    'response_save_configuration_file',
                    {"config_file":this._configfileEditor.getValue()}
                ).then((data) =>{
                    if(data.error_code === "0"){
                        showModalPopup("Configuration Save", "OK !", false, true);
                    }
                    else{
                        showModalPopup("Error !", data.error_message, false, true);
                    }
                }, (error) =>{
                    showModalPopup("Error !", error, false, true);
        
                    logger.error(error);
                });
            });
        });
    
    
        if(!this._authUserListFileEditor){
            this._authUserListFileEditor = ace.edit(
                "config-authuserlistfile-edit-editor",
                {
                    mode:"ace/mode/xml",
                    selectionStyle: "text"
                }
            );
            this._authUserListFileEditor.setOptions({
                enableBasicAutocompletion: true
            });
            this._authUserListFileEditor.setTheme("ace/theme/monokai");
            this._authUserListFileEditor.setHighlightActiveLine(true);
            this._authUserListFileEditor.session.setTabSize(4);
            this._authUserListFileEditor.session.setUseWrapMode(true);
            this._authUserListFileEditor.session.setOptions({
                newLineMode:"auto"
            });
        }
        noraapi.request(
            'request_read_auth_user_list_file',
            'response_read_auth_user_list_file',
            {}
        ).then((data) =>{
            this._authUserListFileEditor.setValue(data.config_file);
            this._authUserListFileEditor.selection.clearSelection();
            this._authUserListFileEditor.resize();
        }, (error) =>{
            logger.error(error);
        });
    
        $(document).off('click', "#config_authuserlistfile_edit_save_btn");
        $(document).on('click', "#config_authuserlistfile_edit_save_btn", () => {
            showModalPopup("Saving...", "Please wait...", true, false, ()=>{
                noraapi.request(
                    'request_save_auth_user_list_file',
                    'response_save_auth_user_list_file',
                    {"config_file":this._authUserListFileEditor.getValue()}
                ).then((data) =>{
                    if(data.error_code === "0"){
                        showModalPopup("Configuration Save", "OK !", false, true);
                    }
                    else{
                        showModalPopup("Error !", data.error_message, false, true);
                    }
                }, (error) =>{
                    showModalPopup("Error !", error, false, true);
        
                    logger.error(error);
                });
            });
        });
    
    
        $(document).off('click', "#config_sysutil_app_reboot_btn");
        $(document).on('click', "#config_sysutil_app_reboot_btn", function() {
            showModalPopup("Application Reboot", "Requesting... Please wait...", true, false, ()=>{
                noraapi.request(
                    'request_application_reboot',
                    'response_application_reboot'
                ).then((data) =>{
                    if(data.error_code === "0"){
                        showModalPopup("Application Reboot", "Okay, Rebooting...", true, false);
    
                        setTimeout(()=>{
                            noraapi.disconnect();
                            closeModalPopup();
                        }, 5000);
                    }
                    else{
                        showModalPopup("Error !", data.error_message, false, true);
                    }
                }, (error) =>{
                    showModalPopup("Error !", error, false, true);
        
                    logger.error(error);
                });
            });
        });
    
        $(document).off('click', "#config_sysutil_app_update_btn");
        $(document).on('click', "#config_sysutil_app_update_btn", function() {
            showModalPopup("Application Update", "Processing... Please wait...", true, false, ()=>{
                noraapi.request(
                    'request_application_update',
                    'response_application_update',
                    {},
                    {"timeout_millis" : 600000}
                ).then((data) =>{
                    if(data.error_code === "0"){
                        showModalPopup("Application Update", "Success! System rebooting...", true, false);

                        setTimeout(()=>{
                            noraapi.disconnect();
                            closeModalPopup();
                        }, 5000);
                    }
                    else{
                        showModalPopup("Error !", data.error_message, false, true);
                    }
                }).catch((error) =>{
                    showModalPopup("Error !", error, false, true);
        
                    logger.error(error);
                });
            });
        });
    
        $(document).off('click', "#config_sysutil_sys_reboot_btn");
        $(document).on('click', "#config_sysutil_sys_reboot_btn", function() {
            showModalPopup("System Reboot", "Processing... Please wait...", true, false, ()=>{
                noraapi.request(
                    'request_system_reboot',
                    'response_system_reboot',
                    {},
                    {"timeout_millis" : 2000}
                ).then((data) =>{
                    if(data.error_code === "0"){
                        showModalPopup("System Reboot", "Reboot now...", true, false);
    
                        setTimeout(()=>{
                            noraapi.disconnect();
                            closeModalPopup();
                        }, 5000);
                    }
                    else{
                        showModalPopup("Error !", data.error_message, false, true);
                    }
                }, (error) =>{
                    showModalPopup("Error !", error, false, true);
        
                    logger.error(error);
                });
            });
        });
    
        $(document).off('click', "#config_sysutil_sys_shutdown_btn");
        $(document).on('click', "#config_sysutil_sys_shutdown_btn", function() {
            showModalPopup("System Shutdown", "Processing... Please wait...", true, false, ()=>{
                noraapi.request(
                    'request_system_shutdown',
                    'response_system_shutdown',
                    {},
                    {"timeout_millis" : 2000}
                ).then((data) =>{
                    if(data.error_code === "0"){
                        showModalPopup("System Shutdown", "Shutdown now...", true, false);
    
                        setTimeout(()=>{
                            noraapi.disconnect();
                            closeModalPopup();
                        }, 5000);
                    }
                    else{
                        showModalPopup("Error !", data.error_message, false, true);
                    }
                }, (error) =>{
                    showModalPopup("Error !", error, false, true);
        
                    logger.error(error);
                });
            });
        });
    }

    static _updateViewConfigContents(tabid) {
        if(!tabid){tabid = "configtab_configfile_edit";}
    
        const $configMenu =  $("#config_menu");
        $configMenu.children().each((index, item) =>{
            $(item).removeClass('config-menu-item-selected');
        });
        $configMenu.find("[tabid*=" + tabid + "]").addClass('config-menu-item-selected');
    
        $("#config_content").children('div[class*="tab-pane"]').each((index, tabPanel) =>{
            if(tabPanel.id === tabid)
                $(tabPanel).addClass('active');
            else
                $(tabPanel).removeClass('active');
        });
    
        this._configTabSelected = tabid;
    }
}
