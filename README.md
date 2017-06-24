# tap-detect-java

Image processing algorithm used to detect fingers' tapping action (like playing the paino)

Based on skin-color-based detection and moving detection using `opencv`

Can be used on both andorid and PC

# Usage

Using `facade/Tap.java` for everthing

> sampling

In order to adapt to various light environment, we need to sample some pixels before detection.
The sample window is somthing like `docs/sampleWindow.jpg`, only pixels in this window and have color near
skin color will sampled.
    
    org.opencv.core.Mat im = org.opencv.imgcodecs.Imgcodecs.imread("sample.jpg");
    tapdetect.facade.Tap.sample(im);

    System.out.println(Arrays.toString(tapdetect.Sampler.getAver())); // average color of sampled pixels
    System.out.println(tapdetect.Sampler.getRatio())); // percentage of passed pixels among all sampled pixels

    if (tapdetect.facade.Tap.sampleCompleted()) {
        .....
    }

At least 5 images are needed for sampling to complete.
Thresholds can be modified in `Config.java`

![sampling demo](https://github.com/gigaflw/tap-detect-java/raw/master/snapshots/sample.jpg)
(snapshot from ![PaperMelody App](https://github.com/hgs1217/Paper-Melody), an android app using this algorithm)

> detection

After sampling, now we can carry out detection
    
    import org.opencv.core.Point;
    import tapdetect.TapDetector.TapDetectPoint;

    List<List<Point>> contoursOutput;   // the contours of all detected skin color area for debug
    List<TapDetectPoint> tapDetectPointsOutput;  // all detected result for debug

    List<Point> taps = Tap.getAll(nextFrame.clone(), contoursOutput, detectedPointsOuput);

    for (Point p: taps) {
        System.out.println("A tapping happens at position (" + p.x + ", " + p.y + ")");
    }

    // for debug use
    for (TapDetectPoint p: detectedPointsOuput) {
        if (p.isFalling()) {
            // ....
        } else if (p.isTapping()) {
            // ....
        } else if (p.isPressing()) {
            // ....
        } else if (p.isLingering()) {
            // ....
        } else {
            // ....
        }
    }

![detection demo](https://github.com/gigaflw/tap-detect-java/raw/master/snapshots/detection.jpg)
(snapshot from ![PaperMelody App](https://github.com/hgs1217/Paper-Melody), an android app using this algorithm)
