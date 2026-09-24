package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 弹药出入库流水实体（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 AmmoRecordPO）。
 *
 * 口径（见建表 SQL 注释）：changeQty 正入负出 ——
 * - IN 入库：正数；OUT 领用：负数（开作业指令时划走）；
 * - RETURN 退回：正数（没打完的退回结存）；SCRAP 报废：负数。
 * refNo 记关联单据：入库单号或作业指令编号（ZY-...）。
 *
 * 流水只追加、不改写：每一次库存变动都落一条，账实可追溯。
 */
@Getter
@Setter
public class AmmoRecord extends BaseEntity {

    private Long id;

    /** 作业点 id（t_operation_site.id），必填。 */
    private Long siteId;

    /** 弹型。 */
    private String ammoType;

    /** 批次号。 */
    private String batchNo;

    /** 变动发数：正入负出。 */
    private Integer changeQty;

    /** IN 入库 / OUT 领用 / RETURN 退回 / SCRAP 报废。 */
    private AmmoBizType bizType;

    /** 关联单据号（入库单号或指令编号）。 */
    private String refNo;

    /** 工厂方法：登记一笔流水。发数符号由业务类型决定，避免调用方把正负写反。 */
    public static AmmoRecord log(Long siteId, String ammoType, String batchNo,
                                 AmmoBizType bizType, int qtyAbs, String refNo) {
        if (qtyAbs <= 0) {
            throw new BizException("弹药流水发数必须为正整数，方向由业务类型决定");
        }
        AmmoRecord record = new AmmoRecord();
        record.assignTo(siteId);
        record.specifyAmmo(ammoType);
        record.specifyBatch(batchNo);
        record.bizType = bizType;
        // 正入负出：入库 / 退回记正，领用 / 报废记负
        record.changeQty = isInbound(bizType) ? qtyAbs : -qtyAbs;
        record.refNo = trimToNull(refNo);
        return record;
    }

    /** 入库类业务（IN / RETURN）记正，出库类业务（OUT / SCRAP）记负。 */
    public static boolean isInbound(AmmoBizType bizType) {
        return bizType == AmmoBizType.IN || bizType == AmmoBizType.RETURN;
    }

    public void assignTo(Long siteId) {
        if (siteId == null) {
            throw new BizException("弹药流水必须归属一个作业点，siteId 不能为空");
        }
        this.siteId = siteId;
    }

    public void specifyAmmo(String ammoType) {
        if (ammoType == null || ammoType.isBlank()) {
            throw new BizException("弹型不能为空，如 BL-1A");
        }
        this.ammoType = ammoType.trim();
    }

    public void specifyBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        this.batchNo = batchNo.trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
