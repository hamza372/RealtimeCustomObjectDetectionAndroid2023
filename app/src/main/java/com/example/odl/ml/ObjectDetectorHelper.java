package com.example.odl.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import java.util.ArrayList;
import java.util.List;


public final class ObjectDetectorHelper {
    private ObjectDetector objectDetector;
    private float threshold;
    private int maxResults;
    private int currentDelegate;
    private String modelName;

    private RunningMode runningMode;

    private Context context;

    private DetectorListener objectDetectorListener;
    public static final int DELEGATE_CPU = 0;
    public static final int DELEGATE_GPU = 1;
    public static final int MODEL_EFFICIENTDETV0 = 0;
    public static final int MODEL_EFFICIENTDETV2 = 1;
    public static final int MODEL_MN = 2;
    public static final int MAX_RESULTS_DEFAULT = 10;
    public static final float THRESHOLD_DEFAULT = 0.5F;
    public static final int OTHER_ERROR = 0;
    public static final int GPU_ERROR = 1;

    public static final String TAG = "ObjectDetectorHelper";


    public final void clearObjectDetector() {
        ObjectDetector detector = this.objectDetector;
        if (detector != null) {
            detector.close();
        }

        this.objectDetector = null;
    }


    public void setupObjectDetector() {
        // Set general detection options, including number of used threads
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder();

        // Use the specified hardware for running the model. Default to CPU
        if (currentDelegate == DELEGATE_CPU) {
                 baseOptionsBuilder.setDelegate(Delegate.CPU);
        }else if(currentDelegate == DELEGATE_GPU)
        {
                // Is there a check for GPU being supported?
                baseOptionsBuilder.setDelegate(Delegate.GPU);
        }

        baseOptionsBuilder.setModelAssetPath(modelName);

        if(runningMode == RunningMode.LIVE_STREAM) {
            if (objectDetectorListener == null) {
                return;
            }
        }

        try {
            ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setBaseOptions(baseOptionsBuilder.build())
                            .setScoreThreshold(threshold)
                            .setRunningMode(runningMode)
                            .setMaxResults(maxResults);

            if (runningMode == RunningMode.IMAGE || runningMode == RunningMode.VIDEO) {
                optionsBuilder.setRunningMode(runningMode);
            }else if(runningMode == RunningMode.LIVE_STREAM){
                optionsBuilder.setRunningMode(runningMode)
                        .setResultListener(this::returnLivestreamResult)
                        .setErrorListener(this::returnLivestreamError);
            }

            ObjectDetector.ObjectDetectorOptions options = optionsBuilder.build();
            objectDetector = ObjectDetector.createFromOptions(context, options);
        } catch (IllegalStateException e) {
            objectDetectorListener.onError("Object detector failed to initialize. See error logs for details", 0);
            Log.e(TAG, "TFLite failed to load model with error: " + e.getMessage());
        } catch (RuntimeException e) {
            objectDetectorListener.onError(
                    "Object detector failed to initialize. See error logs for " +
                            "details", GPU_ERROR
            );
            Log.e(
                    TAG,
                    "Object detector failed to load model with error: " + e.getMessage()
            );
        }
    }

    public final boolean isClosed() {
        return this.objectDetector == null;
    }


    public final ResultBundle detectVideoFile(Uri videoUri, long inferenceIntervalMs) throws Throwable {

        if (this.runningMode != RunningMode.VIDEO) {
            throw (Throwable)(new IllegalArgumentException("Attempting to call detectVideoFile while not using RunningMode.VIDEO"));
        } else if (this.objectDetector == null) {
            return null;
        } else {
            long startTime = SystemClock.uptimeMillis();
            boolean didErrorOccurred = false;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this.context, videoUri);
            String var10000 = retriever.extractMetadata(9);
            Long var27;
            if (var10000 != null) {
                String var9 = var10000;
                var27 = Long.parseLong(var9);
            } else {
                var27 = null;
            }

            Long videoLengthMs = var27;
            Bitmap firstFrame = retriever.getFrameAtTime(0L);
            Integer width = firstFrame != null ? firstFrame.getWidth() : null;
            Integer height = firstFrame != null ? firstFrame.getHeight() : null;
            if (videoLengthMs != null && width != null && height != null) {
                List resultList = (List)(new ArrayList());
                long numberOfFrameToRead = videoLengthMs / inferenceIntervalMs;
                long i = 0L;
                long var17 = numberOfFrameToRead;
                if (i <= numberOfFrameToRead) {
                    while(true) {
                        long timestampMs = i * inferenceIntervalMs;
                        Bitmap var28 = retriever.getFrameAtTime(timestampMs * (long)1000, MediaMetadataRetriever.OPTION_CLOSEST);
                        boolean var23;
                        if (var28 != null) {
                            Bitmap var21 = var28;
                            var23 = false;
                            Bitmap argb8888Frame = var21.getConfig() == Config.ARGB_8888 ? var21 : var21.copy(Config.ARGB_8888, false);
                            MPImage var25 = (new BitmapImageBuilder(argb8888Frame)).build();
                        } else {
                            ObjectDetectorHelper $this$run = (ObjectDetectorHelper)this;
                            var23 = false;
                            didErrorOccurred = true;
                            DetectorListener var29 = $this$run.objectDetectorListener;
                            if (var29 != null) {
                                DetectorListener.DefaultImpls.onError$default(var29, "Frame at specified time could not be retrieved when detecting in video.", 0, 2, (Object)null);
                            }
                        }

                        if (i == var17) {
                            break;
                        }

                        ++i;
                    }
                }

                retriever.release();
                i = (SystemClock.uptimeMillis() - startTime) / numberOfFrameToRead;
                return didErrorOccurred ? null : new ResultBundle(resultList, i, height, width);
            } else {
                return null;
            }
        }
    }

    @VisibleForTesting
    public final void detectAsync(MPImage mpImage, long frameTime) {
        ObjectDetector detector = this.objectDetector;
        if (detector != null) {
            detector.detectAsync(mpImage, frameTime);
        }

    }

    private final void returnLivestreamResult(ObjectDetectionResult result, MPImage input) {
        long finishTimeMs = SystemClock.uptimeMillis();
        long inferenceTime = finishTimeMs - result.timestampMs();
        DetectorListener detectorListener = this.objectDetectorListener;
        if (detectorListener != null) {
            List<ObjectDetectionResult> list = new ArrayList();
            list.add(result);
            detectorListener.onResults(new ResultBundle(list, inferenceTime, input.getHeight(), input.getWidth()));
        }

    }

    private final void returnLivestreamError(RuntimeException error) {
        DetectorListener detectorListener = this.objectDetectorListener;
        if (detectorListener != null) {
            String message = error.getMessage();
            if (message == null) {
                message = "An unknown error has occurred";
            }
            DetectorListener.DefaultImpls.onError$default(detectorListener, message, 0, 2, (Object)null);
        }
    }


    public final ResultBundle detectImage(Bitmap image){
        if (this.runningMode != RunningMode.IMAGE) {
            return null;
        } else if (this.objectDetector == null) {
            Log.d("tryRess","detector is null");
            return null;
        } else {
            long startTime = SystemClock.uptimeMillis();
            MPImage mpImage = (new BitmapImageBuilder(image)).build();
            ObjectDetector detector = this.objectDetector;
            if (detector != null) {
                ObjectDetectionResult objectDetectionResult = detector.detect(mpImage);
                if (objectDetectionResult != null) {
                    long inferenceTimeMs = SystemClock.uptimeMillis() - startTime;
                    List<ObjectDetectionResult> list = new ArrayList();
                    list.add(objectDetectionResult);
                    return new ResultBundle(list, inferenceTimeMs, image.getHeight(), image.getWidth());
                }
            }

            return null;
        }
    }

    public final float getThreshold() {
        return this.threshold;
    }

    public final void setThreshold(float var1) {
        this.threshold = var1;
    }

    public final int getMaxResults() {
        return this.maxResults;
    }

    public final void setMaxResults(int var1) {
        this.maxResults = var1;
    }

    public final int getCurrentDelegate() {
        return this.currentDelegate;
    }

    public final void setCurrentDelegate(int var1) {
        this.currentDelegate = var1;
    }

    public final String getCurrentModel() {
        return this.modelName;
    }

    public final void setCurrentModel(String var1) {
        this.modelName = var1;
    }


    public final RunningMode getRunningMode() {
        return this.runningMode;
    }

    public final void setRunningMode(RunningMode var1) {
        this.runningMode = var1;
    }


    public final Context getContext() {
        return this.context;
    }


    public final DetectorListener getObjectDetectorListener() {
        return this.objectDetectorListener;
    }

    public final void setObjectDetectorListener(DetectorListener var1) {
        this.objectDetectorListener = var1;
    }

    public ObjectDetectorHelper(float threshold, int maxResults, int currentDelegate, String modelName, RunningMode runningMode, Context context, DetectorListener objectDetectorListener) {
        this.threshold = threshold;
        this.maxResults = maxResults;
        this.currentDelegate = currentDelegate;
        this.modelName = modelName;
        this.runningMode = runningMode;
        this.context = context;
        this.objectDetectorListener = objectDetectorListener;
        this.setupObjectDetector();
    }




    public static final class ResultBundle {

        private final List results;
        private final long inferenceTime;
        private final int inputImageHeight;
        private final int inputImageWidth;


        public final List getResults() {
            return this.results;
        }

        public final long getInferenceTime() {
            return this.inferenceTime;
        }

        public final int getInputImageHeight() {
            return this.inputImageHeight;
        }

        public final int getInputImageWidth() {
            return this.inputImageWidth;
        }

        public ResultBundle(List results, long inferenceTime, int inputImageHeight, int inputImageWidth) {
            super();
            this.results = results;
            this.inferenceTime = inferenceTime;
            this.inputImageHeight = inputImageHeight;
            this.inputImageWidth = inputImageWidth;
        }


        public final ResultBundle copy(List results, long inferenceTime, int inputImageHeight, int inputImageWidth) {
           return new ResultBundle(results, inferenceTime, inputImageHeight, inputImageWidth);
        }

        // $FF: synthetic method
        public static ResultBundle copy$default(ResultBundle var0, List var1, long var2, int var4, int var5, int var6, Object var7) {
            if ((var6 & 1) != 0) {
                var1 = var0.results;
            }

            if ((var6 & 2) != 0) {
                var2 = var0.inferenceTime;
            }

            if ((var6 & 4) != 0) {
                var4 = var0.inputImageHeight;
            }

            if ((var6 & 8) != 0) {
                var5 = var0.inputImageWidth;
            }

            return var0.copy(var1, var2, var4, var5);
        }


        public String toString() {
            return "ResultBundle(results=" + this.results + ", inferenceTime=" + this.inferenceTime + ", inputImageHeight=" + this.inputImageHeight + ", inputImageWidth=" + this.inputImageWidth + ")";
        }

        public int hashCode() {
            List var10000 = this.results;
            return (((var10000 != null ? var10000.hashCode() : 0) * 31 + Long.hashCode(this.inferenceTime)) * 31 + Integer.hashCode(this.inputImageHeight)) * 31 + Integer.hashCode(this.inputImageWidth);
        }

//        public boolean equals(Object var1) {
//            if (this != var1) {
//                if (var1 instanceof ResultBundle) {
//                    ResultBundle var2 = (ResultBundle)var1;
//                    if (Intrinsics.areEqual(this.results, var2.results) && this.inferenceTime == var2.inferenceTime && this.inputImageHeight == var2.inputImageHeight && this.inputImageWidth == var2.inputImageWidth) {
//                        return true;
//                    }
//                }
//
//                return false;
//            } else {
//                return true;
//            }
//        }
    }


    public interface DetectorListener {
        void onError(String var1, int var2);

        void onResults(ResultBundle var1);


        public static final class DefaultImpls {
            // $FF: synthetic method
            public static void onError$default(DetectorListener var0, String var1, int var2, int var3, Object var4) {
                if (var4 != null) {
                    throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: onError");
                } else {
                    if ((var3 & 2) != 0) {
                        var2 = 0;
                    }

                    var0.onError(var1, var2);
                }
            }
        }
    }



}

