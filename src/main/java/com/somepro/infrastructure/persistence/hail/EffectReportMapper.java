package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.EffectReportPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 作业效果上报 Mapper（基础设施层）。阻塞 JDBC API，只能在 blocking(...) 里调用。
 */
@Mapper
public interface EffectReportMapper extends BaseMapper<EffectReportPO> {

    /** 按作业指令 id 查效果报告（uk_order，手写 SQL 显式带 del_flag = 0）。 */
    @Select("SELECT * FROM t_effect_report WHERE order_id = #{orderId} AND del_flag = 0 LIMIT 1")
    EffectReportPO selectByOrderId(@Param("orderId") Long orderId);
}
