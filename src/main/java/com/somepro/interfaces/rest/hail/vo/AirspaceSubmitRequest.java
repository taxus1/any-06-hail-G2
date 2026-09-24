package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 空域申报请求体（用户接口层，不可变 record）。
 * 编号由后端按 KQ-yyyy-#### 生成，不由调用方传。
 */
public record AirspaceSubmitRequest(
        @NotNull(message = "作业点 siteId 不能为空")
        Long siteId,

        @NotBlank(message = "作业目的不能为空：HAIL 防雹 / RAIN 增雨")
        String purpose,

        @NotNull(message = "拟作业开始时刻不能为空")
        LocalDateTime planStart,

        @NotNull(message = "拟作业结束时刻不能为空")
        LocalDateTime planEnd,

        @PositiveOrZero(message = "请求高度不能为负数")
        Integer maxAltitude,

        @Size(max = 255, message = "备注最长 255 位")
        String remark) implements Serializable {
}
