package com.somepro.infrastructure.persistence.hail.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * t_fire_order 表的持久化对象（PO，基础设施层）。
 * 字段与列一一对应，不放业务规则（开单扣弹 / 回报退弹规则在领域对象与仓储事务里）。
 * 出弹批次不在本表，落在 t_ammo_record.batch_no 上（ref_no 关联本单 order_no）。
 */
@Getter
@Setter
@TableName("t_fire_order")
public class FireOrderPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("apply_id")
    private Long applyId;

    @TableField("site_id")
    private Long siteId;

    @TableField("launcher_id")
    private Long launcherId;

    @TableField("ammo_type")
    private String ammoType;

    @TableField("plan_rounds")
    private Integer planRounds;

    @TableField("used_rounds")
    private Integer usedRounds;

    @TableField("status")
    private String status;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("void_reason")
    private String voidReason;
}
