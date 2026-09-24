package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 作业指令 Mapper（基础设施层）。阻塞 JDBC API，只能在 blocking(...) 里调用。
 */
@Mapper
public interface FireOrderMapper extends BaseMapper<FireOrderPO> {

    /** 按指令编号查未删除指令（手写 SQL 显式带 del_flag = 0）。 */
    @Select("SELECT * FROM t_fire_order WHERE order_no = #{orderNo} AND del_flag = 0 LIMIT 1")
    FireOrderPO selectByNo(@Param("orderNo") String orderNo);

    /**
     * 取某编号前缀（如 ZY-2026-）下已用的最大编号，用于生成下一个序号。
     * 连逻辑删除行一起算（编号不复用、不回收），没有则返回 null。
     */
    @Select("SELECT MAX(order_no) FROM t_fire_order WHERE order_no LIKE #{prefix}")
    String selectMaxNoByPrefix(@Param("prefix") String prefix);
}
