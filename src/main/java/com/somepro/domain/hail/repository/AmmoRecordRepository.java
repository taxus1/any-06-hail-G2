package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 弹药出入库流水仓储端口（领域层定义，基础设施层实现）。
 *
 * 流水只追加：开单 / 回报 / 入库时由对应仓储在同事务里写入，这里另给 {@link #insert}
 * 供独立追加与测试使用。翻看走 {@link #page}。
 */
public interface AmmoRecordRepository {

    Mono<AmmoRecord> insert(AmmoRecord record);

    Mono<AmmoRecord> findById(Long id);

    /**
     * 分页翻看流水，按时间倒序（最新变动在前）。
     *
     * @param siteId   只看某个作业点，null 表示不限
     * @param ammoType 弹型精确过滤，null 表示不限
     * @param batchNo  批次号模糊匹配，null 表示不限
     * @param bizType  业务类型过滤，null 表示不限；非法值由调用方先收敛
     * @param refNo    关联单据号精确过滤，null 表示不限
     */
    Mono<PageResult<AmmoRecord>> page(int pageNum, int pageSize, Long siteId, String ammoType,
                                      String batchNo, String bizType, String refNo);
}
