package cn.modificator.launcher.autorefresh;

/**
 * Directional flick state machine based on CrossPoint Reader's tilt page turn.
 *
 * Gyroscope input follows the original 270 dps trigger, 50 dps neutral and
 * 600 ms cooldown behavior. Devices without a gyroscope estimate angular
 * velocity from changes in the accelerometer gravity vector.
 */
final class DirectionalTiltDetector {

  static final int NONE = 0;
  static final int FORWARD = 1;
  static final int BACKWARD = -1;

  private static final long WAKE_STABILIZE_NS = 300_000_000L;
  private static final long COOLDOWN_NS = 600_000_000L;
  private static final long MIN_ACCEL_SAMPLE_NS = 40_000_000L;
  private static final long MAX_ACCEL_SAMPLE_NS = 250_000_000L;
  private static final float GYRO_NEUTRAL_DPS = 50f;

  private boolean inTilt;
  private long wakeTimestamp;
  private long lastTiltTimestamp;
  private boolean accelerometerAngleInitialized;
  private long lastAccelerometerTimestamp;
  private float lastAngleX;
  private float lastAngleY;

  int addGyroscopeSample(long timestamp, float gxRadians, float gyRadians,
      int rotation, float triggerThresholdDps) {
    float gxDps = (float) Math.toDegrees(gxRadians);
    float gyDps = (float) Math.toDegrees(gyRadians);
    return process(timestamp, mapTiltAxis(gxDps, gyDps, rotation),
        triggerThresholdDps, GYRO_NEUTRAL_DPS);
  }

  int addAccelerometerSample(long timestamp, float x, float y, float z,
      int rotation, float triggerThresholdDps) {
    float angleX = (float) Math.atan2(y, z);
    float angleY = (float) Math.atan2(-x, z);
    if (!accelerometerAngleInitialized) {
      accelerometerAngleInitialized = true;
      lastAccelerometerTimestamp = timestamp;
      lastAngleX = angleX;
      lastAngleY = angleY;
      ensureWakeTimestamp(timestamp);
      return NONE;
    }

    long elapsedNs = timestamp - lastAccelerometerTimestamp;
    if (elapsedNs > 0 && elapsedNs < MIN_ACCEL_SAMPLE_NS) return NONE;
    float deltaX = unwrapAngle(angleX - lastAngleX);
    float deltaY = unwrapAngle(angleY - lastAngleY);
    lastAccelerometerTimestamp = timestamp;
    lastAngleX = angleX;
    lastAngleY = angleY;
    if (elapsedNs <= 0 || elapsedNs > MAX_ACCEL_SAMPLE_NS) return NONE;

    float seconds = elapsedNs / 1_000_000_000f;
    float rateX = (float) Math.toDegrees(deltaX) / seconds;
    float rateY = (float) Math.toDegrees(deltaY) / seconds;
    return process(timestamp, mapTiltAxis(rateX, rateY, rotation),
        triggerThresholdDps, GYRO_NEUTRAL_DPS);
  }

  void reset() {
    inTilt = false;
    wakeTimestamp = 0;
    lastTiltTimestamp = 0;
    accelerometerAngleInitialized = false;
    lastAccelerometerTimestamp = 0;
    lastAngleX = 0;
    lastAngleY = 0;
  }

  private int process(long timestamp, float tiltAxis, float triggerThreshold,
      float neutralThreshold) {
    ensureWakeTimestamp(timestamp);
    if (timestamp - wakeTimestamp < WAKE_STABILIZE_NS) return NONE;

    if (inTilt) {
      if (Math.abs(tiltAxis) < neutralThreshold) {
        inTilt = false;
      }
      return NONE;
    }
    if (timestamp - lastTiltTimestamp < COOLDOWN_NS) return NONE;

    if (tiltAxis > triggerThreshold) {
      inTilt = true;
      lastTiltTimestamp = timestamp;
      return FORWARD;
    }
    if (tiltAxis < -triggerThreshold) {
      inTilt = true;
      lastTiltTimestamp = timestamp;
      return BACKWARD;
    }
    return NONE;
  }

  private void ensureWakeTimestamp(long timestamp) {
    if (wakeTimestamp == 0) {
      wakeTimestamp = timestamp;
    }
  }

  private float mapTiltAxis(float x, float y, int rotation) {
    switch (rotation) {
      case 1:
        return -y;
      case 2:
        return -x;
      case 3:
        return y;
      default:
        return x;
    }
  }

  private float unwrapAngle(float angle) {
    while (angle > Math.PI) angle -= (float) (Math.PI * 2);
    while (angle < -Math.PI) angle += (float) (Math.PI * 2);
    return angle;
  }
}
