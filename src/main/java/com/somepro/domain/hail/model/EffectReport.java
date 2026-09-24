package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作业效果上报聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 EffectReportPO）。
 *
 * 口径：一条作业指令一份效果报告（库表 uk_order 唯一）。
 * - 过程降雨量 rainfallMm：毫米，给了不能为负；
 * - 最大冰雹粒径 hailSizeMm：毫米，给了不能为负；
 * - 影响面积 areaKm2：平方公里，给了不能为负。
 * 三项指标都允许为空（过程无降雨 / 无雹时可不填），但至少上报一项，避免交白卷。
 */
@Getter
@Setter
public class EffectReport extends BaseEntity {

    private Long id;

    /** 作业指令 id（t_fire_order.id），一条指令一份。 */
    private Long orderId;

    /** 上报时刻。 */
    private LocalDateTime reportTime;

    /** 过程降雨量（毫米）。 */
    private BigDecimal rainfallMm;

    /** 最大冰雹粒径（毫米）。 */
    private BigDecimal hailSizeMm;

    /** 影响面积（平方公里）。 */
    private BigDecimal areaKm2;

    /** 备注。 */
    private String remark;

    /** 工厂方法：为一条作业指令上报效果，集中保证初始不变量。 */
    public static EffectReport report(Long orderId, LocalDateTime reportTime,
                                      BigDecimal rainfallMm, BigDecimal hailSizeMm,
                                      BigDecimal areaKm2, String remark) {
        EffectReport report = new EffectReport();
        report.bindOrder(orderId);
        report.recordRainfall(rainfallMm);
        report.recordHailSize(hailSizeMm);
        report.recordArea(areaKm2);
        if (report.rainfallMm == null && report.hailSizeMm == null && report.areaKm2 == null) {
            throw new BizException("效果上报至少要填一项：降雨量 / 冰雹粒径 / 影响面积");
        }
        report.reportTime = reportTime == null ? LocalDateTime.now() : reportTime;
        report.remark = trimToNull(remark);
        return report;
    }

    /** 领域行为：报告必须挂在一条作业指令上。 */
    public void bindOrder(Long orderId) {
        if (orderId == null) {
            throw new BizException("效果上报必须关联作业指令，orderId 不能为空");
        }
        this.orderId = orderId;
    }

    /** 领域行为：降雨量（毫米）为可选项，给了不能为负。 */
    public void recordRainfall(BigDecimal rainfallMm) {
        if (rainfallMm != null && rainfallMm.signum() < 0) {
            throw new BizException("过程降雨量不能为负数（毫米）");
        }
        this.rainfallMm = rainfallMm;
    }

    /** 领域行为：冰雹粒径（毫米）为可选项，给了不能为负。 */
    public void recordHailSize(BigDecimal hailSizeMm) {
        if (hailSizeMm != null && hailSizeMm.signum() < 0) {
            throw new BizException("最大冰雹粒径不能为负数（毫米）");
        }
        this.hailSizeMm = hailSizeMm;
    }

    /** 领域行为：影响面积（平方公里）为可选项，给了不能为负。 */
    public void recordArea(BigDecimal areaKm2) {
        if (areaKm2 != null && areaKm2.signum() < 0) {
            throw new BizException("影响面积不能为负数（平方公里）");
        }
        this.areaKm2 = areaKm2;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
