package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 作业指令仓储端口（领域层定义，基础设施层实现）。
 *
 * 开单与回报都牵动弹药结存 + 流水，故不在普通 save 上做，而由实现侧在<b>同一数据库事务</b>里
 * 一并落库：开单扣结存 + 记 OUT 领用（{@link #issueWithStock}），
 * 回报退余款 + 记 RETURN 退回（{@link #reportWithReturn}），保证账实一致、可回滚。
 */
public interface FireOrderRepository {

    Mono<FireOrder> findById(Long id);

    /** 按指令编号查未删除的指令；查不到返回空信号。 */
    Mono<FireOrder> findByNo(String orderNo);

    /**
     * 开单（事务）：生成 ZY-yyyy-#### 编号；校验该点该弹型该批次结存 &ge; planRounds，
     * 原子扣减结存并追加一条 OUT 领用流水（refNo = 指令编号，批次落流水），插入指令。
     * 结存不足抛业务异常，整笔回滚。
     *
     * @param batchNo 出弹批次（指令表无批次列，落在流水上）
     */
    Mono<FireOrder> issueWithStock(FireOrder order, String batchNo);

    /**
     * 回报（事务）：落指令实际发数 / DONE / 起止时刻；若有未打完的发数则退回结存。
     * 退回批次由仓储从本单原 OUT 领用流水反查，调用方无需也不应传入；
     * 加回结存并追加一条 RETURN 退回流水（refNo = 指令编号），与指令更新同事务。
     *
     * @param usedRounds 实际打了几发（0 表示一发未打，全退）
     */
    Mono<FireOrder> reportResult(FireOrder order, int usedRounds,
                                 java.time.LocalDateTime startTime,
                                 java.time.LocalDateTime endTime);

    /**
     * 分页翻看作业指令。
     *
     * @param siteId  只看某个作业点，null 表示不限
     * @param status  状态过滤，null 表示不限；非法值由调用方先收敛
     * @param applyId 只看某份空域申请下的指令，null 表示不限
     */
    Mono<PageResult<FireOrder>> page(int pageNum, int pageSize, Long siteId, String status, Long applyId);
}
