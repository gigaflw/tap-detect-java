/*
* @Author: zhouben
* @Date:   2017-05-10 09:14:53
* @Last Modified by:   zhouben
* @Last Modified time: 2017-06-15 11:17:29
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
    private final HandDetector hd = new HandDetector();

    public List<Point> getFingers(Mat im) {
        Mat hand = hd.getHand(im);
        return getFingers(im, hand, null);
    }

    public List<Point> getFingers(Mat im, Mat hand) {
        return getFingers(im, hand, null);
    }

    public List<Point> getFingers(Mat im, List<MatOfPoint> contourOutput) {
        Mat hand = hd.getHand(im);
        return getFingers(im, hand, contourOutput);
    }

    public List<Point> getFingers(Mat im, Mat hand, List<MatOfPoint> contourOutput) {
        /**
         * @param: im: A YCrCb image with same shape as `hand`
         * @param: hand: A binary image indicating which pixel is part of hand
         * @param: contourOutput:
         *      If is not null, contours will be saved for debug
         * @return: A list of points indicating the detected finger tip points
         *      This function will not change `in` or `hand`
         */

        // assert im.size().height == Config.IM_HEIGHT;
        // assert im.size().height == hand.size().height

        List<MatOfPoint> contours = Util.largeContours(hand, Config.HAND_AREA_MIN);
        if (contours.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Point> fingerTips = new ArrayList<>();

        for (int i=0; i<contours.size(); ++i) {
            // apply polygon approximation
            MatOfPoint cnt = contours.get(i);

            double epsilon = 5;
            MatOfPoint2f approx = new MatOfPoint2f(), cntCvt = new MatOfPoint2f();

            cnt.convertTo(cntCvt, CvType.CV_32FC2);
            Imgproc.approxPolyDP(cntCvt, approx, epsilon, true);
            approx.convertTo(cnt, CvType.CV_32S);

            // apply polygon approximation
            fingerTips.addAll(this.findFingerTips(approx.toList(), hand));
        }

        if (contourOutput != null) {
            contourOutput.clear();
            contourOutput.addAll(contours);
        }

        return fingerTips;
    }

    private List<Point> findFingerTips(List<Point> contour, Mat hand) {
        /**
         * @param: contour: A list of the apex of the contour
         * @param: hand: A binary image indicating which pixel is part of hand
         */
        int len = contour.size();

        Point[] diff_n = new Point[len];  // vector_this_pt_to_next
        Point[] diff_p = new Point[len];  // vector_this_pt_to_prev, = -diff_n[i - 1], preserved for convenience
        double[] dist = new double[len]; // |<vector_this_pt_to_next>|

        for (int i = 0; i < len; ++i) {
            int next_i = (i == len - 1) ? 0 : (i + 1);
            int prev_i = (i == 0) ? (len - 1) : (i - 1);

            Point p = contour.get(i), next = contour.get(next_i), prev = contour.get(prev_i);

            diff_n[i] = new Point(next.x - p.x, next.y - p.y);
            diff_p[i] = new Point(prev.x - p.x, prev.y - p.y);
            dist[i] = Math.sqrt(diff_n[i].x * diff_n[i].x + diff_n[i].y * diff_n[i].y);
        }

        boolean[] isConvex = new boolean[len];
        double[] cos = new double[len]; // [0, 1], cos(<vector_this_pt_to_next>)
        double[] tan = new double[len]; // (-inf, +inf), tan(<vector_this_pt_to_next>)
        // tan:  | -1
        //       |
        // 0 ----+----> row
        //       |
        //  -1   |  1
        //       v col
        for (int i = 0; i < len; ++i) {
            int next_i = (i == len - 1) ? 0 : (i + 1);
            int prev_i = (i == 0) ? (len - 1) : (i - 1);

            Point p = contour.get(i), next = contour.get(next_i), prev = contour.get(prev_i);

            isConvex[i] = isConvexPoint(p, prev, next, hand);

            if (isConvex[i]) {
                tan[i] = diff_n[i].y / diff_n[i].x; // maybe infinity
                cos[i] = Util.intersectCos(p, prev, next);
            } // otherwise skip the calculation
        }

        List<Point> ret = new ArrayList<>();
        for (int i = 0; i < len; ++i) {
            if (!isConvex[i]) {
                continue;
            }

            int next_i = (i == len - 1) ? 0 : (i + 1);

            boolean isLowestLocal = diff_p[i].y < 0 && diff_n[i].y < 0;
            boolean goodAngle = cos[i] < 0.8;

            boolean isLowestPair = diff_p[i].y <= 0 && diff_n[next_i].y <= 0;

            boolean isFlat = Math.abs(tan[i]) < 0.5;
            double distRatio = dist[i] / (double) Config.FINGER_TIP_WIDTH;
            boolean goodDist = distRatio < 2 && distRatio > 0.5;
            boolean isColumn = isConvex[i] && isConvex[next_i] && isLowestPair && isFlat && goodDist;
            boolean isCorner = isConvex[i] && isLowestLocal && goodAngle;

            Point p = contour.get(i);
            if (isCorner) {
                ret.add(p.clone());
            }
            if (isColumn) {
                ret.add(new Point(
                        (p.x + contour.get(next_i).x) / 2.0,
                        (p.y + contour.get(next_i).y) / 2.0)
                );
            }
        }
        return ret;
    }

    private boolean isConvexPoint(Point p, Point prev, Point next, Mat hand) {
        Point center = Util.incenter(p, prev, next);
        double tan_normal = (center.y - p.y) / (center.x - p.x); // maybe infinity

        // 2.414 = tan(67.5), 0.414 = tan(22.5), one-eighth of 360
        int dx = (Math.abs(tan_normal) > 2.414) ? 0 : (center.x > p.x ? 1 : -1);
        int dy = (Math.abs(tan_normal) < 0.414) ? 0 : (center.y > p.y ? 1 : -1);

        return center.y < p.y && hand.get((int) p.y + dy, (int) p.x + dx)[0] > 0;
        // hard to have 100% precision since of the holes in `hand`
    }

    @Deprecated
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

    @Deprecated
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

    @Deprecated
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