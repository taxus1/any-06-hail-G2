package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 效果上报请求体（用户接口层，不可变 record）。
 * 降雨量 rainfallMm / 冰雹粒径 hailSizeMm 按毫米填，影响面积 areaKm2 按平方公里填；
 * 三项都允许为空，但后端要求至少填一项。reportTime 不给则取当前时间。
 */
public record EffectReportRequest(
        @NotNull(message = "作业指令 orderId 不能为空")
        Long orderId,

        LocalDateTime reportTime,

        @DecimalMin(value = "0.0", message = "过程降雨量不能为负数（毫米）")
        BigDecimal rainfallMm,

        @DecimalMin(value = "0.0", message = "最大冰雹粒径不能为负数（毫米）")
        BigDecimal hailSizeMm,

        @DecimalMin(value = "0.0", message = "影响面积不能为负数（平方公里）")
        BigDecimal areaKm2,

        @Size(max = 255, message = "备注最长 255 位")
        String remark) implements Serializable {
}
