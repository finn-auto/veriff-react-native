package com.veriff.sdk.reactnative;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.veriff.VeriffBranding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class ReactNativeImageProvider implements VeriffBranding.DrawableProvider, Parcelable {
    private static final String TAG = "ReactNativeImage";

    private final String url;

    private final Handler main = new Handler(Looper.getMainLooper());

    public ReactNativeImageProvider(String url) {
        this.url = url;
    }

    protected ReactNativeImageProvider(Parcel in) {
        this(in.readString());
    }

    @NonNull
    @NotNull
    @Override
    @WorkerThread
    public Drawable loadImage(@NonNull @NotNull Context context) throws IOException {
        ImageRequest request = ImageRequestBuilder.fromRequest(ImageRequest.fromUri(this.url)).build();
        DataSource<CloseableReference<CloseableImage>> source =
            Fresco.getImagePipeline().fetchDecodedImage(request, null);

        CountDownLatch imageLoadLatch = new CountDownLatch(1);
        AtomicReference<Result> atomicResult = new AtomicReference<>(null);

        source.subscribe(new BaseBitmapDataSubscriber() {
            @Override
            protected void onNewResultImpl(@Nullable Bitmap bitmap) {
                if (bitmap == null) {
                    atomicResult.set(new Result(new IOException("Loaded bitmap was null")));
                } else {
                    atomicResult.set(new Result(bitmap.copy(bitmap.getConfig(), false)));
                }
                imageLoadLatch.countDown();
            }

            @Override
            protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                Throwable failure = dataSource.getFailureCause();
                if (failure == null) {
                    atomicResult.set(new Result(new IOException("Provided failure cause was null")));
                } else if (failure instanceof IOException) {
                    atomicResult.set(new Result((IOException) failure));
                } else {
                    atomicResult.set(new Result(new IOException("Failed loading image", failure)));
                }
                imageLoadLatch.countDown();
            }
        }, main::post);


        try {
            imageLoadLatch.await();
            Result result = atomicResult.get();

            if (result.error != null) {
                Log.w(TAG, "Loading image from " + this.url + " failed", result.error);
                throw result.error;
            } else {
                Log.w(TAG, "Loading image got a bitmap with size w=" + result.bitmap.getWidth() + " h=" + result.bitmap.getHeight());
                return new BitmapDrawable(context.getResources(), result.bitmap);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupt status
            throw new IOException("Interrupted while loading image");
        }
    }

    private static class Result {
        @Nullable
        private final IOException error;

        @Nullable
        private final Bitmap bitmap;

        private Result(IOException error) {
            this.error = error;
            this.bitmap = null;
        }

        private Result(Bitmap bitmap) {
            this.error = null;
            this.bitmap = bitmap;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ReactNativeImageProvider> CREATOR = new Creator<ReactNativeImageProvider>() {
        @Override
        public ReactNativeImageProvider createFromParcel(Parcel in) {
           return new ReactNativeImageProvider(in);
        }

        @Override
        public ReactNativeImageProvider[] newArray(int size) {
           return new ReactNativeImageProvider[size];
        }
    };
}
