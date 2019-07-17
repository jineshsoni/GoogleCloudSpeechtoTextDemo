/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.examples.speechrecognition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.widget.TextSwitcher
import android.widget.TextView
import com.google.api.gax.rpc.ApiStreamObserver
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean



private const val TAG = "Speech"

/**
 * This example demonstrates calling the Cloud Speech-to-Text bidirectional
 * streaming API.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private var mPermissionToRecord = false
    private var mAudioEmitter: AudioEmitter? = null
    private lateinit var mTextView: TextSwitcher

    private val mSpeechClient by lazy {
        // NOTE: The line below uses an embedded credential (res/raw/sa.json).
        //       You should not package a credential with real application.
        //       Instead, you should get a credential securely from a server.
        applicationContext.resources.openRawResource(R.raw.credential).use {
            SpeechClient.create(SpeechSettings.newBuilder()
                    .setCredentialsProvider { GoogleCredentials.fromStream(it) }
                    .build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get permissions
        ActivityCompat.requestPermissions(
                this, PERMISSIONS, REQUEST_RECORD_AUDIO_PERMISSION)

        // get UI element
        mTextView = findViewById(R.id.last_recognize_result)
        mTextView.setFactory {
            val t = TextView(this)
            t.setText(R.string.start_talking)
            t.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            t.setTextAppearance(android.R.style.TextAppearance_Large)
            t
        }
        mTextView.setInAnimation(applicationContext, android.R.anim.fade_in)
        mTextView.setOutAnimation(applicationContext, android.R.anim.fade_out)

        speechRec()
    }

    override fun onResume() {
        super.onResume()

//        // kick-off recording process, if we're allowed
//        if (mPermissionToRecord) {
//            val isFirstRequest = AtomicBoolean(true)
//            mAudioEmitter = AudioEmitter()
//
//            // start streaming the data to the server and collect responses
//            val requestStream = mSpeechClient.streamingRecognizeCallable()
//                    .bidiStreamingCall(object : ApiStreamObserver<StreamingRecognizeResponse> {
//                        override fun onNext(value: StreamingRecognizeResponse) {
//                            runOnUiThread {
//                                when {
//                                    value.resultsCount > 0 -> mTextView.setText(value.getResults(0).getAlternatives(0).transcript)
//                                    else -> mTextView.setText(getString(R.string.api_error))
//                                }
//                            }
//                        }
//
//                        override fun onError(t: Throwable) {
//                            Log.e(TAG, "an error occurred", t)
//                        }
//
//                        override fun onCompleted() {
//                            Log.d(TAG, "stream closed")
//                        }
//                    })
//
//            // monitor the input stream and send requests as audio data becomes available
//            mAudioEmitter!!.start { bytes ->
//                val builder = StreamingRecognizeRequest.newBuilder()
//                        .setAudioContent(bytes)
//
//                // if first time, include the config
//                if (isFirstRequest.getAndSet(false)) {
//                    builder.streamingConfig = StreamingRecognitionConfig.newBuilder()
//                            .setConfig(RecognitionConfig.newBuilder()
//                                    .setLanguageCode("en-US")
//                                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
//                                    .setSampleRateHertz(16000)
//                                    .build())
//                            .setInterimResults(false)
//                            .setSingleUtterance(false)
//                            .build()
//                }
//
//                // send the next request
//                requestStream.onNext(builder.build())
//            }
//        } else {
//            Log.e(TAG, "No permission to record! Please allow and then relaunch the app!")
//        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun speechRec(){
        // Instantiates a client
//        val speech = SpeechClient.create()

        // The path to the audio file to transcribe
//        val fileName = "./resources/audio.raw"

        val inputStream = resources.openRawResource(R.raw.audio)

        // Reads the audio file into memory
//        val path = Paths.get(fileName)
//        val data = Files.readAllBytes(path)
        val audioBytes = ByteString.readFrom(inputStream)

        // Builds the sync recognize request
        val config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("en-US")
                .build()
        val audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build()

        // Performs speech recognition on the audio file
        val response = mSpeechClient.recognize(config, audio)
        val results = response.resultsList
        results.forEach {
            val alternative = it.alternativesList[0]
            System.out.printf("Transcription: %s%n", alternative.transcript)
            mTextView.setText(alternative.transcript)
        }

        mSpeechClient.close()
    }

    override fun onPause() {
        super.onPause()

        // ensure mic data stops
        mAudioEmitter?.stop()
        mAudioEmitter = null
    }

    override fun onDestroy() {
        super.onDestroy()

        // cleanup
        mSpeechClient.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            mPermissionToRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED
        }

        // bail out if audio recording is not available
        if (!mPermissionToRecord) {
            finish()
        }
    }

}