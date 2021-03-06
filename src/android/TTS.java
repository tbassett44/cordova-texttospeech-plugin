package com.wordsbaking.cordova.tts;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;

import java.util.HashMap;
import java.util.Locale;

import android.media.AudioManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
/*
    Cordova Text-to-Speech Plugin
    https://github.com/vilic/cordova-plugin-tts

    by VILIC VANE
    https://github.com/vilic

    MIT License
*/

public class TTS extends CordovaPlugin implements OnInitListener {

    public static final String ERR_INVALID_OPTIONS = "ERR_INVALID_OPTIONS";
    public static final String ERR_NOT_INITIALIZED = "ERR_NOT_INITIALIZED";
    public static final String ERR_ERROR_INITIALIZING = "ERR_ERROR_INITIALIZING";
    public static final String ERR_INVALID_PERMISSIONS = "ERR_INVALID_PERMISSIONS";
    public static final String ERR_UNKNOWN = "ERR_UNKNOWN";
    boolean ttsInitialized = false;
    TextToSpeech tts = null;
    AudioManager audioManager = null;
    AudioManager.OnAudioFocusChangeListener afChangeListener;
    AudioAttributes playbackAttributes = null;
    AudioFocusRequest focusRequest = null;

    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        tts = new TextToSpeech(cordova.getActivity().getApplicationContext(), this);
        audioManager = (AudioManager) cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
                // do nothing
            }
            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                }else{
                    audioManager.abandonAudioFocus(afChangeListener);
                }
            }
            @Override
            public void onDone(String callbackId) {
                if (!callbackId.equals("")) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        audioManager.abandonAudioFocusRequest(focusRequest);
                    }else{
                        audioManager.abandonAudioFocus(afChangeListener);
                    }
                    CallbackContext context = new CallbackContext(callbackId, webView);
                    context.success();
                }
            }

            @Override
            public void onError(String callbackId) {
                if (!callbackId.equals("")) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        audioManager.abandonAudioFocusRequest(focusRequest);
                    }else{
                        audioManager.abandonAudioFocus(afChangeListener);
                    }
                    CallbackContext context = new CallbackContext(callbackId, webView);
                    context.error(ERR_UNKNOWN);
                }
            }
        });
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        if (action.equals("speak")) {
            speak(args, callbackContext);
        } else if (action.equals("stop")) {
            stop(args, callbackContext);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            tts = null;
        } else {
            // warm up the tts engine with an empty string
            HashMap<String, String> ttsParams = new HashMap<String, String>();
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "");
            tts.setLanguage(new Locale("en", "US"));
            tts.speak("", TextToSpeech.QUEUE_FLUSH, ttsParams);

            ttsInitialized = true;
        }
    }

    private void stop(JSONArray args, CallbackContext callbackContext)
      throws JSONException, NullPointerException {
        tts.stop();
    }
    
    private void speak(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        JSONObject params = args.getJSONObject(0);
         // Request audio focus for playback
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {//for android 0 and higher
            playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            afChangeListener=new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int i) { }
            };
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build();
            int amResult = audioManager.requestAudioFocus(focusRequest);
        }else{
            int amResult = audioManager.requestAudioFocus(afChangeListener,
                 // Use the music stream.
                 AudioManager.STREAM_MUSIC,
                 // Request permanent focus.
                 AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            if(amResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    callbackContext.error(ERR_INVALID_PERMISSIONS);
                    return;
                }
        }
        if (params == null) {
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        }

        String text;
        String locale;
        double rate;

        if (params.isNull("text")) {
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        } else {
            text = params.getString("text");
        }

        if (params.isNull("locale")) {
            locale = "en-US";
        } else {
            locale = params.getString("locale");
        }

        if (params.isNull("rate")) {
            rate = 1.0;
        } else {
            rate = params.getDouble("rate");
        }

        if (tts == null) {
            callbackContext.error(ERR_ERROR_INITIALIZING);
            return;
        }

        if (!ttsInitialized) {
            callbackContext.error(ERR_NOT_INITIALIZED);
            return;
        }

        HashMap<String, String> ttsParams = new HashMap<String, String>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, callbackContext.getCallbackId());

        String[] localeArgs = locale.split("-");
        tts.setLanguage(new Locale(localeArgs[0], localeArgs[1]));
        tts.setSpeechRate((float) rate);

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, ttsParams);
    }
}
