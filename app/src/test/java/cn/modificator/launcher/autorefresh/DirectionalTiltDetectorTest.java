package cn.modificator.launcher.autorefresh;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DirectionalTiltDetectorTest {

  private static final long MS = 1_000_000L;

  @Test
  public void gyroscopeRequiresNeutralAndCooldownBeforeNextGesture() {
    DirectionalTiltDetector detector = new DirectionalTiltDetector();
    long start = 1_000 * MS;

    assertEquals(DirectionalTiltDetector.NONE,
        detector.addGyroscopeSample(start, 0f, 0f, 0, 270f));
    assertEquals(DirectionalTiltDetector.FORWARD,
        detector.addGyroscopeSample(start + 310 * MS, radians(300f), 0f, 0, 270f));
    assertEquals(DirectionalTiltDetector.NONE,
        detector.addGyroscopeSample(start + 330 * MS, radians(320f), 0f, 0, 270f));
    assertEquals(DirectionalTiltDetector.NONE,
        detector.addGyroscopeSample(start + 400 * MS, radians(20f), 0f, 0, 270f));
    assertEquals(DirectionalTiltDetector.NONE,
        detector.addGyroscopeSample(start + 700 * MS, radians(-300f), 0f, 0, 270f));
    assertEquals(DirectionalTiltDetector.BACKWARD,
        detector.addGyroscopeSample(start + 920 * MS, radians(-300f), 0f, 0, 270f));
  }

  @Test
  public void landscapeClockwiseUsesNegativeYAxis() {
    DirectionalTiltDetector detector = new DirectionalTiltDetector();
    long start = 2_000 * MS;

    detector.addGyroscopeSample(start, 0f, 0f, 1, 270f);
    assertEquals(DirectionalTiltDetector.BACKWARD,
        detector.addGyroscopeSample(start + 310 * MS, 0f, radians(300f), 1, 270f));
  }

  @Test
  public void accelerometerFallbackUsesSameDirectionalStateMachine() {
    DirectionalTiltDetector detector = new DirectionalTiltDetector();
    long start = 3_000 * MS;

    detector.addAccelerometerSample(start, 0f, 0f, 9.8f, 0, 270f);
    detector.addAccelerometerSample(start + 310 * MS, 0f, 0f, 9.8f, 0, 270f);
    assertEquals(DirectionalTiltDetector.NONE,
        detector.addAccelerometerSample(start + 320 * MS, 0f, 3f, 9.33f, 0, 270f));
    assertEquals(DirectionalTiltDetector.FORWARD,
        detector.addAccelerometerSample(start + 360 * MS, 0f, 3f, 9.33f, 0, 270f));

    detector.addAccelerometerSample(start + 410 * MS, 0f, 0f, 9.8f, 0, 270f);
    detector.addAccelerometerSample(start + 460 * MS, 0f, 0f, 9.8f, 0, 270f);
    detector.addAccelerometerSample(start + 920 * MS, 0f, 0f, 9.8f, 0, 270f);

    assertEquals(DirectionalTiltDetector.BACKWARD,
        detector.addAccelerometerSample(start + 970 * MS, 0f, -3f, 9.33f, 0, 270f));
  }

  private static float radians(float degrees) {
    return (float) Math.toRadians(degrees);
  }
}
