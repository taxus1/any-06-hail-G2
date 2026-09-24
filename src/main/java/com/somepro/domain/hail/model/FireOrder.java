package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 作业指令聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 FireOrderPO）。
 *
 * 关键不变量与流程：
 * - 指令编号（orderNo，如 ZY-2026-0101）全局唯一且非空，库表 uk_order_no 兜底。
 * - 一条指令必须挂在一份<b>已获批</b>的空域申请上（applyId），归到某个作业点；
 *   申请 APPROVED 是开单的硬前置，由应用层查库确认。
 * - 执行装备、弹型、计划发数必填；计划发数必须为正。
 * - 开单即从该点该弹型该批次结存里划走 planRounds 发（账上记一笔 OUT 领用），
 *   库存不足拒绝开单。扣减与流水在仓储里同事务落库。
 * - 打完回报实际发数 usedRounds：0 &le; usedRounds &le; planRounds；
 *   没打完的 planRounds - usedRounds 发退回结存（账上记一笔 RETURN），指令置 DONE。
 */
@Getter
@Setter
public class FireOrder extends BaseEntity {

    private Long id;

    /** 指令编号，全局唯一，如 ZY-2026-0101。 */
    private String orderNo;

    /** 空域申请 id（t_airspace_apply.id），必须是已获批的申请。 */
    private Long applyId;

    /** 作业点 id（t_operation_site.id）。 */
    private Long siteId;

    /** 执行装备 id（t_launcher.id）。 */
    private Long launcherId;

    /** 弹型。 */
    private String ammoType;

    /** 计划用弹发数（开单时从结存划走的数量）。 */
    private Integer planRounds;

    /** 实际用弹发数（回报后填）。 */
    private Integer usedRounds;

    /** ISSUED 已下达 / EXECUTING 作业中 / DONE 已完成 / VOID 已作废。 */
    private FireOrderStatus status;

    /** 实际作业开始时刻。 */
    private LocalDateTime startTime;

    /** 实际作业结束时刻。 */
    private LocalDateTime endTime;

    /** 作废原因。 */
    private String voidReason;

    /**
     * 工厂方法：下达一条新作业指令，集中保证初始不变量，初始状态 ISSUED、实际发数 0。
     *
     * <p>编号 orderNo 由仓储按「ZY-年份-序号」生成（取号要查库 + 撞号重试），
     * 故不进工厂，落库前由仓储 {@code changeNo} 回填。批次不落在本表，开单时交给仓储记账。
     *
     * @param applyId    已获批空域申请 id
     * @param siteId     作业点 id（取自空域申请，冗余到指令上方便记账）
     * @param launcherId 执行装备 id
     * @param ammoType   弹型
     * @param planRounds 计划打几发（开单即划走）
     */
    public static FireOrder issue(Long applyId, Long siteId, Long launcherId,
                                  String ammoType, int planRounds) {
        FireOrder order = new FireOrder();
        order.bindApply(applyId);
        order.assignTo(siteId);
        order.useLauncher(launcherId);
        order.specifyAmmo(ammoType);
        order.changePlanRounds(planRounds);
        order.usedRounds = 0;
        order.status = FireOrderStatus.ISSUED;
        return order;
    }

    /** 领域行为：改编号并校验非空（唯一性由仓储 + 唯一索引保证）。 */
    public void changeNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException("作业指令编号不能为空");
        }
        this.orderNo = orderNo.trim();
    }

    /** 领域行为：指令必须挂在一份空域申请上。 */
    public void bindApply(Long applyId) {
        if (applyId == null) {
            throw new BizException("作业指令必须关联空域申请，applyId 不能为空");
        }
        this.applyId = applyId;
    }

    /** 领域行为：指令必须归到某个作业点。 */
    public void assignTo(Long siteId) {
        if (siteId == null) {
            throw new BizException("作业指令必须归属一个作业点，siteId 不能为空");
        }
        this.siteId = siteId;
    }

    /** 领域行为：必须指定执行装备。 */
    public void useLauncher(Long launcherId) {
        if (launcherId == null) {
            throw new BizException("作业指令必须指定执行装备，launcherId 不能为空");
        }
        this.launcherId = launcherId;
    }

    /** 领域行为：必须写明弹型。 */
    public void specifyAmmo(String ammoType) {
        if (ammoType == null || ammoType.isBlank()) {
            throw new BizException("作业指令必须写明弹型，如 BL-1A");
        }
        this.ammoType = ammoType.trim();
    }

    /** 领域行为：计划发数必须为正。 */
    public void changePlanRounds(int planRounds) {
        if (planRounds <= 0) {
            throw new BizException("计划用弹发数必须为正整数");
        }
        this.planRounds = planRounds;
    }

    /** 领域行为：进入作业中。 */
    public void markExecuting(LocalDateTime startTime) {
        if (this.status != FireOrderStatus.ISSUED) {
            throw new BizException("只有已下达指令才能开始作业，当前状态：" + this.status);
        }
        this.status = FireOrderStatus.EXECUTING;
        this.startTime = startTime == null ? LocalDateTime.now() : startTime;
    }

    /**
     * 领域行为：打完回报实际发数，指令收尾为 DONE。
     *
     * @param usedRounds 实际打了几发，0 表示一发未打（全退）
     * @return 需要退回结存的发数（计划 - 实际，&ge; 0），由应用层 / 仓储记一笔 RETURN
     */
    public int reportResult(int usedRounds, LocalDateTime startTime, LocalDateTime endTime) {
        if (this.status != FireOrderStatus.ISSUED && this.status != FireOrderStatus.EXECUTING) {
            throw new BizException("只有已下达 / 作业中的指令才能回报，当前状态：" + this.status);
        }
        if (usedRounds < 0) {
            throw new BizException("实际用弹发数不能为负数");
        }
        if (usedRounds > this.planRounds) {
            throw new BizException("实际用弹发数不能超过计划发数：实际 " + usedRounds
                    + " / 计划 " + this.planRounds);
        }
        this.usedRounds = usedRounds;
        this.status = FireOrderStatus.DONE;
        this.startTime = startTime != null ? startTime : this.startTime;
        this.endTime = endTime == null ? LocalDateTime.now() : endTime;
        return this.planRounds - usedRounds;
    }
}
