package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作业回报请求体：实际上报打了几发，没打完的后端自动退回结存。
 * 起止时刻可选，不给则结束时刻取当前时间。
 */
public record FireOrderReportRequest(
        @NotNull(message = "实际用弹发数不能为空（一发未打请填 0）")
        @PositiveOrZero(message = "实际用弹发数不能为负数")
        Integer usedRounds,

        LocalDateTime startTime,

        LocalDateTime endTime) implements Serializable {
}
