/*
* @Author: zhouben
* @Date:   2017-06-10 23:12:42
* @Last Modified by:   zhouben
* @Last Modified time: 2017-06-16 16:29:56
*/

package tapdetect;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import java.util.List;

public class ColorRange {
    private static double[][] range =
            {Config.FINGER_COLOR_RANGE[0].clone(), Config.FINGER_COLOR_RANGE[1].clone()};
    private static double[] center = Config.FINGER_COLOR.clone();
    private static int updatedCnt = 1;

    public static void reset() {
        range[0] = Config.FINGER_COLOR_RANGE[0].clone();
        range[1] = Config.FINGER_COLOR_RANGE[1].clone();
        center = Config.FINGER_COLOR.clone();
        updatedCnt = 1;
    }

    public static Scalar[] getRange() {
        // double[][] ret = new double[2][3];
        // for (int i = 0; i < 2; ++i) {
        //     for (int j = 0; j < 3; ++j) {
        //         ret[i][j] = Math.round(range[i][j]);
        //     }
        // }
        // return new Scalar[]{new Scalar(ret[0]), new Scalar(ret[1])};
        return new Scalar[]{new Scalar(range[0]), new Scalar(range[1])};
    }

    public static double[] getCenter() {
        return center;
    }

    public static int getUpdatedCnt() {
        return updatedCnt;
    }

    public static void updateRange(Mat im, List<Point> movingPixels) {
        if (movingPixels.isEmpty()) {
            return;
        }
        double[] aver = {0, 0, 0};
        double[] std = {0, 0, 0};

        // calc stat values
        int n = movingPixels.size();
        for (Point point : movingPixels) {
            double[] val = im.get((int) point.y, (int) point.x);
            for (int i = 0; i < 3; ++i) {
                aver[i] += val[i];
                std[i] += val[i] * val[i];
            }
        }
        for (int i = 0; i < 3; ++i) {
            aver[i] /= n;
            std[i] = Math.sqrt(std[i] / n - aver[i] * aver[i]);
        }
        // std[0] *= 3;
        // std[1] *= 2;
        // std[2] *= 2;  // YCrCb, Y channel has a larger range

        // System.out.println("n: " + n);
        // System.out.println("aver: " + new Scalar(aver));
        // System.out.println("std: " + new Scalar(std));

        // calc new color range
        double[][] newRange = new double[2][3];
        for (int i = 0; i < 3; ++i) {
            newRange[0][i] = Math.max(aver[i] - std[i], 0);
            newRange[1][i] = Math.min(aver[i] + std[i], 255.0);

            range[0][i] += (newRange[0][i] - range[0][i]) / updatedCnt;
            range[1][i] += (newRange[1][i] - range[1][i]) / updatedCnt;
            center[i] = (range[0][i] + range[1][i]) / 2;
        }

        updatedCnt += 1;
    }
}
