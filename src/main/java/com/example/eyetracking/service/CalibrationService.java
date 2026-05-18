package com.example.eyetracking.service;

import com.example.eyetracking.model.CalibrationData;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.CalibrationDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 眼动仪校准服务
 *
 * 参考文档：
 * - Tobii Pro 校准原理: https://developer.tobiipro.com/commonconcepts/calibration.html
 * - tobiisdk4j: https://github.com/jeangabin/tobiisdk4j
 *
 * 核心特性：
 * 1. 9点校准算法（符合官方标准）
 * 2. 精度评估（平均误差、最大误差）
 * 3. 失败原因诊断
 * 4. 异常点过滤（RANSAC）
 * 5. 支持不同校准模式
 */
@Service
public class CalibrationService {

    private static final Logger logger = LoggerFactory.getLogger(CalibrationService.class);

    /**
     * 9点校准布局 - 符合Tobii官方推荐的笔记本屏幕校准点位
     * 点位分布：三行三列，覆盖屏幕四角和中心
     */
    private static final int[][] CALIBRATION_POINTS_9 = {
            {50, 50},  // 中心
            {12, 12},  // 左上角
            {50, 12},  // 上中
            {88, 12},  // 右上角
            {12, 50},  // 左中
            {88, 50},  // 右中
            {12, 88},  // 左下角
            {50, 88},  // 下中
            {88, 88}   // 右下角
    };

    private static final int[][] CALIBRATION_POINTS_5 = {
            {50, 50},  // 中心
            {12, 12},  // 左上角
            {88, 12},  // 右上角
            {12, 88},  // 左下角
            {88, 88}   // 右下角
    };

    /**
     * 校准配置参数（参考官方文档）
     */
    private static final int SAMPLES_PER_POINT = 10;           // 每个点采样次数
    private static final int MIN_VALID_SAMPLES_PER_POINT = 4;  // 每个点最小有效样本数
    private static final int MIN_SUCCESSFUL_POINTS_9 = 7;      // 9点校准最小成功点数
    private static final int MIN_SUCCESSFUL_POINTS_5 = 4;      // 5点校准最小成功点数
    private static final long SAMPLE_INTERVAL_MS = 60L;        // 采样间隔（毫秒）
    private static final long SAMPLE_TIMEOUT_MS = 3000L;       // 采样超时时间

    /**
     * 精度评估阈值（像素）
     * 根据官方文档：平均误差 < 80像素为良好，< 40像素为优秀
     */
    private static final double EXCELLENT_THRESHOLD = 40.0;
    private static final double GOOD_THRESHOLD = 80.0;
    private static final double POOR_THRESHOLD = 120.0;

    @Autowired
    private CalibrationDataRepository calibrationDataRepository;

    @Autowired
    private TobiiGlassesService tobiiGlassesService;

    @Autowired
    private CoordinateMapperService coordinateMapperService;

    /**
     * 获取校准点布局信息
     */
    public CalibrationLayout getCalibrationLayout() {
        return getCalibrationLayout(9);
    }

    public CalibrationLayout getCalibrationLayout(int pointCount) {
        int[][] points = calibrationPointsFor(pointCount);
        CalibrationLayout layout = new CalibrationLayout();
        layout.setTotalPoints(points.length);
        layout.setPoints(points);
        layout.setRecommendedDistanceCm(50);  // 推荐距离：50厘米
        layout.setMinDistanceCm(30);          // 最小距离：30厘米
        layout.setMaxDistanceCm(80);          // 最大距离：80厘米
        return layout;
    }

    /**
     * 采集单个校准点数据
     *
     * @param user 用户
     * @param pointIndex 校准点序号（1-9）
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 校准点结果
     */
    public CalibrationPointResult collectCalibrationPoint(User user, int pointIndex, int screenWidth, int screenHeight) {
        return collectCalibrationPoint(user, pointIndex, 9, screenWidth, screenHeight);
    }

    public CalibrationPointResult collectCalibrationPoint(User user, int pointIndex, int pointCount, int screenWidth, int screenHeight) {
        if (user == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        int[][] calibrationPoints = calibrationPointsFor(pointCount);
        if (pointIndex < 1 || pointIndex > calibrationPoints.length) {
            throw new IllegalArgumentException("校准点序号必须在1-" + calibrationPoints.length + "之间");
        }

        int width = sanitizeDimension(screenWidth, 1920);
        int height = sanitizeDimension(screenHeight, 1080);
        int[] target = calibrationPoints[pointIndex - 1];
        double screenX = target[0] / 100.0 * width;
        double screenY = target[1] / 100.0 * height;

        logger.info("[校准] 正在采集校准点 {} ({}, {})", pointIndex, target[0], target[1]);

        List<RawCalibrationSample> validSamples = new ArrayList<>();
        List<RawCalibrationSample> allSamples = new ArrayList<>();

        for (int i = 0; i < SAMPLES_PER_POINT; i++) {
            try {
                Future<TobiiGlassesService.GazeDataSample> future = tobiiGlassesService.getGazeData();
                TobiiGlassesService.GazeDataSample sample = future.get(SAMPLE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                RawCalibrationSample raw = toRawSample(sample);
                allSamples.add(raw);
                if (raw != null) {
                    validSamples.add(raw);
                }
            } catch (Exception e) {
                logger.warn("[校准] 校准点 {} 第 {} 次采样失败: {}", pointIndex, i + 1, e.getMessage());
            }

            if (i < SAMPLES_PER_POINT - 1) {
                sleep(SAMPLE_INTERVAL_MS);
            }
        }

        // 使用中位数滤波（更鲁棒）
        RawCalibrationSample averaged = medianFilter(allSamples);
        boolean pointSuccessful = averaged != null && validSamples.size() >= MIN_VALID_SAMPLES_PER_POINT;

        // 保存校准记录
        CalibrationData record = new CalibrationData();
        record.setUser(user);
        record.setCalibrationTime(LocalDateTime.now());
        record.setStatus(pointSuccessful ? "SUCCESS" : "FAILED");
        record.setPointIndex(pointIndex);
        record.setScreenX(screenX);
        record.setScreenY(screenY);

        if (averaged != null) {
            record.setGazeX(averaged.gazeX);
            record.setGazeY(averaged.gazeY);
            record.setGazeZ(averaged.gazeZ);
            record.setNormalizedGazeX(averaged.normalizedU);
            record.setNormalizedGazeY(averaged.normalizedV);
            record.setLeftPupilDiameter(averaged.leftPupil);
            record.setRightPupilDiameter(averaged.rightPupil);
            record.setError(pixelError(averaged.normalizedU, averaged.normalizedV, screenX, screenY, width, height));
        } else {
            record.setError(Double.MAX_VALUE);
        }

        String notes = String.format("valid=%d/%d, total=%d", validSamples.size(), MIN_VALID_SAMPLES_PER_POINT, allSamples.size());

        // 添加瞳孔直径诊断信息
        if (averaged != null && averaged.leftPupil != null && averaged.leftPupil < 2.0) {
            notes += ", 瞳孔过小(可能光线过强)";
        }
        record.setNotes(notes);
        record = calibrationDataRepository.save(record);

        CalibrationPointResult result = new CalibrationPointResult();
        result.setSuccessful(pointSuccessful);
        result.setCalibrationPointId(record.getId());
        result.setPointIndex(pointIndex);
        result.setValidSamples(validSamples.size());
        result.setRequiredSamples(MIN_VALID_SAMPLES_PER_POINT);
        result.setError(record.getError());

        if (pointSuccessful) {
            result.setMessage(String.format("采样成功 (有效样本: %d/%d)", validSamples.size(), SAMPLES_PER_POINT));
        } else {
            result.setMessage(String.format("采样失败 - 有效样本不足 (%d/%d)", validSamples.size(), MIN_VALID_SAMPLES_PER_POINT));
        }

        return result;
    }

    /**
     * 完成校准并计算映射参数
     *
     * @param user 用户
     * @param calibrationPointIds 校准点记录ID集合
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 校准结果
     */
    public CalibrationResult finishCalibration(User user, Collection<Long> calibrationPointIds, int screenWidth, int screenHeight) {
        return finishCalibration(user, calibrationPointIds, 9, screenWidth, screenHeight);
    }

    public CalibrationResult finishCalibration(User user, Collection<Long> calibrationPointIds, int pointCount, int screenWidth, int screenHeight) {
        if (user == null) {
            throw new IllegalArgumentException("用户不能为空");
        }

        int[][] calibrationPoints = calibrationPointsFor(pointCount);
        int minSuccessfulPoints = minimumSuccessfulPoints(calibrationPoints.length);
        int width = sanitizeDimension(screenWidth, 1920);
        int height = sanitizeDimension(screenHeight, 1080);

        // 获取校准点记录
        List<CalibrationData> records = new ArrayList<>();
        if (calibrationPointIds != null && !calibrationPointIds.isEmpty()) {
            for (CalibrationData data : calibrationDataRepository.findAllById(calibrationPointIds)) {
                if (belongsTo(data, user)) {
                    records.add(data);
                }
            }
        }

        records.sort(Comparator.comparing(CalibrationData::getPointIndex, Comparator.nullsLast(Integer::compareTo)));
        List<CalibrationData> usable = usablePoints(records);

        CalibrationResult result = new CalibrationResult();
        result.setTotalPoints(calibrationPoints.length);
        result.setSuccessfulPoints(usable.size());
        result.setCalibrationPoints(records);

        // 检查有效校准点数量
        if (usable.size() < minSuccessfulPoints) {
            result.setSuccessful(false);
            result.setAverageError(averageStoredError(usable));
            result.setMessage(String.format("有效校准点不足，至少需要 %d 个点，当前只有 %d 个",
                    minSuccessfulPoints, usable.size()));
            result.setQuality(CalibrationQuality.POOR);
            result.setFailureReason(CalibrationFailureReason.INSUFFICIENT_POINTS);
            return result;
        }

        // 拟合仿射变换
        CalibrationFit fit = fitAffine(usable, width, height);
        if (fit == null) {
            result.setSuccessful(false);
            result.setAverageError(averageStoredError(usable));
            result.setMessage("校准点分布不足，无法拟合映射");
            result.setQuality(CalibrationQuality.POOR);
            result.setFailureReason(CalibrationFailureReason.INSUFFICIENT_DISTRIBUTION);
            return result;
        }

        // 异常点过滤（RANSAC风格）
        List<CalibrationData> inliers = removeOutliers(usable, fit, width, height);
        if (inliers.size() >= minSuccessfulPoints && inliers.size() < usable.size()) {
            logger.info("[校准] 移除了 {} 个异常点", usable.size() - inliers.size());
            CalibrationFit refined = fitAffine(inliers, width, height);
            if (refined != null) {
                fit = refined;
                usable = inliers;
            }
        }

        // 计算校正后的误差
        double averageError = updateCorrectedErrors(usable, fit, width, height);
        double maxError = calculateMaxError(usable);

        // 评估校准质量
        CalibrationQuality quality = evaluateQuality(averageError, maxError, width, height);
        boolean successful = quality != CalibrationQuality.POOR;

        result.setSuccessful(successful);
        result.setSuccessfulPoints(usable.size());
        result.setAverageError(round(averageError));
        result.setMaxError(round(maxError));
        result.setCalibrationPoints(records);
        result.setQuality(quality);

        // 设置结果消息
        if (successful) {
            result.setMessage(String.format("校准成功 - 平均误差: %.1fpx, 最大误差: %.1fpx", averageError, maxError));
            // 保存校准参数
            coordinateMapperService.setAffineCalibration(fit.coeffsU, fit.coeffsV);
        } else {
            result.setMessage(String.format("校准失败 - 平均误差: %.1fpx (超过阈值)", averageError));
            result.setFailureReason(CalibrationFailureReason.HIGH_ERROR);
        }

        // 添加改进建议
        result.setImprovementSuggestions(generateSuggestions(quality, averageError, maxError));

        return result;
    }

    /**
     * 完整校准流程（简化接口）
     */
    public CalibrationResult startCalibration(User user, int screenWidth, int screenHeight) {
        logger.info("[校准] 开始完整校准流程");
        List<Long> ids = new ArrayList<>();
        int[][] calibrationPoints = calibrationPointsFor(9);

        for (int i = 1; i <= calibrationPoints.length; i++) {
            CalibrationPointResult pointResult = collectCalibrationPoint(user, i, calibrationPoints.length, screenWidth, screenHeight);
            if (pointResult.getCalibrationPointId() != null) {
                ids.add(pointResult.getCalibrationPointId());
            }
            // 校准点之间的间隔（给用户时间移动视线）
            if (i < calibrationPoints.length) {
                sleep(500);
            }
        }

        return finishCalibration(user, ids, calibrationPoints.length, screenWidth, screenHeight);
    }

    public List<Map<String, Integer>> getCalibrationPointCoordinates(int pointCount) {
        int[][] points = calibrationPointsFor(pointCount);
        List<Map<String, Integer>> coordinates = new ArrayList<>();
        for (int[] point : points) {
            Map<String, Integer> item = new HashMap<>();
            item.put("x", point[0]);
            item.put("y", point[1]);
            coordinates.add(item);
        }
        return coordinates;
    }

    /**
     * 获取用户最近的校准数据
     */
    public List<CalibrationData> getLatestCalibrationData(User user) {
        return calibrationDataRepository.findByUserOrderByCalibrationTimeDesc(user);
    }

    /**
     * 检查校准是否有效（24小时内）
     */
    public boolean isCalibrationValid(User user) {
        List<CalibrationData> recentCalibrations = getLatestCalibrationData(user);
        if (recentCalibrations.isEmpty()) {
            return false;
        }

        CalibrationData latestCalibration = recentCalibrations.get(0);
        LocalDateTime calibrationTime = latestCalibration.getCalibrationTime();
        return calibrationTime != null && calibrationTime.plusHours(24).isAfter(LocalDateTime.now());
    }

    /**
     * 获取校准质量评估
     */
    public CalibrationQuality getCalibrationQuality(User user) {
        List<CalibrationData> recent = getLatestCalibrationData(user);
        if (recent.isEmpty()) {
            return CalibrationQuality.NONE;
        }

        double avgError = averageStoredError(recent);
        double maxError = calculateMaxError(recent);
        int width = 1920; // 默认屏幕宽度

        return evaluateQuality(avgError, maxError, width, 1080);
    }

    /**
     * 将原始数据转换为校准样本
     */
    private RawCalibrationSample toRawSample(TobiiGlassesService.GazeDataSample sample) {
        if (sample == null) {
            return null;
        }

        Double gaze3dX = sample.isGaze3dValid() && isValidGaze3d(sample.getX(), sample.getY(), sample.getZ())
                ? sample.getX() : null;
        Double gaze3dY = gaze3dX == null ? null : sample.getY();
        Double gaze3dZ = gaze3dX == null ? null : sample.getZ();
        Double fallbackU = sample.isGaze2dValid() && isNormalized(sample.getGaze2dX()) ? sample.getGaze2dX() : null;
        Double fallbackV = sample.isGaze2dValid() && isNormalized(sample.getGaze2dY()) ? sample.getGaze2dY() : null;

        if (gaze3dX == null && (fallbackU == null || fallbackV == null)) {
            return null;
        }

        double[] normalized = coordinateMapperService.projectGaze3dToNormalized(
                gaze3dX, gaze3dY, gaze3dZ, fallbackU, fallbackV);

        RawCalibrationSample raw = new RawCalibrationSample();
        raw.normalizedU = normalized[0];
        raw.normalizedV = normalized[1];
        raw.gazeX = gaze3dX;
        raw.gazeY = gaze3dY;
        raw.gazeZ = gaze3dZ;
        raw.leftPupil = finiteOrNull(sample.getLeftPupilDiameter());
        raw.rightPupil = finiteOrNull(sample.getRightPupilDiameter());
        raw.timestamp = sample.getTimestamp();

        return raw;
    }

    /**
     * 中位数滤波（更鲁棒的平均方法）
     */
    private RawCalibrationSample medianFilter(List<RawCalibrationSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return null;
        }

        // 过滤无效样本
        List<RawCalibrationSample> validSamples = new ArrayList<>();
        for (RawCalibrationSample s : samples) {
            if (s != null && isNormalized(s.normalizedU) && isNormalized(s.normalizedV)) {
                validSamples.add(s);
            }
        }

        if (validSamples.isEmpty()) {
            return null;
        }

        // 使用中位数
        RawCalibrationSample result = new RawCalibrationSample();

        // 对U坐标排序取中位数
        validSamples.sort(Comparator.comparingDouble(s -> s.normalizedU));
        result.normalizedU = validSamples.get(validSamples.size() / 2).normalizedU;

        // 对V坐标排序取中位数
        validSamples.sort(Comparator.comparingDouble(s -> s.normalizedV));
        result.normalizedV = validSamples.get(validSamples.size() / 2).normalizedV;

        // 计算3D坐标平均值
        List<RawCalibrationSample> with3d = validSamples.stream()
                .filter(s -> s.gazeX != null)
                .collect(Collectors.toList());

        if (!with3d.isEmpty()) {
            result.gazeX = with3d.stream().mapToDouble(s -> s.gazeX).average().getAsDouble();
            result.gazeY = with3d.stream().mapToDouble(s -> s.gazeY).average().getAsDouble();
            result.gazeZ = with3d.stream().mapToDouble(s -> s.gazeZ).average().getAsDouble();
        }

        // 瞳孔直径
        List<RawCalibrationSample> withLeftPupil = validSamples.stream()
                .filter(s -> s.leftPupil != null)
                .collect(Collectors.toList());
        List<RawCalibrationSample> withRightPupil = validSamples.stream()
                .filter(s -> s.rightPupil != null)
                .collect(Collectors.toList());
        if (!withLeftPupil.isEmpty()) {
            result.leftPupil = withLeftPupil.stream().mapToDouble(s -> s.leftPupil).average().getAsDouble();
        }
        if (!withRightPupil.isEmpty()) {
            result.rightPupil = withRightPupil.stream().mapToDouble(s -> s.rightPupil).average().getAsDouble();
        }

        return result;
    }

    /**
     * 评估校准质量
     */
    private CalibrationQuality evaluateQuality(double averageError, double maxError, int width, int height) {
        double diagonal = Math.sqrt(width * width + height * height);

        // 相对于屏幕对角线的误差比例
        double avgRatio = averageError / diagonal;
        double maxRatio = maxError / diagonal;

        if (avgRatio < 0.02 && maxRatio < 0.04) {
            return CalibrationQuality.EXCELLENT;  // 优秀
        } else if (avgRatio < 0.04 && maxRatio < 0.08) {
            return CalibrationQuality.GOOD;       // 良好
        } else if (avgRatio < 0.06 && maxRatio < 0.12) {
            return CalibrationQuality.ACCEPTABLE; // 可接受
        } else {
            return CalibrationQuality.POOR;       // 差
        }
    }

    /**
     * 生成改进建议
     */
    private List<String> generateSuggestions(CalibrationQuality quality, double averageError, double maxError) {
        List<String> suggestions = new ArrayList<>();

        if (quality == CalibrationQuality.POOR) {
            suggestions.add("请确保距离屏幕约50厘米");
            suggestions.add("保持头部稳定，避免频繁移动");
            suggestions.add("确保光线充足且不直射眼睛");
            suggestions.add("校准期间请专注于每个校准点");
            suggestions.add("尝试重新校准");
        } else if (quality == CalibrationQuality.ACCEPTABLE) {
            suggestions.add("校准精度可接受，如需要更高精度可重新校准");
            suggestions.add("建议距离屏幕45-55厘米");
        } else if (quality == CalibrationQuality.GOOD) {
            suggestions.add("校准质量良好！");
        } else {
            suggestions.add("校准质量优秀！");
        }

        return suggestions;
    }

    /**
     * 计算最大误差
     */
    private double calculateMaxError(List<CalibrationData> points) {
        if (points == null || points.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double max = 0;
        for (CalibrationData point : points) {
            if (point.getError() != null && point.getError() > max) {
                max = point.getError();
            }
        }
        return max;
    }

    /**
     * 获取可用的校准点
     */
    private List<CalibrationData> usablePoints(List<CalibrationData> records) {
        List<CalibrationData> usable = new ArrayList<>();
        for (CalibrationData record : records) {
            if ("SUCCESS".equals(record.getStatus())
                    && record.getNormalizedGazeX() != null
                    && record.getNormalizedGazeY() != null
                    && record.getScreenX() != null
                    && record.getScreenY() != null) {
                usable.add(record);
            }
        }
        return usable;
    }

    /**
     * 拟合仿射变换
     */
    private CalibrationFit fitAffine(List<CalibrationData> points, int width, int height) {
        if (points == null || points.size() < 3) {
            return null;
        }

        double[][] normal = new double[3][3];
        double[] targetU = new double[3];
        double[] targetV = new double[3];

        for (CalibrationData point : points) {
            double rawU = point.getNormalizedGazeX();
            double rawV = point.getNormalizedGazeY();
            double desiredU = clamp(point.getScreenX() / width, 0.0, 1.0);
            double desiredV = clamp(point.getScreenY() / height, 0.0, 1.0);
            double[] row = new double[]{1.0, rawU, rawV};

            for (int r = 0; r < 3; r++) {
                targetU[r] += row[r] * desiredU;
                targetV[r] += row[r] * desiredV;
                for (int c = 0; c < 3; c++) {
                    normal[r][c] += row[r] * row[c];
                }
            }
        }

        double[] coeffsU = solve3x3(normal, targetU);
        double[] coeffsV = solve3x3(normal, targetV);

        if (coeffsU == null || coeffsV == null) {
            return null;
        }

        return new CalibrationFit(coeffsU, coeffsV);
    }

    /**
     * 异常点过滤
     */
    private List<CalibrationData> removeOutliers(List<CalibrationData> points, CalibrationFit fit, int width, int height) {
        List<Double> errors = new ArrayList<>();
        for (CalibrationData point : points) {
            errors.add(correctedError(point, fit, width, height));
        }
        errors.sort(Double::compareTo);

        double median = errors.get(errors.size() / 2);
        double mad = calculateMAD(errors, median);
        double threshold = median + 3.5 * mad;  // 3.5倍MAD作为阈值

        List<CalibrationData> inliers = new ArrayList<>();
        for (CalibrationData point : points) {
            if (correctedError(point, fit, width, height) <= threshold) {
                inliers.add(point);
            }
        }

        return inliers.size() >= minimumSuccessfulPoints(points.size()) ? inliers : points;
    }

    /**
     * 计算中位数绝对偏差（MAD）
     */
    private double calculateMAD(List<Double> errors, double median) {
        List<Double> deviations = new ArrayList<>();
        for (double error : errors) {
            deviations.add(Math.abs(error - median));
        }
        deviations.sort(Double::compareTo);
        return deviations.get(deviations.size() / 2);
    }

    /**
     * 更新校正后的误差
     */
    private double updateCorrectedErrors(List<CalibrationData> points, CalibrationFit fit, int width, int height) {
        double total = 0.0;
        for (CalibrationData point : points) {
            double error = correctedError(point, fit, width, height);
            point.setError(round(error));
            total += error;
        }
        calibrationDataRepository.saveAll(points);
        return points.isEmpty() ? Double.MAX_VALUE : total / points.size();
    }

    /**
     * 计算校正后的误差
     */
    private double correctedError(CalibrationData point, CalibrationFit fit, int width, int height) {
        double rawU = point.getNormalizedGazeX();
        double rawV = point.getNormalizedGazeY();
        double mappedU = fit.coeffsU[0] + fit.coeffsU[1] * rawU + fit.coeffsU[2] * rawV;
        double mappedV = fit.coeffsV[0] + fit.coeffsV[1] * rawU + fit.coeffsV[2] * rawV;
        return pixelError(mappedU, mappedV, point.getScreenX(), point.getScreenY(), width, height);
    }

    /**
     * 计算像素误差
     */
    private double pixelError(double normalizedU, double normalizedV, double screenX, double screenY, int width, int height) {
        double mappedX = clamp(normalizedU, 0.0, 1.0) * width;
        double mappedY = clamp(normalizedV, 0.0, 1.0) * height;
        return Math.sqrt(Math.pow(screenX - mappedX, 2) + Math.pow(screenY - mappedY, 2));
    }

    /**
     * 解3x3线性方程组
     */
    private double[] solve3x3(double[][] normal, double[] target) {
        double[][] a = new double[3][4];
        for (int r = 0; r < 3; r++) {
            System.arraycopy(normal[r], 0, a[r], 0, 3);
            a[r][3] = target[r];
        }

        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(a[pivot][col]) < 1e-9) {
                return null;
            }
            if (pivot != col) {
                double[] tmp = a[col];
                a[col] = a[pivot];
                a[pivot] = tmp;
            }

            double divisor = a[col][col];
            for (int c = col; c < 4; c++) {
                a[col][c] /= divisor;
            }
            for (int row = 0; row < 3; row++) {
                if (row == col) continue;
                double factor = a[row][col];
                for (int c = col; c < 4; c++) {
                    a[row][c] -= factor * a[col][c];
                }
            }
        }

        return new double[]{a[0][3], a[1][3], a[2][3]};
    }

    private double averageStoredError(List<CalibrationData> points) {
        if (points == null || points.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double total = 0.0;
        int count = 0;
        for (CalibrationData point : points) {
            if (point.getError() != null && Double.isFinite(point.getError())) {
                total += point.getError();
                count++;
            }
        }
        return count == 0 ? Double.MAX_VALUE : round(total / count);
    }

    private boolean belongsTo(CalibrationData data, User user) {
        return data != null && data.getUser() != null && data.getUser().getId() != null
                && user != null && data.getUser().getId().equals(user.getId());
    }

    private boolean isValidGaze3d(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(z) >= 1.0 && Math.abs(x) <= 10000.0
                && Math.abs(y) <= 10000.0 && Math.abs(z) <= 20000.0;
    }

    private boolean isNormalized(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private int sanitizeDimension(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private int[][] calibrationPointsFor(int pointCount) {
        return pointCount == 5 ? CALIBRATION_POINTS_5 : CALIBRATION_POINTS_9;
    }

    private int minimumSuccessfulPoints(int pointCount) {
        return pointCount <= 5 ? MIN_SUCCESSFUL_POINTS_5 : MIN_SUCCESSFUL_POINTS_9;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 内部类 ====================

    private static class RawCalibrationSample {
        private double normalizedU;
        private double normalizedV;
        private Double gazeX;
        private Double gazeY;
        private Double gazeZ;
        private Double leftPupil;
        private Double rightPupil;
        private long timestamp;
    }

    private static class CalibrationFit {
        private final double[] coeffsU;
        private final double[] coeffsV;

        private CalibrationFit(double[] coeffsU, double[] coeffsV) {
            this.coeffsU = coeffsU;
            this.coeffsV = coeffsV;
        }
    }

    // ==================== 公共枚举和类 ====================

    /**
     * 校准质量等级
     */
    public enum CalibrationQuality {
        EXCELLENT("优秀", "平均误差 < 2% 屏幕对角线"),
        GOOD("良好", "平均误差 < 4% 屏幕对角线"),
        ACCEPTABLE("可接受", "平均误差 < 6% 屏幕对角线"),
        POOR("差", "平均误差 >= 6% 屏幕对角线"),
        NONE("未校准", "无校准数据");

        private final String label;
        private final String description;

        CalibrationQuality(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    /**
     * 校准失败原因
     */
    public enum CalibrationFailureReason {
        INSUFFICIENT_POINTS("有效校准点不足"),
        INSUFFICIENT_DISTRIBUTION("校准点分布不足"),
        HIGH_ERROR("校准误差过大"),
        NO_DATA("无校准数据");

        private final String description;

        CalibrationFailureReason(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * 校准布局信息
     */
    public static class CalibrationLayout {
        private int totalPoints;
        private int[][] points;
        private int recommendedDistanceCm;
        private int minDistanceCm;
        private int maxDistanceCm;

        public int getTotalPoints() { return totalPoints; }
        public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
        public int[][] getPoints() { return points; }
        public void setPoints(int[][] points) { this.points = points; }
        public int getRecommendedDistanceCm() { return recommendedDistanceCm; }
        public void setRecommendedDistanceCm(int recommendedDistanceCm) { this.recommendedDistanceCm = recommendedDistanceCm; }
        public int getMinDistanceCm() { return minDistanceCm; }
        public void setMinDistanceCm(int minDistanceCm) { this.minDistanceCm = minDistanceCm; }
        public int getMaxDistanceCm() { return maxDistanceCm; }
        public void setMaxDistanceCm(int maxDistanceCm) { this.maxDistanceCm = maxDistanceCm; }
    }

    public static class CalibrationPointResult {
        private boolean successful;
        private Long calibrationPointId;
        private int pointIndex;
        private int validSamples;
        private int requiredSamples;
        private double error;
        private String message;

        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        public Long getCalibrationPointId() { return calibrationPointId; }
        public void setCalibrationPointId(Long calibrationPointId) { this.calibrationPointId = calibrationPointId; }
        public int getPointIndex() { return pointIndex; }
        public void setPointIndex(int pointIndex) { this.pointIndex = pointIndex; }
        public int getValidSamples() { return validSamples; }
        public void setValidSamples(int validSamples) { this.validSamples = validSamples; }
        public int getRequiredSamples() { return requiredSamples; }
        public void setRequiredSamples(int requiredSamples) { this.requiredSamples = requiredSamples; }
        public double getError() { return error; }
        public void setError(double error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class CalibrationResult {
        private boolean successful;
        private double averageError;
        private double maxError;
        private int successfulPoints;
        private int totalPoints;
        private String message;
        private List<CalibrationData> calibrationPoints;
        private CalibrationQuality quality;
        private CalibrationFailureReason failureReason;
        private List<String> improvementSuggestions;

        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        public double getAverageError() { return averageError; }
        public void setAverageError(double averageError) { this.averageError = averageError; }
        public double getMaxError() { return maxError; }
        public void setMaxError(double maxError) { this.maxError = maxError; }
        public int getSuccessfulPoints() { return successfulPoints; }
        public void setSuccessfulPoints(int successfulPoints) { this.successfulPoints = successfulPoints; }
        public int getTotalPoints() { return totalPoints; }
        public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<CalibrationData> getCalibrationPoints() { return calibrationPoints; }
        public void setCalibrationPoints(List<CalibrationData> calibrationPoints) { this.calibrationPoints = calibrationPoints; }
        public CalibrationQuality getQuality() { return quality; }
        public void setQuality(CalibrationQuality quality) { this.quality = quality; }
        public CalibrationFailureReason getFailureReason() { return failureReason; }
        public void setFailureReason(CalibrationFailureReason failureReason) { this.failureReason = failureReason; }
        public List<String> getImprovementSuggestions() { return improvementSuggestions; }
        public void setImprovementSuggestions(List<String> improvementSuggestions) { this.improvementSuggestions = improvementSuggestions; }
    }
}
