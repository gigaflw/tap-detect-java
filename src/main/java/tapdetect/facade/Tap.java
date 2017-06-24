/*
* @Author: zhouben
* @Date:   2017-05-10 22:47:18
* @Last Modified by:   zhouben
* @Last Modified time: 2017-06-23 23:18:48
*/

package tapdetect.facade;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import tapdetect.ColorRange;
import tapdetect.Config;
import tapdetect.FingerDetector;
import tapdetect.HandDetector;
import tapdetect.ImgLogger;
import tapdetect.Sampler;
import tapdetect.TapDetector;
import tapdetect.TapDetector.TapDetectPoint;
import tapdetect.Util;

// import java.util.stream.Collectors;

public class Tap {
    /**
     * Facade for outside to use the tap detector
     *
     * @param im: a Image in the type of org.opencv.core.Mat
     *            required to be in <strong>YCrCb</strong> color mode!
     * @return : a <code>List</code> of <code>Points</code>
     * which takes the left top point as (0, 0) point
     * <p>
     * <br> Usage:
     * <code>
     * <br>  Mat im = Imgcodecs.imread("samples/foo.jpg");
     * <br>  List<Point> taps = Tap.getTaps(im);
     * <br>  Point pt = taps.get(0);
     * <br>  System.out.println("Hurrah, someone taps at (" + pt.x + ", " + pt.y + ")");
     * </code>
     */

    static {
        // with opencv java, use Core.NATIVE_LIBRARY_NAME,
        // with opencv android, use "opencv_java3"
        // System.loadLibrary(Core.NATIVE_LIBRARY_NAME);  // this line only need to be carried out once
        // ImgLogger.silent(); // should be silent on mobile phones
    }

    public static long getProcessInterval() {
        return processInterval;
    }

    public static boolean readyForNextFrame() {
        return System.currentTimeMillis() - lastProcess > Config.PROCESS_INTERVAL_MS;
    }

    public static void reset() {
        ColorRange.reset();
    }

    public static boolean sampleCompleted() {
        /**
         * Once this returns `True`, sampling process should be completed,
         * sampling function will not be called anymore.
         * However you can call `Tap.sample` manually if you really want
         */
        return Sampler.sampleCompleted();
    }


    public static List<Point> getTaps(Mat im) {
        /**
         * Searching tapping points from `im`
         * This is the most used func in this facade
         * @param im: one frame from a video
         * @return : A list of `Point` indicating the points which
         *  (1) is regarded as the finger tip
         *  (2) is regarded as being tapping
         */
        if (!preprocess(im)) {
            return resultCache;
        }

        Mat hand = HandDetector.getHand(im);
        List<Point> fingers = FingerDetector.getFingers(im, hand);
        List<Point> taps = TapDetector.getTapping(im, fingers);

        scaleResult(taps);
        updateResultCache(taps);

        return taps;
    }

    public static List<Point> getPress(Mat im) {
        /**
         * Searching pressing finger tips from `im`
         * @param im: one frame from a video
         * @return : A list of `Point` indicating the points which
         *  (1) is regarded as the finger tip
         *  (2) is regarded as being pressing
         *
         *  @warning: do not use getPress and getTaps in a row for the sake of performance.
         *      Use `getAll` to get every finger tips instead
         */
        if (!preprocess(im)) {
            return resultCache;
        }

        Mat hand = HandDetector.getHand(im);
        List<Point> fingers = FingerDetector.getFingers(im, hand);
        List<Point> press = TapDetector.getPressing(im, fingers);

        scaleResult(press);
        updateResultCache(press);

        return press;
    }

    public static List<Point> getAll(Mat im,
                                     List<List<Point>> contoursOutput,
                                     List<TapDetectPoint> tapDetectPointsOutput
    ) {
        /**
         * @param: im: A image in color space BGR
         * @param: contoursOutput
         *      if is not null, apexes of the contour of hand will be saved
         * @param: tapDetectPointsOutput
         *      if is not null, all results of detected points will be saved
         * @retrun:
         *      A list of points detected as being tapping
         *      (nothing but `TapDetectPoint` with status `FALLING` in `tapDetectPointsOutput`)
         *  This function will modify `im` into YCrCb as well as a smaller size
         */

        if (!preprocess(im)) {
            return resultCache;
        }
        Mat hand = HandDetector.getHand(im);

        List<MatOfPoint> contour = new ArrayList<>();
        List<Point> fingers = FingerDetector.getFingers(im, hand, contour);
        List<TapDetectPoint> taps = TapDetector.getTappingAll(im, fingers);

        if (contoursOutput != null) {
            contoursOutput.clear();
            for (MatOfPoint cnt : contour) {
                List<Point> cntPt = cnt.toList();
                scaleResult(cntPt);
                contoursOutput.add(cntPt);
            }
        }

        for (TapDetectPoint pt : taps) {
            pt.x *= recoverRatio;
            pt.y *= recoverRatio;
        }

        if (contoursOutput != null) {
            tapDetectPointsOutput.clear();
            tapDetectPointsOutput.addAll(taps);
        }

        List<Point> ret = new ArrayList<>();

        for (TapDetectPoint pt : taps) {
            if (pt.isTapping()) {
                ret.add(pt);
            }
        }
        updateResultCache(ret);

        return ret;
    }

    public static List<Point> getPressAll(Mat im,
                                          List<List<Point>> contoursOutput,
                                          List<TapDetectPoint> tapDetectPointsOutput
    ) {
        /**
         * Same with `getAll` but returns a list of `pressing` points
         */

        if (!preprocess(im)) {
            return resultCache;
        }
        Mat hand = HandDetector.getHand(im);

        List<MatOfPoint> contour = new ArrayList<>();
        List<Point> fingers = FingerDetector.getFingers(im, hand, contour);
        List<TapDetectPoint> taps = TapDetector.getTappingAll(im, fingers);

        if (contoursOutput != null) {
            contoursOutput.clear();
            for (MatOfPoint cnt : contour) {
                List<Point> cntPt = cnt.toList();
                scaleResult(cntPt);
                contoursOutput.add(cntPt);
            }
        }

        for (TapDetectPoint pt : taps) {
            pt.x *= recoverRatio;
            pt.y *= recoverRatio;
        }

        if (contoursOutput != null) {
            tapDetectPointsOutput.clear();
            tapDetectPointsOutput.addAll(taps);
        }

        List<Point> ret = new ArrayList<>();

        for (TapDetectPoint pt : taps) {
            if (pt.isPressing()) {
                ret.add(pt);
            }
        }
        updateResultCache(ret);

        return ret;
    }


    public static List<Point> getSampleWindowContour() {
        if (sampleWindowContour == null && recoverRatio > 0.0) {
            sampleWindowContour = new ArrayList<>();
            for (Point p : Sampler.getSampleWindowContour()) {
                sampleWindowContour.add(new Point(p.x * recoverRatio, p.y * recoverRatio));
            }
        }
        return sampleWindowContour;
    }

    private static boolean checkTime() {
        long t = System.currentTimeMillis();
        if (t - lastProcess < Config.PROCESS_INTERVAL_MS) {
            // too higher the camera fps
            return false;
        } else {
            processInterval = t - lastProcess;
            lastProcess = t;
            return true;
        }
    }

    private static boolean sample(Mat mat) {
        /**
         * Sampling pixels from mat to get a appropriate hand color
         * This will be called automatically before tapping detection being carried out
         */
        if (!Sampler.isInited()) {
            Sampler.initSampleMask(mat.height(), mat.width());
        }
        Sampler.sample(mat);
        return Sampler.sampleCompleted();
    }

    private static boolean preprocess(Mat im) {
        // check time
        if (!checkTime()) {
            return false;
        }

        // resize to the standard size
        recoverRatio = 1.0 / Util.resize(im);

        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb);
        Imgproc.blur(im, im, new Size(Config.IM_BLUR_SIZE, Config.IM_BLUR_SIZE));

        if (!Sampler.sampleCompleted()) {
            sample(im);
            return false;
        }
        return true;
    }

    private static void scaleResult(List<Point> result) {
        for (Point pt : result) {
            pt.x *= recoverRatio;
            pt.y *= recoverRatio;
        }
    }

    private static void updateResultCache(List<Point> result) {
        resultCache.clear();
        for (Point pt : result) {
            resultCache.add(pt);
        }
    }

    private static double recoverRatio = 0.0;
    private static long lastProcess = 0;
    private static long processInterval;
    private static List<Point> resultCache = new ArrayList<>();
    private static List<Point> sampleWindowContour = null;


    // Configs
    public static void setHighPerformance(boolean highPerformance) {
        if (highPerformance) {
            Config.PROCESS_INTERVAL_MS = 50;
        } else {
            Config.PROCESS_INTERVAL_MS = 100;
        }
    }

    public static void setMotionSensibility(int motionSensibility) {
        switch (motionSensibility) {
            case 0:
                // sensitive to small move
                Config.FINGER_TIP_MOVE_DIST_MAX = 20;
                Config.FINGER_TIP_LINGER_DIST_MAX = 1;
                break;
            case 1:
                Config.FINGER_TIP_MOVE_DIST_MAX = 25;
                Config.FINGER_TIP_LINGER_DIST_MAX = 2;
                break;
            case 2:
                // sensitive to big move
                Config.FINGER_TIP_MOVE_DIST_MAX = 35;
                Config.FINGER_TIP_LINGER_DIST_MAX = 4;
                break;
        }
    }

    public static void setSkinColorSensibility(int colorSensibility) {
        switch (colorSensibility) {
            case 0:
                // a small range of color is considered as skin color
                Config.COLOR_RANGE_EXPAND[0] = 2;
                Config.COLOR_RANGE_EXPAND[1] = 1.4;
                Config.COLOR_RANGE_EXPAND[2] = 2;
                break;
            case 1:
                Config.COLOR_RANGE_EXPAND[0] = 3;
                Config.COLOR_RANGE_EXPAND[1] = 1.6;
                Config.COLOR_RANGE_EXPAND[2] = 3;
                break;
            case 2:
                // a large range of color is considered as skin color
                Config.COLOR_RANGE_EXPAND[0] = 3;
                Config.COLOR_RANGE_EXPAND[1] = 1.8;
                Config.COLOR_RANGE_EXPAND[2] = 3;
                break;
        }
    }

    public static void setCalibritionColorSensibility(int calibritionColorSensibility) {
        switch (calibritionColorSensibility) {
            case 0:
                // have a coarse sampling
                Config.SAMPLE_PASS_THRESHOLD = 0.75;
                Config.FINGER_COLOR_TOLERANCE[1] = 25;
                Config.FINGER_COLOR_TOLERANCE[2] = 25;
                break;
            case 1:
                Config.SAMPLE_PASS_THRESHOLD = 0.85;
                Config.FINGER_COLOR_TOLERANCE[1] = 17;
                Config.FINGER_COLOR_TOLERANCE[2] = 20;
                break;
            case 2:
                // have a precise sampling
                Config.SAMPLE_PASS_THRESHOLD = 0.9;
                Config.FINGER_COLOR_TOLERANCE[1] = 15;
                Config.FINGER_COLOR_TOLERANCE[2] = 15;
                break;
        }
    }
}
