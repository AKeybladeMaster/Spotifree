(function () {
    'use strict';
    var result = {
        status: 'pending',
        ua: navigator.userAgent,
        secureContext: window.isSecureContext === true,
        mediaSource: !!window.MediaSource,
        emeApi: typeof navigator.requestMediaKeySystemAccess === 'function'
    };
    window.__spotifreePlaybackDiagnostics = result;
    var finished = false;
    var timer;
    function finish(status, message) {
        if (finished) return;
        finished = true;
        clearTimeout(timer);
        result.status = status;
        result.widevineEme = message;
    }
    try {
        var hints = navigator.userAgentData;
        result.uaData = hints ? {
            mobile: hints.mobile,
            platform: hints.platform,
            brands: hints.brands
        } : null;
        var audio = document.createElement('audio');
        result.audioOgg = !!audio.canPlayType('audio/ogg; codecs="vorbis"');
        result.audioMp4 = !!audio.canPlayType('audio/mp4; codecs="mp4a.40.2"');
        result.audioWebm = !!audio.canPlayType('audio/webm; codecs="opus"');
        if (!result.emeApi) {
            finish('unavailable', 'Encrypted Media Extensions API is unavailable');
            return;
        }
        timer = setTimeout(function () {
            finish('timeout', 'Widevine did not finish initializing within 8 seconds');
        }, 8000);
        navigator.requestMediaKeySystemAccess('com.widevine.alpha', [
            { initDataTypes: ['cenc'], audioCapabilities: [{ contentType: 'audio/mp4; codecs="mp4a.40.2"' }] },
            { initDataTypes: ['webm'], audioCapabilities: [{ contentType: 'audio/webm; codecs="opus"' }] }
        ]).then(function (access) {
            if (finished) return;
            result.keySystem = access.keySystem;
            result.configuration = access.getConfiguration();
            return access.createMediaKeys();
        }).then(function () {
            finish('available', 'MediaKeys created; a Spotify streaming license has not been tested');
        }).catch(function (error) {
            finish('failed', String(error && error.name || 'Error') + ': ' + String(error && error.message || error));
        });
    } catch (error) {
        finish('failed', String(error && error.name || 'Error') + ': ' + String(error && error.message || error));
    }
})();
