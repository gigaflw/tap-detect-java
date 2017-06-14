/*
* @Author: zhouben
* @Date:   2017-05-10 09:14:53
* @Last Modified by:   zhouben
* @Last Modified time: 2017-06-14 16:03:01
*/

package tapdetect;

import java.util.List;
import java.util.ArrayList;
// import java.util.stream.Collectors;

import org.opencv.core.CvType;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class FingerDetector {
    public List<Point> getFingers(Mat im) {
        HandDetector hd = new HandDetector();
        Mat hand = hd.getHand(im.clone());
        return getFingers(im, hand);
    }

    public List<Point> getFingers(Mat im, Mat hand) {
        assert im.size().height == Config.IM_HEIGHT;

        List<MatOfPoint> contours = Util.largeContours(hand, Config.HAND_AREA_MIN);
        if (contours.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Point> fingerTips = new ArrayList<>();
        for (int i=0; i<contours.size(); ++i) {
            MatOfPoint cnt = contours.get(i);

            double epsilon = 5;
            MatOfPoint2f approx = new MatOfPoint2f(), cntCvt = new MatOfPoint2f();

            cnt.convertTo(cntCvt, CvType.CV_32FC2);
            Imgproc.approxPolyDP(cntCvt, approx, epsilon, true);
            approx.convertTo(cnt, CvType.CV_32S);

            fingerTips.addAll(this.findFingerTips(approx.toList(), hand));
        }
        Mat handWithContour = Util.drawContours(im, contours, new Scalar(0, 0, 255));
        ImgLogger.info("11_contour.jpg", handWithContour);

        Mat handWithFingerTips = Util.drawPoints(handWithContour, fingerTips, new Scalar(255, 0, 0));
        ImgLogger.info("12_finger_tips.jpg", handWithFingerTips);
        handWithFingerTips.assignTo(im);  // so that the caller can have the `im` with contour and fingertip painted

        return fingerTips;
    }


    private List<Point> findFingerTips(List<Point> contour, Mat hand) {
        int len = contour.size();

        Point[] diff_n = new Point[len];
        Point[] diff_p = new Point[len];
        double[] tan_n = new double[len];
        double[] tan_p = new double[len];
        double[] cosine = new double[len];
        boolean[] isConvex = new boolean[len];

        for (int i = 0; i < len; ++i) {
            int next_i = (i == len - 1) ? 0 : (i + 1);
            int prev_i = (i == 0) ? (len - 1) : (i - 1);

            Point p = contour.get(i);
            Point next = contour.get(next_i);
            Point prev = contour.get(prev_i);

            isConvex[i] = hand.get((int) p.y - 1, (int) p.x)[0] > 0;
            diff_n[i] = new Point(next.x - p.x, next.y - p.y);
            diff_p[i] = new Point(prev.x - p.x, prev.y - p.y);

            if (!isConvex[i]) {
                continue;
            }

            tan_n[i] = diff_n[i].y / diff_n[i].x; // maybe infinity
            tan_p[i] = diff_p[i].y / diff_p[i].x; // maybe infinity
            cosine[i] = Util.intersectCos(p, prev, next);
        }

        List<Point> ret = new ArrayList<>();
        for (int i = 0; i < len; ++i) {
            if (!isConvex[i]) {
                continue;
            }

            int next_i = (i == len - 1) ? 0 : (i + 1);
            Point p = contour.get(i);
            Point next = contour.get(next_i);

            boolean isLowestLocal = diff_p[i].y <= 0 && diff_n[i].y <= 0;
            boolean goodAngle = Math.abs(tan_n[i]) > 0.2 && Math.abs(tan_p[i]) > 0.2 && cosine[i] < 0.8;

            boolean isLowestPair = diff_p[i].y <= 0 && diff_n[next_i].y <= 0;

            boolean isFlat = Math.abs(tan_n[i]) < 0.5;
            double distRatio =
                    Math.sqrt(diff_n[i].x * diff_n[i].x + diff_n[i].y * diff_n[i].y)
                            / (double) Config.FINGER_TIP_WIDTH;
            boolean goodDist = distRatio < 3 && distRatio > 0.3;
            boolean isColumn = isConvex[i] && isConvex[next_i] && isLowestPair && isFlat && goodDist;
            boolean isCorner = isConvex[i] && isLowestLocal && goodAngle;

            if (isCorner) {
                ret.add(p.clone());
            }
            if (isColumn) {
                ret.add(new Point((p.x + next.x) / 2.0, (p.y + next.y) / 2.0));
            }
        }
        return ret;
    }


    private List<Point> findFingerTipsOld2(List<Point> contour, Mat hand) {
        int len = contour.size();

        List<Point> ret = new ArrayList<>();
        List<Point> cache = new ArrayList<>();
        double cacheY = 0.0;
        double lastY = contour.get(0).y;

        for (int i = 2; i < len; ++i) {
            Point pt = contour.get(i);

            if (cache.isEmpty()) { cacheY = pt.y; }

            if (pt.y == cacheY) {
                cache.add(pt);
            } else {

                if (cacheY >= lastY && cacheY >= pt.y                // 1. is local lowest point
                    && cache.size() < Config.FINGER_TIP_WIDTH           // 2. not too long
                    ) {

                    Point aver = Util.averPoint(cache);
                    if (hand.get((int) aver.y - 1, (int) aver.x)[0] > 0){ // 3. is lower bound
                        ret.add(aver);
                    }
                }
                cache.clear();
                lastY = cacheY;
                i -= 1;
            }
        }

        return ret;
    }

    private List<Point> findFingerTipsOld(MatOfPoint contour, Mat hand) {
        List<Point> contourPt = contour.toList();
        int step = Config.FINGER_TIP_STEP;
        int len = contourPt.size();

        ArrayList<Integer> fingerTipsInd = new ArrayList<>();

        for (int i = 0; i < len; ++i) {
            Point pt = contourPt.get(i);

            Point ahead = contourPt.get((i + step) % len);
            Point behind = contourPt.get((i < step) ? (i - step) % len + len : (i - step) % len);

            int centerX = (int) (ahead.x + behind.x + pt.x) / 3;
            int centerY = (int) (ahead.y + behind.y + pt.y) / 3;

            if (centerY > pt.y) {
                continue;
            }

            if ((int) hand.get(centerY, centerX)[0] == 0) {
                continue;
            }

            double cos = Util.intersectCos(contourPt.get(i), ahead, behind);

            if (cos > 0.7) {
                continue;
            }

            fingerTipsInd.add(i);
        }

        List<Integer> fingerTipsIndSeparate = this.mergeNeighbors(fingerTipsInd, Config.FINGER_TIP_WIDTH);

        // FIXME: can't use stream until android sdk 24
        // return fingerTipsIndSeparate.stream().map(contour_pt::get).collect(Collectors.toList());
        List<Point> ret = new ArrayList<>();
        for (Integer ind: fingerTipsIndSeparate) { ret.add(contourPt.get(ind)); }
        return ret;
    }

    private List<Integer> mergeNeighbors(List<Integer> inds, int tolerance) {
        List<Integer> ret = new ArrayList<>();
        List<Integer> series = new ArrayList<>();

        for (int ind : inds) {
            if (series.isEmpty() || Math.abs(ind - series.get(series.size() - 1)) < tolerance) {
                series.add(ind);
            } else {
                // ret.add(series.stream().reduce(0, (x, y) -> x + y) / series.size());
                ret.add((int) aver(series));
                series.clear();
            }
        }
        if (!series.isEmpty()) {
            // ret.add(series.stream().reduce(0, (x, y) -> x + y) / series.size());
            ret.add((int) aver(series));
        }
        return ret;
    }
    
    private double aver(List<Integer> lst) {
         double sum = 0.0;
         for (int val: lst) { sum += val; }
         return sum / lst.size();
    }
}