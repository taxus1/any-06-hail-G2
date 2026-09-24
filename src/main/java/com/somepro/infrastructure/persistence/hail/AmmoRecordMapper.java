package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 弹药出入库流水 Mapper（基础设施层）。
 * 流水只追加，翻看走 BaseMapper.selectList + PageHelper。阻塞 JDBC，只能在 blocking(...) 里调用。
 */
@Mapper
public interface AmmoRecordMapper extends BaseMapper<AmmoRecordPO> {

    /**
     * 反查某张作业指令开单时的 OUT 领用流水（用于回报时定位退回批次）。
     * 一张单只开一次、只记一笔 OUT，故 LIMIT 1。查不到返回 null。
     */
    @Select("SELECT * FROM t_ammo_record "
            + "WHERE ref_no = #{orderNo} AND biz_type = 'OUT' AND del_flag = 0 "
            + "ORDER BY id DESC LIMIT 1")
    AmmoRecordPO selectOutByRefNo(@Param("orderNo") String orderNo);
}
