package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 空域申请聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 AirspaceApplyPO）。
 *
 * 关键不变量与状态规则：
 * - 申请编号（applyNo，如 KQ-2026-0101）全局唯一且非空，库表 uk_apply_no 兜底。
 * - 必须挂在某个真实作业点（siteId 非空，存在性由应用层查库确认，含早先数据）。
 * - 目的只能是 {@link FirePurpose} 的 HAIL 防雹 / RAIN 增雨。
 * - 拟作业时段 planStart &lt; planEnd；请求高度给了就不能为负。
 * - 新报上来一律 {@link AirspaceStatus#PENDING}；批复只能 PENDING → APPROVED / REJECTED。
 *   驳回必须写明原因；批复后不可再改。
 * - 只有 APPROVED 的空域才能开作业指令（由作业指令用例校验）。
 */
@Getter
@Setter
public class AirspaceApply extends BaseEntity {

    private Long id;

    /** 申请编号，全局唯一，如 KQ-2026-0101。 */
    private String applyNo;

    /** 作业点 id（t_operation_site.id），必填。 */
    private Long siteId;

    /** HAIL 防雹 / RAIN 增雨。 */
    private FirePurpose purpose;

    /** 拟作业开始时刻。 */
    private LocalDateTime planStart;

    /** 拟作业结束时刻。 */
    private LocalDateTime planEnd;

    /** 请求高度上限（米）。 */
    private Integer maxAltitude;

    /** PENDING 待批 / APPROVED 已批 / REJECTED 已驳 / CANCELLED 已撤 / EXPIRED 已失效。 */
    private AirspaceStatus status;

    /** 驳回原因。 */
    private String rejectReason;

    /** 批复时刻。 */
    private LocalDateTime approveTime;

    /**
     * 工厂方法：新报一条空域申请，集中保证初始不变量，初始状态 PENDING。
     *
     * <p>编号 applyNo 由仓储按「KQ-年份-序号」生成（取号要查库 + 撞号重试），
     * 故不进工厂，落库前由仓储 {@code changeNo} 回填。
     */
    public static AirspaceApply submit(Long siteId, String purposeRaw,
                                       LocalDateTime planStart, LocalDateTime planEnd,
                                       Integer maxAltitude) {
        AirspaceApply apply = new AirspaceApply();
        apply.assignTo(siteId);
        apply.changePurpose(purposeRaw);
        apply.planWindow(planStart, planEnd);
        apply.changeMaxAltitude(maxAltitude);
        apply.status = AirspaceStatus.PENDING;
        return apply;
    }

    /** 领域行为：改编号并校验非空（唯一性由仓储 + 唯一索引保证）。 */
    public void changeNo(String applyNo) {
        if (applyNo == null || applyNo.isBlank()) {
            throw new BizException("空域申请编号不能为空");
        }
        this.applyNo = applyNo.trim();
    }

    /** 领域行为：申请必须挂在某个作业点上。 */
    public void assignTo(Long siteId) {
        if (siteId == null) {
            throw new BizException("空域申请必须归属一个作业点，siteId 不能为空");
        }
        this.siteId = siteId;
    }

    /** 领域行为：目的只能是防雹或增雨。 */
    public void changePurpose(String purposeRaw) {
        this.purpose = FirePurpose.of(purposeRaw);
    }

    /** 领域行为：拟作业时段两端必填，且开始必须早于结束。 */
    public void planWindow(LocalDateTime planStart, LocalDateTime planEnd) {
        if (planStart == null || planEnd == null) {
            throw new BizException("拟作业开始、结束时刻都不能为空");
        }
        if (!planEnd.isAfter(planStart)) {
            throw new BizException("拟作业结束时刻必须晚于开始时刻");
        }
        this.planStart = planStart;
        this.planEnd = planEnd;
    }

    /** 领域行为：请求高度为可选项，给了就不能为负。 */
    public void changeMaxAltitude(Integer maxAltitude) {
        if (maxAltitude != null && maxAltitude < 0) {
            throw new BizException("请求高度不能为负数");
        }
        this.maxAltitude = maxAltitude;
    }

    /** 领域行为：批复通过。只有待批状态可批，批过 / 驳过的不再受理。 */
    public void approve(LocalDateTime approveTime) {
        if (this.status != AirspaceStatus.PENDING) {
            throw new BizException("只有待批空域才能批复通过，当前状态：" + this.status);
        }
        this.status = AirspaceStatus.APPROVED;
        this.rejectReason = null;
        this.approveTime = approveTime == null ? LocalDateTime.now() : approveTime;
    }

    /** 领域行为：驳回。必须写明原因，且只有待批状态可驳。 */
    public void reject(String reason, LocalDateTime approveTime) {
        if (this.status != AirspaceStatus.PENDING) {
            throw new BizException("只有待批空域才能驳回，当前状态：" + this.status);
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("驳回空域必须写明驳回原因");
        }
        this.status = AirspaceStatus.REJECTED;
        this.rejectReason = reason.trim();
        this.approveTime = approveTime == null ? LocalDateTime.now() : approveTime;
    }

    /** 空域是否已获批，作业指令开单前置条件。 */
    public boolean isApproved() {
        return this.status == AirspaceStatus.APPROVED;
    }
}
