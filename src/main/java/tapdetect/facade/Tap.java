/*
* @Author: zhouben
* @Date:   2017-05-10 22:47:18
* @Last Modified by:   zhouben
* @Last Modified time: 2017-06-14 17:03:14
*/

package tapdetect.facade;

import java.util.List;
import java.util.ArrayList;
// import java.util.stream.Collectors;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.Video;

import tapdetect.Config;
import tapdetect.FingerDetector;
import tapdetect.ForegroundDetector;
import tapdetect.HandDetector;
import tapdetect.ImgLogger;
import tapdetect.TapDetector;
import tapdetect.Util;

public class Tap {
    static {
        System.loadLibrary("opencv_java3");  // this line only need to be carried out once
        ImgLogger.silent();
    }

    private static final HandDetector hd = new HandDetector();
    private static final FingerDetector fd = new FingerDetector();
    private static final TapDetector td = new TapDetector();
    private static final ForegroundDetector fgd = new ForegroundDetector();

    private static long lastProcess = 0;
    private static long processInterval;
    private static List<List<Point>> resultCache = new ArrayList<>();

    static {
        for (int i = 0; i < 5; ++i) {
            resultCache.add(new ArrayList<Point>());
        }
    }

    public static long getProcessInterval() {
        return processInterval;
    }

    public static boolean readyForNextFrame() {
        return System.currentTimeMillis() - lastProcess > Config.PROCESS_INTERVAL_MS;
    }


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


    public static List<Point> getTaps(Mat im) {
        // TODO: add throttle

        // resize to the standard size
        double recover_ratio = 1.0 / Util.resize(im);
        Mat fgmask = fgd.getForeground(im);

        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb);
        Mat hand = hd.getHand(im, fgmask);
        List<Point> fingers = fd.getFingers(im, hand);
        List<Point> taps = td.getTapping(im, fingers);

        for (Point pt : taps) {
            pt.x *= recover_ratio;
            pt.y *= recover_ratio;
        }

        return taps;
        // return taps.stream().map(pt -> new Point((int) (pt.x / recover_ratio), (int) (pt.y / recover_ratio)))
        //         .collect(Collectors.toList());
    }

    public static List<List<Point>> getAllForDebug(Mat im) {
        /*
            e.g.
                List<List<Point>> ret = getAllForDebug(im);
                ret[0]   // contour points of detected hand
                ret[1]   // detected fingers
                ret[2]   // detected points which is 'falling down'
                ret[3]   // detected points where tap happens
        */

        long t = System.currentTimeMillis();
        if (t - lastProcess < Config.PROCESS_INTERVAL_MS) {
            // too higher the camera fps
            return resultCache;
        } else {
            processInterval = t - lastProcess;
            lastProcess = t;
        }

        double recover_ratio = 1.0 / Util.resize(im);
        Mat fgmask = fgd.getForeground(im);

        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb);
        Mat hand = hd.getHand(im, fgmask);

        List<MatOfPoint> hand_contour = Util.largeContours(hand, Config.HAND_AREA_MIN);
        List<Point> hand_contour_pt = Util.contoursToPoints(hand_contour);

        List<Point> fingers = fd.getFingers(im, hand);

        List<TapDetector.TapDetectPoint> taps = td.getTappingAll(im, fingers);

        for (Point pt : hand_contour_pt) {
            pt.x *= recover_ratio;
            pt.y *= recover_ratio;
        }
        for (Point pt : fingers) {
            pt.x *= recover_ratio;
            pt.y *= recover_ratio;
        }
        for (TapDetector.TapDetectPoint pt : taps) {
            pt.getPoint().x *= recover_ratio;
            pt.getPoint().y *= recover_ratio;
        }


        // no need to shrink points in taps because Point in taps and Point in finger have same reference

        resultCache.clear();
        resultCache.add(hand_contour_pt);
        resultCache.add(fingers);
        resultCache.add(new ArrayList<Point>());
        resultCache.add(new ArrayList<Point>());
        resultCache.add(new ArrayList<Point>());
        for (TapDetector.TapDetectPoint p : taps) {
            if (p.isFalling()) {
                resultCache.get(2).add(p.getPoint());
            } else if (p.isLingering()) {
                resultCache.get(3).add(p.getPoint());
            } else if (p.isTapping()) {
                resultCache.get(4).add(p.getPoint());
            }
        }
        return resultCache;
    }
}
