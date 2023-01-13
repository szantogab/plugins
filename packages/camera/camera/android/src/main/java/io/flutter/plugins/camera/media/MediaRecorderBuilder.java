// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera.media;

import android.media.CamcorderProfile;
import android.media.EncoderProfiles;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MediaRecorderBuilder {
  @SuppressWarnings("deprecation")
  static class MediaRecorderFactory {
    MediaRecorder makeMediaRecorder() {
      return new MediaRecorder();
    }
  }

  private final String outputFilePath;
  private final CamcorderProfile camcorderProfile;
  private final EncoderProfiles encoderProfiles;
  private final MediaRecorderFactory recorderFactory;

  private boolean enableAudio;
  private int mediaOrientation;

  public MediaRecorderBuilder(
      @NonNull CamcorderProfile camcorderProfile, @NonNull String outputFilePath) {
    this(camcorderProfile, outputFilePath, new MediaRecorderFactory());
  }

  public MediaRecorderBuilder(
      @NonNull EncoderProfiles encoderProfiles, @NonNull String outputFilePath) {
    this(encoderProfiles, outputFilePath, new MediaRecorderFactory());
  }

  MediaRecorderBuilder(
      @NonNull CamcorderProfile camcorderProfile,
      @NonNull String outputFilePath,
      MediaRecorderFactory helper) {
    this.outputFilePath = outputFilePath;
    this.camcorderProfile = camcorderProfile;
    this.encoderProfiles = null;
    this.recorderFactory = helper;
  }

  MediaRecorderBuilder(
      @NonNull EncoderProfiles encoderProfiles,
      @NonNull String outputFilePath,
      MediaRecorderFactory helper) {
    this.outputFilePath = outputFilePath;
    this.encoderProfiles = encoderProfiles;
    this.camcorderProfile = null;
    this.recorderFactory = helper;
  }

  public MediaRecorderBuilder setEnableAudio(boolean enableAudio) {
    this.enableAudio = enableAudio;
    return this;
  }

  public MediaRecorderBuilder setMediaOrientation(int orientation) {
    this.mediaOrientation = orientation;
    return this;
  }

  public MediaRecorder build() throws IOException, NullPointerException, IndexOutOfBoundsException {
    MediaRecorder mediaRecorder = recorderFactory.makeMediaRecorder();

    // There's a fixed order that mediaRecorder expects. Only change these functions accordingly.
    // You can find the specifics here: https://developer.android.com/reference/android/media/MediaRecorder.
    if (enableAudio) mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

    if (Build.VERSION.SDK_INT >= 31) {
      List<String> a = new ArrayList<>();

      EncoderProfiles.VideoProfile videoProfile = encoderProfiles.getVideoProfiles().get(0);
      for (int i = 0; i < encoderProfiles.getVideoProfiles().size(); i++) {
        EncoderProfiles.VideoProfile curVideoProfile = encoderProfiles.getVideoProfiles().get(i);

        Log.d("MediaRecorderBuilder", "Video codec: " + curVideoProfile.getCodec().toString + ", bitrate: " + curVideoProfile.getBitrate().toString());

        if (curVideoProfile.getCodec() == MediaRecorder.VideoEncoder.HEVC) {
          if (videoProfile.getCodec() != MediaRecorder.VideoEncoder.HEVC || videoProfile.getBitrate() > curVideoProfile.getBitrate()) {
            videoProfile = curVideoProfile;
          }
        }
      }

      Log.d("MediaRecorderBuilder", "Using video codec: " + videoProfile.getCodec().toString + ", bitrate: " + videoProfile.getBitrate().toString());

      EncoderProfiles.AudioProfile audioProfile = encoderProfiles.getAudioProfiles().get(0);

      mediaRecorder.setOutputFormat(encoderProfiles.getRecommendedFileFormat());
      if (enableAudio) {
        mediaRecorder.setAudioEncoder(audioProfile.getCodec());
        mediaRecorder.setAudioEncodingBitRate(audioProfile.getBitrate());
        mediaRecorder.setAudioSamplingRate(audioProfile.getSampleRate());
      }
      mediaRecorder.setVideoEncoder(videoProfile.getCodec());
      mediaRecorder.setVideoEncodingBitRate(videoProfile.getBitrate());
      mediaRecorder.setVideoFrameRate(videoProfile.getFrameRate());
      mediaRecorder.setVideoSize(videoProfile.getWidth(), videoProfile.getHeight());
      mediaRecorder.setVideoSize(videoProfile.getWidth(), videoProfile.getHeight());
    } else {
      mediaRecorder.setOutputFormat(camcorderProfile.fileFormat);
      if (enableAudio) {
        mediaRecorder.setAudioEncoder(camcorderProfile.audioCodec);
        mediaRecorder.setAudioEncodingBitRate(camcorderProfile.audioBitRate);
        mediaRecorder.setAudioSamplingRate(camcorderProfile.audioSampleRate);
      }
      mediaRecorder.setVideoEncoder(camcorderProfile.videoCodec);
      mediaRecorder.setVideoEncodingBitRate(camcorderProfile.videoBitRate);
      mediaRecorder.setVideoFrameRate(camcorderProfile.videoFrameRate);
      mediaRecorder.setVideoSize(
          camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
    }

    mediaRecorder.setOutputFile(outputFilePath);
    mediaRecorder.setOrientationHint(this.mediaOrientation);

    mediaRecorder.prepare();

    return mediaRecorder;
  }
}
