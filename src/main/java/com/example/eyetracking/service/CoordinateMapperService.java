package com.example.eyetracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CoordinateMapperService {
    private static final Logger logger = LoggerFactory.getLogger(CoordinateMapperService.class);

    private int screenWidth;
    private int screenHeight;

    private double calibrationOffsetU = 0.0;
    private double calibrationOffsetV = 0.0;
    private double calibrationScaleU = 1.0;
    private double calibrationScaleV = 1.0;

    private double[] polyCoeffsU;
    private double[] polyCoeffsV;
    private boolean usePolynomial = false;

    private double[] homographyMatrix;
    private boolean useHomography = false;

    private boolean isCalibrated = false;

    private double[] smoothLastU;
    private double[] smoothLastV;
    private boolean useKalmanFilter = true;

    public CoordinateMapperService() {
        this.screenWidth = 1920;
        this.screenHeight = 1080;
        this.polyCoeffsU = new double[10];
        this.polyCoeffsV = new double[10];
        this.smoothLastU = new double[]{0.0, 0.0};
        this.smoothLastV = new double[]{0.0, 0.0};
    }

    public CoordinateMapperService(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.polyCoeffsU = new double[10];
        this.polyCoeffsV = new double[10];
        this.smoothLastU = new double[]{0.0, 0.0};
        this.smoothLastV = new double[]{0.0, 0.0};
    }

    public ScreenCoordinate processGazeData(double u, double v) {
        double calibratedU = u;
        double calibratedV = v;

        if (isCalibrated && usePolynomial && polyCoeffsU != null && polyCoeffsV != null) {
            calibratedU = evaluatePolynomial(polyCoeffsU, u, v);
            calibratedV = evaluatePolynomial(polyCoeffsV, u, v);
        } else if (isCalibrated) {
            calibratedU = u * calibrationScaleU + calibrationOffsetU;
            calibratedV = v * calibrationScaleV + calibrationOffsetV;
        }

        if (useKalmanFilter) {
            double[] smoothed = applyKalmanFilter(calibratedU, calibratedV);
            calibratedU = smoothed[0];
            calibratedV = smoothed[1];
        }

        int screenX = (int) (calibratedU * screenWidth);
        int screenY = (int) (calibratedV * screenHeight);

        screenX = Math.max(0, Math.min(screenWidth, screenX));
        screenY = Math.max(0, Math.min(screenHeight, screenY));

        return new ScreenCoordinate(screenX, screenY, calibratedU, calibratedV);
    }

    private double[] applyKalmanFilter(double u, double v) {
        double alpha = 0.7;

        if (smoothLastU[0] == 0.0 && smoothLastV[0] == 0.0) {
            smoothLastU[0] = u;
            smoothLastV[0] = v;
        }

        smoothLastU[0] = alpha * u + (1 - alpha) * smoothLastU[0];
        smoothLastV[0] = alpha * v + (1 - alpha) * smoothLastV[0];

        return new double[]{smoothLastU[0], smoothLastV[0]};
    }

    private double evaluatePolynomial(double[] coeffs, double u, double v) {
        if (coeffs == null || coeffs.length < 10) {
            return u;
        }

        return coeffs[0] +
               coeffs[1] * u +
               coeffs[2] * v +
               coeffs[3] * u * u +
               coeffs[4] * u * v +
               coeffs[5] * v * v +
               coeffs[6] * u * u * u +
               coeffs[7] * u * u * v +
               coeffs[8] * u * v * v +
               coeffs[9] * v * v * v;
    }

    public void setCalibrationData(double offsetU, double offsetV, double scaleU, double scaleV) {
        this.calibrationOffsetU = offsetU;
        this.calibrationOffsetV = offsetV;
        this.calibrationScaleU = scaleU;
        this.calibrationScaleV = scaleV;
        this.isCalibrated = true;
        logger.info("Calibration data set: offsetU={}, offsetV={}, scaleU={}, scaleV={}",
                    offsetU, offsetV, scaleU, scaleV);
    }

    public void setPolynomialCalibration(double[] coeffsU, double[] coeffsV) {
        if (coeffsU != null && coeffsU.length >= 10) {
            this.polyCoeffsU = coeffsU;
        }
        if (coeffsV != null && coeffsV.length >= 10) {
            this.polyCoeffsV = coeffsV;
        }
        this.usePolynomial = true;
        this.isCalibrated = true;
        logger.info("Polynomial calibration enabled");
    }

    public void setHomographyMatrix(double[] matrix) {
        if (matrix != null && matrix.length == 9) {
            this.homographyMatrix = matrix;
            this.useHomography = true;
            this.isCalibrated = true;
            logger.info("Homography matrix set");
        }
    }

    public void clearCalibration() {
        this.isCalibrated = false;
        this.usePolynomial = false;
        this.useHomography = false;
        this.calibrationOffsetU = 0.0;
        this.calibrationOffsetV = 0.0;
        this.calibrationScaleU = 1.0;
        this.calibrationScaleV = 1.0;
        this.polyCoeffsU = new double[10];
        this.polyCoeffsV = new double[10];
        this.homographyMatrix = null;
        logger.info("Calibration cleared");
    }

    public boolean isCalibrated() {
        return isCalibrated;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    public boolean isUseKalmanFilter() {
        return useKalmanFilter;
    }

    public void setUseKalmanFilter(boolean useKalmanFilter) {
        this.useKalmanFilter = useKalmanFilter;
    }

    public static class ScreenCoordinate {
        private final int x;
        private final int y;
        private final double normalizedU;
        private final double normalizedV;

        public ScreenCoordinate(int x, int y, double normalizedU, double normalizedV) {
            this.x = x;
            this.y = y;
            this.normalizedU = normalizedU;
            this.normalizedV = normalizedV;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public double getNormalizedU() {
            return normalizedU;
        }

        public double getNormalizedV() {
            return normalizedV;
        }

        @Override
        public String toString() {
            return String.format("ScreenCoordinate(x=%d, y=%d, u=%.3f, v=%.3f)", x, y, normalizedU, normalizedV);
        }
    }
}
