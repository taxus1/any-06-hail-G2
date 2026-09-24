package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.AmmoStockPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 弹药库存 Mapper（基础设施层）。
 *
 * 除 BaseMapper 外，关键是 {@link #addQuantity}：用数据库原子自加完成「同点 + 同弹型 + 同批次
 * 再次入库累加到原记录」，避免「先查后写」在并发下丢更新。
 *
 * 阻塞 JDBC API，只能在 boundedElastic 线程的 blocking(...) 里调用。
 */
@Mapper
public interface AmmoStockMapper extends BaseMapper<AmmoStockPO> {

    /** 按「作业点 + 弹型 + 批次」查未删除的唯一库存（手写 SQL 显式带 del_flag = 0）。 */
    @Select("SELECT * FROM t_ammo_stock "
            + "WHERE site_id = #{siteId} AND ammo_type = #{ammoType} AND batch_no = #{batchNo} "
            + "AND del_flag = 0 LIMIT 1")
    AmmoStockPO selectUnique(@Param("siteId") Long siteId,
                             @Param("ammoType") String ammoType,
                             @Param("batchNo") String batchNo);

    /**
     * 原子累加发数并顺带校准日期（同批次日期理应一致，非空才覆盖）。
     * 只影响未删除行，返回受影响行数（0 表示这条库存还不存在，调用方改走 insert）。
     */
    @Update("UPDATE t_ammo_stock SET quantity = quantity + #{delta}, "
            + "produce_date = COALESCE(#{produceDate}, produce_date), "
            + "expire_date = COALESCE(#{expireDate}, expire_date) "
            + "WHERE id = #{id} AND del_flag = 0")
    int addQuantity(@Param("id") Long id,
                    @Param("delta") int delta,
                    @Param("produceDate") java.time.LocalDate produceDate,
                    @Param("expireDate") java.time.LocalDate expireDate);

    /**
     * 开单领用：仅当结存足够时原子扣减（{@code quantity >= qty} 才放行），
     * 不先查后写，靠这条带条件的 UPDATE 兜住并发超发。
     * 返回受影响行数：1 扣减成功；0 表示库存行不存在或结存不足（调用方据此报「结存不足」并回滚）。
     */
    @Update("UPDATE t_ammo_stock SET quantity = quantity - #{qty} "
            + "WHERE id = #{id} AND del_flag = 0 AND quantity >= #{qty}")
    int deductQuantity(@Param("id") Long id, @Param("qty") int qty);
}
