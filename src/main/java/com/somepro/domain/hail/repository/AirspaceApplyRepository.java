package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 空域申请仓储端口（领域层定义，基础设施层实现）。
 *
 * 申请编号由实现侧按「KQ-年份-序号」生成并保证唯一，故 insert 走 {@link #insert} 由仓储编号落库；
 * 批复后的状态改写走 {@link #updateById}。
 */
public interface AirspaceApplyRepository {

    /**
     * 新报一条空域申请：仓储生成 KQ-yyyy-#### 编号（序号取当年最大 + 1），落库后回填。
     * 编号基于 planStart 的年份；并发撞 uk_apply_no 时由实现侧重试序号。
     */
    Mono<AirspaceApply> insert(AirspaceApply apply);

    /** 批复 / 驳回后的状态改写（按 id 更新）。 */
    Mono<AirspaceApply> updateById(AirspaceApply apply);

    Mono<AirspaceApply> findById(Long id);

    /** 按申请编号查未删除的申请；查不到返回空信号。 */
    Mono<AirspaceApply> findByNo(String applyNo);

    /**
     * 分页翻看空域申请。
     *
     * @param siteId 只看某个作业点，null 表示不限
     * @param status 状态过滤，null 表示不限；非法值由调用方先收敛
     */
    Mono<PageResult<AirspaceApply>> page(int pageNum, int pageSize, Long siteId, String status);
}
