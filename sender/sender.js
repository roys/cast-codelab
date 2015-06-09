/*global chrome, window, console, document */
/*jslint todo: true */

var APP_ID_TORMOD = 'FD61034B';
var APP_ID_ROY = 'FD61034B';
var APP_ID_PROD = 'FD61034B';
var CAST_NAMESPACE = 'urn:x-cast:city.trapps.cc.codelab';

(function () {
    'use strict';

    var ChromeSender = function () {

        var self = this;
        self.applicationID = null;
        self.session = null;

        function appendMessage(message) { // TODO: We should probably make a global logger that we can tune with log levels
            console.log(message);
            var debugMessageEl = document.getElementById("debugmessage");
            if (typeof message !== 'string') {
                message = JSON.stringify(message);
            }
            debugMessageEl.innerHTML += '<br/>' + message;
        }

        function sessionUpdateListener() {
            appendMessage('Got session update. Session status is now [' + self.session.status + '].');
        }

        /*jslint unparam: true*/
        function sessionMessageListener(namespace, message) {
            appendMessage('Got message from receiver: [' + message + ']');
        }
        /*jslint unparam: false*/

        function sessionListener(session) {
            appendMessage("Got session with id [" + session.sessionId + '].');
            self.session = session;
            self.session.addUpdateListener(sessionUpdateListener);
            self.session.addMessageListener(CAST_NAMESPACE, sessionMessageListener);
        }

        function receiverListener(e) {
            if (e === chrome.cast.ReceiverAvailability.AVAILABLE) {
                appendMessage("Found one or more receivers.");
            } else {
                appendMessage("Unable to find any receivers. Please try again.");
            }
        }

        function onCastInitSuccess() {
            appendMessage("Cast API successfully initialized.");
        }

        function onCastInitError(error) {
            appendMessage("Failed to initialize cast API.");
            appendMessage(error);
        }

        function initializeCastApi() {
            var sessionRequest = new chrome.cast.SessionRequest(self.applicationID, [], 15000),
                apiConfig = new chrome.cast.ApiConfig(sessionRequest, sessionListener, receiverListener);

            chrome.cast.initialize(apiConfig, onCastInitSuccess, onCastInitError);
        }

        self.init = function () {
            if (window.location.protocol === 'file:') {
                appendMessage('!This app needs to be run from http:// and not file://!');
            }
            self.applicationID = window.location.search === '?app-id=prod' ? APP_ID_PROD : (window.location.search === '?app-id=roy' ? APP_ID_ROY : APP_ID_TORMOD);
            appendMessage('Using app id [' + self.applicationID + '].');
            if (!chrome.cast || !chrome.cast.isAvailable) {
                window.setTimeout(initializeCastApi, 1000);
            }
        };

        function onRequestSessionError(error) {
            appendMessage("Failed to get a session. Please ensure that your Chromecast is up and running and try again.");
            appendMessage(error);
        }

        self.launchApp = function () {
            appendMessage("Launching app by requesting session...");
            chrome.cast.requestSession(sessionListener, onRequestSessionError);
        };

        function onStopAppSuccess() {
            appendMessage('Successfully stopped app/session.');
        }

        function onStopAppError(error) {
            appendMessage('Failed to stop app/session.');
            appendMessage(error);
        }

        self.stopApp = function() {
            if (!self.session) {
                appendMessage("Don't have a session. Unable to stop app.");
                return;
            }
            self.session.stop(onStopAppSuccess, onStopAppError);
        };

        function onSendMessageSuccess() {
            appendMessage('onSendMessageSuccess');
        }

        function onSendMessageError(error) {
            appendMessage('Failed to send message.');
            appendMessage(error);
        }

        function sendCommand(command, extra) {
            var message = {};
            message.command = command;
            message.extra = extra;
            appendMessage('Sending message...');
            appendMessage(message);
            self.session.sendMessage(CAST_NAMESPACE, message, onSendMessageSuccess, onSendMessageError);
        }

        self.joinGame = function (playerName) {
            sendCommand('JOIN', playerName);
        };

        self.leaveGame = function () {
            sendCommand('LEAVE');
        };

        self.move = function (direction) {
            sendCommand(direction);
        };

        self.shoot = function () {
            sendCommand('SHOOT');
        };

        self.clearDebugMessages = function () {
            var debugMessageEl = document.getElementById("debugmessage");
            debugMessageEl.innerHTML = '';
        };
    };

    window.ChromeSender = ChromeSender;
})();
