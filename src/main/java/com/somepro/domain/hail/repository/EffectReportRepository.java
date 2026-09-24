package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.EffectReport;
import reactor.core.publisher.Mono;

/**
 * 作业效果上报仓储端口（领域层定义，基础设施层实现）。
 * 一条指令一份（uk_order）：重复上报由 {@link #findByOrderId} 判重，唯一索引兜底。
 */
public interface EffectReportRepository {

    Mono<EffectReport> insert(EffectReport report);

    Mono<EffectReport> findById(Long id);

    /** 按作业指令 id 查效果报告；查不到返回空信号。 */
    Mono<EffectReport> findByOrderId(Long orderId);
}
