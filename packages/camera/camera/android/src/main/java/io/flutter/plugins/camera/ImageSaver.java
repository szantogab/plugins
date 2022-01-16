// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.*;
import java.nio.ByteBuffer;

/** Saves a JPEG {@link Image} into the specified {@link File}. */
public class ImageSaver implements Runnable {

  /** The JPEG image */
  private final Image image;

  /** The file we save the image into. */
  private final File file;

  /** Used to report the status of the save action. */
  private final Callback callback;

  /**
   * Creates an instance of the ImageSaver runnable
   *
   * @param image - The image to save
   * @param file - The file to save the image to
   * @param callback - The callback that is run on completion, or when an error is encountered.
   */
  ImageSaver(@NonNull Image image, @NonNull File file, @NonNull Callback callback) {
    this.image = image;
    this.file = file;
    this.callback = callback;
  }

  @Override
  public void run() {
    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.outWidth = image.getWidth();
    options.outHeight = image.getHeight();

    options.inSampleSize = 2;
    //options.inSampleSize = calculateInSampleSize(options, 1920, 1080);
    options.inJustDecodeBounds = false;

    final InputStream inStr = new ByteArrayInputStream(bytes);
    final Bitmap bitmap = BitmapFactory.decodeStream(inStr, null, options);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.WEBP, 90, bos);
    byte[] bitmapdata = bos.toByteArray();
    bitmap.recycle();
    try {
      inStr.close();
      bos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    FileOutputStream output = null;
    try {
      output = FileOutputStreamFactory.create(file);
      output.write(bitmapdata);

      callback.onComplete(file.getAbsolutePath());

    } catch (IOException e) {
      callback.onError("IOError", "Failed saving image");
    } finally {
      image.close();
      if (null != output) {
        try {
          output.close();
        } catch (IOException e) {
          callback.onError("cameraAccess", e.getMessage());
        }
      }
    }
  }

  public int calculateInSampleSize(
          BitmapFactory.Options options, int reqWidth, int reqHeight) {
    // Raw height and width of image
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {

      // Calculate ratios of height and width to requested height and width
      final int heightRatio = Math.round((float) height / (float) reqHeight);
      final int widthRatio = Math.round((float) width / (float) reqWidth);

      // Choose the smallest ratio as inSampleSize value, this will guarantee
      // a final image with both dimensions larger than or equal to the
      // requested height and width.
      inSampleSize = Math.min(heightRatio, widthRatio);
    }

    return inSampleSize;
  }


  /**
   * The interface for the callback that is passed to ImageSaver, for detecting completion or
   * failure of the image saving task.
   */
  public interface Callback {
    /**
     * Called when the image file has been saved successfully.
     *
     * @param absolutePath - The absolute path of the file that was saved.
     */
    void onComplete(String absolutePath);

    /**
     * Called when an error is encountered while saving the image file.
     *
     * @param errorCode - The error code.
     * @param errorMessage - The human readable error message.
     */
    void onError(String errorCode, String errorMessage);
  }

  /** Factory class that assists in creating a {@link FileOutputStream} instance. */
  static class FileOutputStreamFactory {
    /**
     * Creates a new instance of the {@link FileOutputStream} class.
     *
     * <p>This method is visible for testing purposes only and should never be used outside this *
     * class.
     *
     * @param file - The file to create the output stream for
     * @return new instance of the {@link FileOutputStream} class.
     * @throws FileNotFoundException when the supplied file could not be found.
     */
    @VisibleForTesting
    public static FileOutputStream create(File file) throws FileNotFoundException {
      return new FileOutputStream(file);
    }
  }
}
