package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.AirspaceApplyPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 空域申请 Mapper（基础设施层）。阻塞 JDBC API，只能在 blocking(...) 里调用。
 */
@Mapper
public interface AirspaceApplyMapper extends BaseMapper<AirspaceApplyPO> {

    /**
     * 取某编号前缀（如 KQ-2026-）下已用的最大编号，用于生成下一个序号。
     * 连逻辑删除行一起算（编号不复用、不回收），没有则返回 null。
     */
    @Select("SELECT MAX(apply_no) FROM t_airspace_apply WHERE apply_no LIKE #{prefix}")
    String selectMaxNoByPrefix(@Param("prefix") String prefix);
}
