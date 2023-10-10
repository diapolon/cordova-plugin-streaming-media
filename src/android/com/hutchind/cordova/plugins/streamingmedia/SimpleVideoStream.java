/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

const ENVIRONMENT_PRODUCTION = 'production'
const ENVIRONMENT_DEVELOPMENT = 'development'

var app = {
    environment: ENVIRONMENT_PRODUCTION,
    config: null,
    $pageHome: null,
    $pageConfigure: null,
    $itemContainer: null,
    currentPathRelative: null,
    currentPath: null,
    pathSeparator: '/',

    pageConfigure: '#configure',
    apiServer: null,
    bundleList: 'data-bundle-list',
    bundleConfig: 'data-bundle-config',

    attrItemPosition: 'data-position',
    attrItemFolder: 'data-item-folder',
    attrItemPdf: 'data-item-pdf',
    attrItemImage: 'data-gallery',
    attrItemVideo: 'data-item-video',

    templateBundle: '#template-bundle',
    zipMediaFilename: 'media.zip',
    configFilename: 'config.json',
    $waitPanel: null,

    // Application Constructor
    initialize: function() {
        document.addEventListener('deviceready', this.onDeviceReady.bind(this), false);
    }, // initialize

    // deviceready Event Handler
    //
    // Bind any cordova events here. Common events are:
    // 'pause', 'resume', etc.
    onDeviceReady: function() {
        var that = this
        that.setEnvironment.call(that)
        console.log('onDeviceReady');

        that.$waitPanel = $('#wait')

        that.$pageHome = $('#home')
        that.$pageConfigure = $('#configure')
        that.$itemContainer = that.$pageHome.find('#home-item-container')


        var from = getQueryVariable('from')

        var loadLocalConfigFileSuccess = that.onDeviceReadyLocalConfigSuccessWithUpdateCheck.bind(that)
        if( typeof from !== 'undefined' ){
            loadLocalConfigFileSuccess = that.onDeviceReadyLocalConfigSuccess.bind(that)
        }

        var loadLocalConfigFileFailed = that.onDeviceReadyLocalConfigError.bind(that)
        that.loadLocalConfigFile.call( that, loadLocalConfigFileSuccess, loadLocalConfigFileFailed )

        $(document).on('vclick','['+that.attrItemFolder+']',that.itemFolderClick.bind(that))
        $(document).on('vclick','['+that.attrItemVideo+']',that.itemVideoClick.bind(that))
        $(document).on('vclick','['+that.attrItemPdf+']',that.itemPdfClick.bind(that))

        $(document).on('click','['+that.bundleConfig+']',that.onDownloadBundleClick.bind(that))
    }, // onDeviceReady

    setEnvironment: function(){
        var that = this
        if( that.environment == ENVIRONMENT_DEVELOPMENT ){
            that.apiServer = 'http://appsapi.visiv.local'
            $('#developmentbadge').show();

        }else{
            that.apiServer = 'http://appsapi.wip.visivcomunicazione.it'
        }
    }, // setEnvironment

    uiNotifyError: function(message,fnCallback,title,buttonName){
        var that = this
        message = ( typeof message === 'string' ) ? message : "Si è verificato un errore da gestiremeglio";
        fnCallback = ( typeof fnCallback === 'function' ) ? fnCallback : null
        title = ( typeof title === 'string' ) ? title : "Errore Applicazione";
        buttonName = ( typeof buttonName === 'string' ) ? buttonName : "Chiudi";
        navigator.notification.alert(message,fnCallback,title,buttonName)
    }, // uiNotifyError

    itemPdfClick: function(event){
        var that = this
        var target = $(event.currentTarget)
        var pdfUrl = target.attr(that.attrItemPdf)
        window.location.href = 'pdfjs-1.9.426-dist/web/viewer.html?f=' + pdfUrl + '&path=' + that.currentPathRelative
    }, // itemPdfClick

    itemVideoClick: function(event){
        var that = this
        var target = $(event.currentTarget)
        var videoUrl = target.attr(that.attrItemVideo)
        window.plugins.streamingMedia.playVideo(videoUrl,{closeOnLongTap:true});
        //closeOnLongTap
    }, // itemVideoClick


    itemFolderClick: function(event){
        var that = this
        var target = $(event.currentTarget)
        var relativeItemPath = target.attr(that.attrItemFolder)
        that.uiDrawContent.call(that,relativeItemPath)
    }, // itemFolderClick

    uiDrawBreadcrumbs: function(){
        var that = this

        var $breadcrumbsContainer = $('[data-role="breadcrumbs"]');
        $breadcrumbsContainer.empty()
        var absoluteMediaPath = that.getLocalMediaPath.call(that)

        var relativeItemPath = that.stringDiff.call( that, that.currentPath, absoluteMediaPath )
        var pieces = relativeItemPath.split( that.pathSeparator ).filter( Boolean ) // REMOVE EMPTY
        var path = '';
        pieces.unshift( that.pathSeparator ) // ADD HOME
        console.log(pieces)
        $.each( pieces, function( i, piece ) {
            if( i == 0 ){ // HOMEPAGE
                var fullItemPath = piece
            }else{
                path = path + piece + that.pathSeparator
                fullItemPath = path
            }

            var items = that.findItemConfigByAttr.call(that,'name',fullItemPath)
            if( items.length == 0 ){ return; }
            var item = items[0]
            var label = item.monitorLabel || piece;
            var $template = $('<span>').html(label)
            $template.attr( that.attrItemFolder, path )
            $breadcrumbsContainer.append($template)
        });
    }, // uiDrawBreadcrumbs



    uiDrawContent: function( relativePath, fnBegin, fnComplete ){
        var that = this

        fnBegin = fnBegin ||  function(){}
        fnComplete = fnComplete || function(){}



        relativePath = (typeof relativePath === 'string') ? relativePath : '/'

        that.currentPathRelative = relativePath
        that.currentPath = that.getLocalMediaPath.call(that) + relativePath

        fnBegin.call(that)

        that.uiDrawBreadcrumbs.call(that)

        var fnListDirSuccess = function(entries){
            that.$itemContainer.empty()
            $.each( entries, function(i,entry){
                console.log(entry);
                var absoluteMediaPath = that.getLocalMediaPath.call(that)
                var relativeItemPath = that.stringDiff.call(that,entry.nativeURL,absoluteMediaPath)
                var itemsConfig = that.findItemConfigByAttr.call(that,'name',relativeItemPath)
                if( itemsConfig.length == 0 ){ return; }
                var item = itemsConfig[0]
                if( !item.visible ){ return; }
                var $template = $($('#template-home-item').html())
                // IS PRESENT IN JSON CONFIG ?
                var label = item.hasOwnProperty('label') ? item.monitorLabel : entry.name;
                var position = item.hasOwnProperty('monitorIndex') ? item.monitorIndex : Number.MAX_SAFE_INTEGER;
                // TEMPLATE ATTRIBUTES
                $template.attr( that.attrItemPosition, position )
                $template.find('.item-label').html( label )
                if( entry instanceof DirectoryEntry ){
                    that.uiDrawContentFolderItem.call( that, that.$itemContainer, $template, relativeItemPath )
                }else if( entry instanceof FileEntry ){
                    entry.file(function(data) {
                        var mimeType = data.type
                        var templateIconClass = 'bi-file-earmark'
                        if( (new RegExp('video')).test(mimeType) ){ that.uiDrawContentVideoItem.call( that, that.$itemContainer, $template, entry ); }
                        else if( (new RegExp('pdf')).test(mimeType) ){ that.uiDrawContentPdfItem.call( that, that.$itemContainer, $template, entry ); }
                        else if( (new RegExp('image')).test(mimeType) ){ that.uiDrawContentImageItem.call( that, that.$itemContainer, $template, entry, item ); }
                    })
                }
            })
            setTimeout(function(){
                that.uiSortElementByAttribute.call( that, that.$itemContainer, that.attrItemPosition )
                fnComplete.call(that)
            },100);



        } // fnListDirSuccess

        that.listDir.call(that,that.currentPath,fnListDirSuccess)
    }, // uiDrawContent

    // ORDINA GLI ELEMENTI BASANDOSI SULL'ATTRIBUTO
    uiSortElementByAttribute: function( $container, sortingAttribute ){
        $container.find('['+sortingAttribute+']').sort(function(a, b) {
        return +$(a).attr(sortingAttribute) - +$(b).attr(sortingAttribute);
        }).appendTo($container);
    }, // uiSortElementByAttribute

    uiDrawContentFolderItem: function( itemContainer, $template, relativeItemPath ){
        var that = this
        var $icon = $template.find('.bi')
        $template.attr( that.attrItemFolder, relativeItemPath )
        $icon.addClass('bi-folder')
        itemContainer.append( $template )
    }, // uiDrawContentFolderItem

    uiDrawContentVideoItem: function( itemContainer, $template, entry ){
        var that = this
        var $icon = $template.find('.bi')
        templateIconClass = 'bi-camera-video';
        $icon.addClass( templateIconClass )
        $template.attr( that.attrItemVideo, entry.nativeURL )
        itemContainer.append( $template )
    }, // uiDrawContentVideoItem

    uiDrawContentImageItem: function( itemContainer, $template, entry, item ){
        var that = this
        var anchorAttrs = { 'href': entry.nativeURL }
        anchorAttrs[that.attrItemImage] = '';
        if( item.hasOwnProperty('title') ){ anchorAttrs.title = item.title; }
        $template.addClass('image');
        $template.css('background-image', 'url(' + entry.nativeURL + ')');
        var $anchor = $('<a>',anchorAttrs)
        $template.find('> div').wrap( $anchor )
        itemContainer.append( $template )
//        $template.wrap( $('<a>',anchorAttrs) )
    }, // uiDrawContentImageItem

    uiDrawContentPdfItem: function( itemContainer, $template, entry ){
        var that = this
        var $icon = $template.find('.bi')
        templateIconClass = 'bi-file-earmark-pdf';
        $icon.addClass( templateIconClass )
        $template.attr( that.attrItemPdf, entry.nativeURL )
        itemContainer.append( $template )
    }, // uiDrawContentPdfItem


    findItemConfigByAttr: function( attr, value ){
        var that = this
        var propertyName = 'items'
        if( !that.config.hasOwnProperty(propertyName) ){ return []; }
        var founded = that.config[propertyName].filter( i => i[attr] === value );
        return founded;
    }, // isItemExcluded

    // REMOVE str2 FROM str1
    stringDiff: function( str1, str2 ){
        var that = this
        var strEnd = str1.length
//        var strStart = that.getLocalMediaPath.call(that).length
        var strStart = str2.length
        return str1.substring(strStart, strEnd)
    }, // stringDiff


    listDir: function (path,fnSuccess,fnError){
        var that = this

        var fnError = (  typeof fnError === 'function' ) ? fnError : function(error){ console.log(error); }
        var fnSuccess = (  typeof fnSuccess === 'function' ) ? fnSuccess : function(entries){ console.log(entries); }

        var fnResolveSuccess = function (fileSystem) {
            var reader = fileSystem.createReader();
            reader.readEntries(fnSuccess, fnError);
        } // fnResolveSuccess

      window.resolveLocalFileSystemURL( path, fnResolveSuccess, fnError);
    }, // listDir

    uiInstallDownloadProgress: function(event){
        var that = this
        var p = (event.lengthComputable) ? (event.loaded / event.total) : p+1;
        var msg = 'download file zip ' + (Math.trunc(p*100)) + '%'
        console.log(msg)
        that.uiWaitSetText.call(that,msg)
    }, // uiInstallDownloadProgress

    uiUnzipProgress: function(event){
        var that = this
        var percent =  Math.round((event.loaded / event.total)*100)
        var msg = 'Estrazione file zip ' + percent + '%'
        console.log(msg)
        that.uiWaitSetText.call(that,msg)
    }, // uiUnzipProgress

    uiUpdateInstallComplete: function(){
        var that = this
        var fnDrawContentComplete = function(){
            jQuery.mobile.changePage(that.$pageHome);
            that.uiWaitToggle.call(that)
        }
        var relativePath = that.getQueryVariablePath.call(that)
        that.uiDrawContent.call(that,relativePath,fnDrawContentComplete)
    }, // uiUpdateInstallComplete

    onDownloadBundleClick: function(event){
        var that = this
        var target = $(event.currentTarget)
        var configFileUrl = target.attr(that.bundleConfig);

        var fnApiGetJsonConfigError = function( jqXHR, textStatus, errorThrown ){ console.log(jqXHR); } // TODO:
        var fnApiGetJsonConfigSuccess = function( json ){
            var fnBegin = that.uiWaitToggle.bind(that)
            var fnError = that.uiNotifyError.bind( that, 'Si è verificato un errore nell\'installazione degli aggiornamenti. Si prega di disinstallare e reinstallare l\'app');
            var fnDownloadProgress = that.uiInstallDownloadProgress.bind(that)
            var fnUnzipProgress = that.uiUnzipProgress.bind(that)
            var fnComplete = that.uiUpdateInstallComplete.bind(that)
            that.installUpdates.call(that,json,fnBegin,fnError,fnDownloadProgress, fnUnzipProgress, fnComplete )
        } // fnApiGetJsonConfigSuccess

        that.apiGetJsonConfig.call(that,configFileUrl,fnApiGetJsonConfigSuccess,fnApiGetJsonConfigError)
    }, // onDownloadBundleClick

    installUpdates: function( configObj, fnBegin, fnError, fnDownloadZipProgress, fnUnzipProgress, fnComplete ){
        var that = this
        fnBegin = fnBegin || function(){}
        fnError = fnError || function(err){}
        fnDownloadZipProgress = fnDownloadZipProgress || function(event){}
        fnUnzipProgress = fnUnzipProgress || function(event){}

        fnBegin.call(that)
        var fileContent = JSON.stringify(configObj);
        var dstConfigFolder = that.getLocalConfigFolder.call(that)
        var dstMediaZipFile = that.getLocalZipMediaPathFilename.call(that)
        var mediaFolderPath = that.getLocalMediaPath.call(that)
        var localConfigPathFilename = that.getLocalConfigPathFilename.call(that)

        var fnUnzipSuccess = function(result){
            that.deleteFile.call(that,dstMediaZipFile);
            fnComplete.call(that)
        } // fnUnzipSuccess

        var fnDownloadZipError = fnError.bind(that)
        var fnDownloadZipSuccess = function(entry){
            console.log('zip bundle salvato nel dispositivo '+dstMediaZipFile);
            that.unzipFile.call( that, dstMediaZipFile, mediaFolderPath, fnUnzipSuccess, fnError.bind(that), fnUnzipProgress );
        } // fnDownloadZipSuccess

        var fnConfigFileSaveSuccess = function(){
            that.config = configObj
            var downloadUrl = that.apiServer + that.config.mediaZipFileUrl
            that.downloadFile.call( that, downloadUrl, dstMediaZipFile, fnDownloadZipSuccess, fnDownloadZipError, fnDownloadZipProgress );
        } // fnConfigFileSaveSuccess
        var fnConfigFileSaveError = fnError.bind(that) // that.uiNotifyError.bind( that, 'Errore nel salvataggio del file di configurazione. Disinstalla e reinstalla l\'app'+that.configFilename, fnError.bind(that) );
        var fnDeleteConfigFileSuccess = that.saveTextFile.bind( that, fileContent, dstConfigFolder, that.configFilename, fnConfigFileSaveSuccess, fnConfigFileSaveError )
        var fnDeleteConfigFileError = fnDeleteConfigFileSuccess
        var fnDeleteFolderMediaSuccess = that.deleteFile.bind(that,localConfigPathFilename,fnDeleteConfigFileSuccess,fnDeleteConfigFileError)
        var fnDeleteFolderMediaError = fnDeleteFolderMediaSuccess
        that.deleteDir.call(that,mediaFolderPath,fnDeleteFolderMediaSuccess,fnDeleteFolderMediaError)
    }, // installUpdates

    getLocalMediaPath: function(){
        var that = this
        var mediaFolder = 'media_app'
        return cordova.file.dataDirectory + 'Documents/' + mediaFolder + '/';
    }, // getLocalMediaPath

    unzipFile: function( zipFilename, dstFolder, fnSuccess, fnFailed, fnProgress ){
        var that = this
        fnSuccess = ( typeof fnSuccess === 'function' ) ? fnSuccess : function(result){ console.log(zipFilename+'estratto con successo in '+dstFolder); }
        fnError = ( typeof fnError === 'function' ) ? fnError : function(error){ console.log(zipFilename+'non è stato estratto '); }
        fnProgress = ( typeof fnProgress === 'function' ) ? fnProgress : function(progressEvent){
            var percent =  Math.round((progressEvent.loaded / progressEvent.total)*100)
            console.log('Estrazione ' + zipFilename + ' ' + percent + '%');
        } // fnProgress


        var fnCallback = function(result){
            if( result == 0){ fnSuccess(result) }
            else{ fnError(result); }
        } // fnCallback
        zip.unzip(zipFilename, dstFolder, fnCallback, fnProgress);
    }, // unzipFile

    deleteFile: function( pathFilename, fnSuccess, fnFailed ){
        var that = this
        var lastSlash = pathFilename.lastIndexOf("/");
        var path = pathFilename.substr(0,lastSlash);
        var filename = pathFilename.substr(lastSlash+1);

        var fnFailed = ( typeof fnFailed === 'function' ) ? fnFailed : function(error){ console.log(error); }
        var fnSuccess = ( typeof fnSuccess === 'function' ) ? fnSuccess : function(){ console.log('Il file '+ pathFilename +' è stato cancellato'); }

        var fnFileNotFound = function(error){
            console.log('Il file ' + filename + ' non è stata trovato in ' + path + ' o non è accessibile' );
            fnFailed(error);
        } // fnFileNotFound

        var fnDirFound = function(dir){
            dir.getFile(filename, {create:false}, function(fileEntry) {
                fileEntry.remove(fnSuccess,fnFailed);
            }, fnFileNotFound);
        } // fnDirFound


        var fnDirNotFound = function(error){
            console.log('La directory ' + path + ' non è stata trovata o non è accessibile' );
            fnFailed(error);
        } // fnDirNotFound
        window.resolveLocalFileSystemURL( path, fnDirFound, fnDirNotFound );
    }, // deleteFile

    deleteDir: function( target, fnSuccess, fnError ){
        fnSuccess = fnSuccess || function(){ console.log('successfully deleted the folder and its content'); }
        fnError = fnError || function(error){ console.error('there was an error deleting the directory', e.toString()); }

        var fnDirFound = function(dirEntry){
            dirEntry.removeRecursively(fnSuccess,fnError)
        } // fnDirFound
        window.resolveLocalFileSystemURL(target, fnDirFound, fnError );
    }, // deleteDir


    uiWaitToggle: function(){
        var that = this
        that.$waitPanel.toggleClass('hide')
    }, // uiWaitToggle

    uiWaitSetText: function( html ){
        var that = this
        that.$waitPanel.find('.info').html(html)
    }, // uiWaitSetText

    getLocalZipMediaPathFilename: function(){
        var that = this
        return that.getLocalZipMediaFolder.call(that) + that.zipMediaFilename
    }, // getLocalZipMediaPathFilename


    getLocalZipMediaFolder: function(){
        var that = this
        return cordova.file.dataDirectory + 'Documents/';
    }, // getLocalZipMediaFolder

    downloadFile: function( uri, fileURL, fnSuccess, fnError, fnProgress, trustAllHosts, options ){
        var that = this

        var fnProgress = (  typeof fnProgress === 'function' ) ? fnProgress : function(event) {
            var p = (event.lengthComputable) ? (event.loaded / event.total) : p+1;
            console.log('download ' + (Math.trunc(p*100)) + '%');
        } // fnProgress

        var fileTransfer = new FileTransfer();
        trustAllHosts = (typeof trustAllHosts != "boolean") ? true : trustAllHosts;
        options = ( typeof options === 'object' ) ? options : {}
        var uri = encodeURI( uri );
        console.log('Inizio download del file ' + uri + ' salvataggio in '+fileURL)
        fileTransfer.download( uri, fileURL, fnSuccess, fnError, trustAllHosts, options );
        fileTransfer.onprogress = fnProgress
        return fileTransfer;
    }, // downloadFile


    apiGetJsonConfig: function( relativeUrl, fnSuccess, fnError ){
        var that = this
        var url = encodeURI( that.apiServer + relativeUrl )

        var ajaxOpts = {
            url: url,
            success: fnSuccess,
            error: fnError,
            dataType: 'json',
       } // ajaxOpts
        $.ajax(ajaxOpts);
    }, // apiGetJsonConfig

    getLocalConfigFolder: function(){
        var that = this
        return cordova.file.dataDirectory;
    }, // getLocalConfigFolder

    saveTextFile: function( fileContent, dstFolder, dstFilename, fnSuccess, fnError ){
        var that = this

        var fnCreateWriterSuccess = function(fileWriter) {
            var blob = new Blob([fileContent], {type:'text/plain'});
            fileWriter.onwriteend = fnSuccess;
            fileWriter.onerror = fnError;
            fileWriter.write(blob);
        } // fnCreateWriterSuccess

        var fnFileAccessError = function(error){
            console.log('Impossibile accedere alla file ' + dstFilename);
            fnError();
        } // fnFileAccessError

        var fnFileAccessSuccess = function(file){
            file.createWriter( fnCreateWriterSuccess, fnError ); // createWriter
        } // fnFileAccessSuccess

        var fnFolderAccessError = function(error){
            console.log('Impossibile accedere alla directory ' + dstFolder);
            fnError();
        } // fnFolderAccessError

        var fnFolderAccessSuccess = function(dir) {
            var opts = {create:true,exclusive: false}
            dir.getFile(dstFilename, opts, fnFileAccessSuccess, fnFileAccessError );
        } // fnFolderAccessSuccess

        window.resolveLocalFileSystemURL( dstFolder, fnFolderAccessSuccess, fnFolderAccessError );
    }, // saveTextFile

    validateConfigObject: function( json ){
        var that = this
        if( json == null ){ return false; }
        return json.hasOwnProperty('items') && json.hasOwnProperty('mediaZipFileUrl');
    }, // validateConfigObject

    onDeviceReadyLocalConfigSuccessWithUpdateCheck: function(evt){
        var that = this
        var localConfig = jQuery.parseJSON(evt.target.result)
        that.uiWaitSetText.call(that,'Verifico aggioramenti');
        that.uiWaitToggle.call(that)
//        var mediaFolderPath = that.getLocalMediaPath.call(that)
//        var localConfigPathFilename = that.getLocalConfigPathFilename.call(that)
//

        var fnDownloadProgress = that.uiInstallDownloadProgress.bind(that)
        var fnUnzipProgress = that.uiUnzipProgress.bind(that)
        var fnGetConfigBundleFromWSError = that.onDeviceReadyLocalConfigSuccess.bind(that,evt)
        var fnComplete = that.uiUpdateInstallComplete.bind(that)
        var fnGetConfigBundleFromWSSuccess = function(remoteConfig,textStatus,jqXHR){
            var mustUpdated = ( localConfig.mediaConfigFileHash != remoteConfig.mediaConfigFileHash );
            if( mustUpdated ){
                console.log('sono disponibili aggiornamenti');
                var fnInstallBegin = null;
                var fnInstallError = function(){
                    var fnCloseNotifyError = function(){
                        var fnDrawContentComplete = function(){
                            jQuery.mobile.changePage(that.$pageHome);
                            that.uiWaitToggle.call(that)
                        } // fnDrawContentComplete
                        var relativePath = that.getQueryVariablePath.call(that)
                        that.uiDrawContent.call(that,relativePath,fnDrawContentComplete)
                    } // fnCloseNotifyError

                    that.uiNotifyError.call(that,'Si è verificato un errore durante l\'aggiornamento', fnCloseNotifyError);
                } // fnInstallError
                that.installUpdates.call(that,remoteConfig,fnInstallBegin,fnInstallError,fnDownloadProgress, fnUnzipProgress, fnComplete )
            }else{
                console.log('NON sono disponibili aggiornamenti');
                that.uiWaitToggle.call(that)
                that.onDeviceReadyLocalConfigSuccess.call(that,evt)
            }
        } // fnGetConfigBundleFromWSSuccess
        that.apiGetJsonConfig.call(that,localConfig.mediaConfigFileUrl,fnGetConfigBundleFromWSSuccess, fnGetConfigBundleFromWSError )
    }, // onDeviceReadyLocalConfigSuccessWithUpdateCheck

    onDeviceReadyLocalConfigSuccess: function(evt){
        var that = this
        var json = jQuery.parseJSON(evt.target.result)
        that.config = json;
        var fnDrawContentComplete = function(){
            jQuery.mobile.changePage(that.$pageHome);
//            that.uiWaitToggle.call(that)
        };
        var relativePath = that.getQueryVariablePath.call(that)
        that.uiDrawContent.call(that,relativePath,fnDrawContentComplete)
    }, // onDeviceReadyLocalConfigSuccess

    getQueryVariablePath: function(){
        var that = this
        return getQueryVariable('path');
    }, // getQueryVariablePath

    onDeviceReadyLocalConfigError: function(){
        var that = this
        var fnSuccess = function(json,textStatus,jqXHR){
            that.uiPageConfigurePopulateBundleList.call(that,json.bundles)
        } // fnSuccess
        var fnError = null
        that.apiGetBundles.call(that,fnSuccess,fnError)
        jQuery.mobile.changePage(that.pageConfigure);
    }, // onDeviceReadyLocalConfigError

    apiGetBundles: function( fnSuccess, fnError ){
        var that = this
        var ajaxOpts = {
            url: that.apiServer + '/explorer-app/visualizza-tutti-i-files/json',
            dataType: 'json',
            success: fnSuccess, //function(json,textStatus,jqXHR){ that.uiPageConfigurePopulateBundleList.call( that, json.bundles ); },
            error: fnError, // function( jqXHR, textStatus, errorThrown ){ console.log(jqXHR); },
        } // ajaxOpts
        $.ajax( ajaxOpts )
    }, // apiGetBundles



    uiPageConfigurePopulateBundleList: function( bundles ){
        var that = this
        var page = $(that.pageConfigure)
        var bundleContainer = page.find('['+that.bundleList+']');
        bundleContainer.empty()
        $.each(bundles,function(i,bundle){
            var template = $($(that.templateBundle).html())
            template.find('['+that.bundleConfig+']').attr(that.bundleConfig,bundle.mediaConfigFileUrl)
                                                      .html(bundle.filename)
            bundleContainer.append(template)
        });
    }, // uiPageConfigurePopulateBundleList


    loadLocalConfigFile: function(fnFileExists,fnFileNotExists){
        var that = this
        var pathFilename = that.getLocalConfigPathFilename.call(that)

        that.readTextFile.call(that,pathFilename,fnFileExists,fnFileNotExists)

////        readTextFile: function( pathFilename, fnFileReaded, fnFileNotFound ){
//
//
//        console.log(pathFilename)
//        window.resolveLocalFileSystemURL( pathFilename, fnFileExists, fnFileNotExists )
    }, // loadLocalConfigFile

    readTextFile: function( pathFilename, fnFileReaded, fnFileNotFound ){
        var that = this
        //var appConfigFile = that.store + that.appConfigJson;

        var fnFileFound = function(fileEntry){
            fileEntry.file(function(file) {
                var reader = new FileReader();
                reader.onloadend = fnFileReaded
                reader.readAsText(file);
            })
        } // fnFileFound

        window.resolveLocalFileSystemURL( pathFilename, fnFileFound, fnFileNotFound )
    }, // readTextFile


    getLocalConfigPathFilename: function(){
        var that = this
        return that.getLocalConfigFolder.call(that) + that.configFilename
    }, //getLocalConfigPathFilename

    getLocalConfigFolder: function(){
        var that = this
        return cordova.file.dataDirectory;
    }, // getLocalConfigFolder

};

app.initialize();
