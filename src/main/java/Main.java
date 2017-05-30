/*
* @Author: gigaflower
* @Date:   2017-05-11 10:31:50
* @Last Modified by:   zhouben
* @Last Modified time: 2017-05-30 14:44:06
*/

import java.util.List;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;;

import org.opencv.imgproc.Imgproc;
import tapdetect.HandDetector;
import tapdetect.FingerDetector;
import tapdetect.TapDetector;
import tapdetect.ImgLogger;
import tapdetect.Util;
import tapdetect.facade.Tap;

public class Main {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);  // this line only need to be carried out once
    }

    public static void main(String[] args) {
        ImgLogger.setLevel("debug");
        ImgLogger.setBaseDir("record");

//        ImgLoggerTest();
//        HandDetectorTest("../samples/00.jpg");
//        FingerDetectorTest("../samples/00.jpg");
//        TapDetectorTest("../samples/00.jpg");
        Mat im = Imgcodecs.imread("../samples/00.jpg");
        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb);
        List<Point> tap = Tap.getTaps(im);
        System.out.println(tap);
    }

    private static void ImgLoggerTest() {
        Mat im1 = Imgcodecs.imread("../samples/00.jpg");
        Mat im2 = Imgcodecs.imread("../samples/01.jpg");
        ImgLogger.silent();
        ImgLogger.debug("test1.jpg", im1);  // be hidden
        ImgLogger.setLevel(ImgLogger.LOG_LEVEL_DEBUG);
        ImgLogger.info("test2.jpg", im2);   // succeed

        System.out.println("Read image '../samples/00.jpg' sized " + im1.size());
        System.out.println("Read image '../samples/01.jpg' sized " + im2.size());
        System.out.println("Image '../samples/01.jpg' logged to 'ret/test2.jpg");
    }

    private static void HandDetectorTest(String filename) {
        System.out.println("\nTesting HandDetector...");

        Mat im = Imgcodecs.imread(filename);

        System.out.println("Read image '" + filename + "' sized " + im.size());

        if (im.empty()) {
            System.out.println("Error! No image named '" + filename + "'");
            return;
        }

        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb);
        ImgLogger.debug("00_YCrCb.jpg", im);

        // hand detector is not responsible for resizing
        Util.resize(im);

        System.out.println("Image resized to " + im.size());

        HandDetector hd = new HandDetector();
        Mat hand = hd.getHand(im.clone());
        System.out.println("Hand detection result saved");
    }

    private static void FingerDetectorTest(String filename) {
        System.out.println("\nTesting FingerDetector...");
        Mat im = Imgcodecs.imread(filename);

        if (im.empty()) {
            System.out.println("Error! No image named '" + filename + "'");
            return;
        }

        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb);
        ImgLogger.debug("00_YCrCb.jpg", im);
        Util.resize(im);
        System.out.println("Image resized to " + im.size());

        FingerDetector fd = new FingerDetector();
        List<Point> fingers = fd.getFingers(im.clone());

        System.out.println(fingers.size() + " finger tip points found");
    }

    private static void TapDetectorTest(String filename) {
        System.out.println("\nTesting TapDetector...");
        Mat im = Imgcodecs.imread(filename);

        if (im.empty()) {
            System.out.println("Error! No image named '" + filename + "'");
            return;
        }

        Imgproc.cvtColor(im, im, Imgproc.COLOR_BGR2YCrCb);
        ImgLogger.debug("00_YCrCb.jpg", im);
        Util.resize(im);
        System.out.println("Image resized to " + im.size());

        TapDetector td = new TapDetector();
        List<Point> tapping = td.getTapping(im.clone());

        System.out.println(tapping.size() + " tapping points found");
    }

}
