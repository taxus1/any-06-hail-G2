package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 开作业指令请求体（用户接口层，不可变 record）。
 * 指令编号 ZY-yyyy-#### 由后端生成；开单即从该点该弹型该批次结存划走 planRounds 发。
 */
public record FireOrderIssueRequest(
        @NotNull(message = "空域申请 applyId 不能为空")
        Long applyId,

        @NotNull(message = "执行装备 launcherId 不能为空")
        Long launcherId,

        @NotBlank(message = "弹型不能为空，如 BL-1A")
        @Size(max = 32, message = "弹型最长 32 位")
        String ammoType,

        @NotBlank(message = "出弹批次不能为空")
        @Size(max = 32, message = "批次号最长 32 位")
        String batchNo,

        @NotNull(message = "计划用弹发数不能为空")
        @Positive(message = "计划用弹发数必须为正整数")
        Integer planRounds) implements Serializable {
}
