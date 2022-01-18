// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera.features.resolution;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.EncoderProfiles;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import io.flutter.plugins.camera.CameraProperties;
import io.flutter.plugins.camera.features.CameraFeature;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static io.flutter.plugins.camera.CameraUtils.computeBestCaptureSize;

/**
 * Controls the resolutions configuration on the {@link android.hardware.camera2} API.
 *
 * <p>The {@link ResolutionFeature} is responsible for converting the platform independent {@link
 * ResolutionPreset} into a {@link android.media.CamcorderProfile} which contains all the properties
 * required to configure the resolution using the {@link android.hardware.camera2} API.
 */
public class ResolutionFeature extends CameraFeature<ResolutionPreset> {
  private Size captureSize;
  private Size previewSize;
  private CamcorderProfile recordingProfileLegacy;
  private EncoderProfiles recordingProfile;
  private ResolutionPreset currentSetting;
  private int cameraId;

  /**
   * Creates a new instance of the {@link ResolutionFeature}.
   *
   * @param cameraProperties Collection of characteristics for the current camera device.
   * @param resolutionPreset Platform agnostic enum containing resolution information.
   * @param cameraName Camera identifier of the camera for which to configure the resolution.
   */
  public ResolutionFeature(
      CameraProperties cameraProperties, ResolutionPreset resolutionPreset, String cameraName) {
    super(cameraProperties);
    this.currentSetting = resolutionPreset;
    try {
      this.cameraId = Integer.parseInt(cameraName, 10);
    } catch (NumberFormatException e) {
      this.cameraId = -1;
      return;
    }
    configureResolution(resolutionPreset, cameraId);
  }

  /**
   * Gets the {@link android.media.CamcorderProfile} containing the information to configure the
   * resolution using the {@link android.hardware.camera2} API.
   *
   * @return Resolution information to configure the {@link android.hardware.camera2} API.
   */
  public CamcorderProfile getRecordingProfileLegacy() {
    return this.recordingProfileLegacy;
  }

  public EncoderProfiles getRecordingProfile() {
    return this.recordingProfile;
  }

  /**
   * Gets the optimal preview size based on the configured resolution.
   *
   * @return The optimal preview size.
   */
  public Size getPreviewSize() {
    return this.previewSize;
  }

  /**
   * Gets the optimal capture size based on the configured resolution.
   *
   * @return The optimal capture size.
   */
  public Size getCaptureSize() {
    return this.captureSize;
  }

  @Override
  public String getDebugName() {
    return "ResolutionFeature";
  }

  @Override
  public ResolutionPreset getValue() {
    return currentSetting;
  }

  @Override
  public void setValue(ResolutionPreset value) {
    this.currentSetting = value;
    configureResolution(currentSetting, cameraId);
  }

  @Override
  public boolean checkIsSupported() {
    return cameraId >= 0;
  }

  @Override
  public void updateBuilder(CaptureRequest.Builder requestBuilder) {
    // No-op: when setting a resolution there is no need to update the request builder.
  }

  @VisibleForTesting
  static Size computeBestPreviewSize(int cameraId, ResolutionPreset preset)
      throws IndexOutOfBoundsException {
    if (preset.ordinal() > ResolutionPreset.high.ordinal()) {
      preset = ResolutionPreset.high;
    }
    if (Build.VERSION.SDK_INT >= 31) {
      EncoderProfiles profile =
          getBestAvailableCamcorderProfileForResolutionPreset(cameraId, preset);
      List<EncoderProfiles.VideoProfile> videoProfiles = profile.getVideoProfiles();
      EncoderProfiles.VideoProfile defaultVideoProfile = videoProfiles.get(0);

      return new Size(defaultVideoProfile.getWidth(), defaultVideoProfile.getHeight());
    } else {
      @SuppressWarnings("deprecation")
      CamcorderProfile profile =
          getBestAvailableCamcorderProfileForResolutionPresetLegacy(cameraId, preset);
      return new Size(profile.videoFrameWidth, profile.videoFrameHeight);
    }
  }

  /**
   * Gets the best possible {@link android.media.CamcorderProfile} for the supplied {@link
   * ResolutionPreset}. Supports SDK < 31.
   *
   * @param cameraId Camera identifier which indicates the device's camera for which to select a
   *     {@link android.media.CamcorderProfile}.
   * @param preset The {@link ResolutionPreset} for which is to be translated to a {@link
   *     android.media.CamcorderProfile}.
   * @return The best possible {@link android.media.CamcorderProfile} that matches the supplied
   *     {@link ResolutionPreset}.
   */
  public static CamcorderProfile getBestAvailableCamcorderProfileForResolutionPresetLegacy(
      int cameraId, ResolutionPreset preset) {
    if (cameraId < 0) {
      throw new AssertionError(
          "getBestAvailableCamcorderProfileForResolutionPreset can only be used with valid (>=0) camera identifiers.");
    }

    switch (preset) {
        // All of these cases deliberately fall through to get the best available profile.
      case max:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
        }
      case ultraHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P);
        }
      case veryHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
        }
      case high:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
        }
      case medium:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
        }
      case low:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA);
        }
      default:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
          return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
        } else {
          throw new IllegalArgumentException(
              "No capture session available for current capture session.");
        }
    }
  }

  @TargetApi(Build.VERSION_CODES.S)
  public static EncoderProfiles getBestAvailableCamcorderProfileForResolutionPreset(
      int cameraId, ResolutionPreset preset) {
    if (cameraId < 0) {
      throw new AssertionError(
          "getBestAvailableCamcorderProfileForResolutionPreset can only be used with valid (>=0) camera identifiers.");
    }

    String cameraIdString = Integer.toString(cameraId);

    switch (preset) {
        // All of these cases deliberately fall through to get the best available profile.
      case max:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_HIGH);
        }
      case ultraHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_2160P);
        }
      case veryHigh:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_1080P);
        }
      case high:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_720P);
        }
      case medium:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_480P);
        }
      case low:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_QVGA);
        }
      default:
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
          return CamcorderProfile.getAll(cameraIdString, CamcorderProfile.QUALITY_LOW);
        }

        throw new IllegalArgumentException(
            "No capture session available for current capture session.");
    }
  }

  private void configureResolution(ResolutionPreset resolutionPreset, int cameraId)
      throws IndexOutOfBoundsException {
    if (!checkIsSupported()) {
      return;
    }

    if (Build.VERSION.SDK_INT >= 31) {
      recordingProfile =
          getBestAvailableCamcorderProfileForResolutionPreset(cameraId, resolutionPreset);
      List<EncoderProfiles.VideoProfile> videoProfiles = recordingProfile.getVideoProfiles();

      EncoderProfiles.VideoProfile defaultVideoProfile = videoProfiles.get(0);
      captureSize = new Size(defaultVideoProfile.getWidth(), defaultVideoProfile.getHeight());
    } else {
      CamcorderProfile camcorderProfile =
              getBestAvailableCamcorderProfileForResolutionPresetLegacy(cameraId, resolutionPreset);
      recordingProfileLegacy = camcorderProfile;
    }

    StreamConfigurationMap configs = cameraProperties.getStreamConfigurationMap();
    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
    List<Size> pictureSizes = new ArrayList<>();
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      android.util.Size [] camera_picture_sizes_hires = configs.getHighResolutionOutputSizes(ImageFormat.JPEG);
      if (camera_picture_sizes_hires != null) {
        for(android.util.Size camera_size : camera_picture_sizes_hires) {
            Log.d("flutter_camera", "high resolution picture size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
          // Check not already listed? If it's listed in both, we'll add it later on when scanning camera_picture_sizes
          // (and we don't want to set supports_burst to false for such a resolution).
          boolean found = false;
          for (android.util.Size sz : camera_picture_sizes) {
            if (sz.equals(camera_size)) {
              found = true;
              break;
            }
          }
          if( !found ) {
              Log.d("flutter_camera", "high resolution [non-burst] picture size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
            Size size = new Size(camera_size.getWidth(), camera_size.getHeight());
            pictureSizes.add(size);
          }
        }
      }
    }

    // camera_picture_sizes is null on Samsung Galaxy Note 10+ and S20 for camera ID 4!
    if (camera_picture_sizes != null) {
      for(android.util.Size camera_size : camera_picture_sizes) {
        Log.d("flutter_camera", "picture size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
        pictureSizes.add(new Size(camera_size.getWidth(), camera_size.getHeight()));
      }
    }

    Collections.sort(pictureSizes, new SizeSorter());

    captureSize = pictureSizes.get(0);
    double ratio = (double)captureSize.getWidth() / (double)captureSize.getHeight();

    for (int i = pictureSizes.size() - 1; i >= 0; i--) {
      double _ratio = (double)pictureSizes.get(i).getWidth() / (double)pictureSizes.get(i).getHeight();
      if (_ratio == ratio) {
        previewSize = pictureSizes.get(i);
        break;
      }
    }

    if (previewSize == null) previewSize = computeBestPreviewSize(cameraId, resolutionPreset);

    Log.i("Camera", "[Preview Resolution] :" + previewSize);
    Log.i("Camera", "[Capture Resolution] :" + captureSize);
  }

  /* Sorts resolutions from highest to lowest, by area.
   * Android docs and FindBugs recommend that Comparators also be Serializable
   */
  static class SizeSorter implements Comparator<Size>, Serializable {
    private static final long serialVersionUID = 5802214721073718212L;

    @Override
    public int compare(final Size a, final Size b) {
      return b.getWidth() * b.getHeight() - a.getWidth() * a.getHeight();
    }
  }
}
