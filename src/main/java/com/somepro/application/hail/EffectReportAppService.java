package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.FireOrderStatus;
import com.somepro.domain.hail.repository.EffectReportRepository;
import com.somepro.domain.hail.repository.FireOrderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作业效果上报应用服务：编排「上报、翻看」用例（应用层）。
 *
 * 规则：一条指令一份效果报告 —— 指令必须存在且已 DONE（打完才能评效果），
 * 已报过的不允许重复报，uk_order 唯一索引兜底。
 */
@Service
public class EffectReportAppService {

    private final EffectReportRepository reportRepository;
    private final FireOrderRepository orderRepository;

    public EffectReportAppService(EffectReportRepository reportRepository,
                                  FireOrderRepository orderRepository) {
        this.reportRepository = reportRepository;
        this.orderRepository = orderRepository;
    }

    /** 为一条已完成指令上报效果。指标单位：降雨量 / 冰雹粒径毫米，影响面积平方公里。 */
    public Mono<EffectReport> report(Long orderId, LocalDateTime reportTime,
                                     BigDecimal rainfallMm, BigDecimal hailSizeMm,
                                     BigDecimal areaKm2, String remark) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：orderId=" + orderId)))
                .flatMap(this::ensureDone)
                // 一条指令一份：先查重，并发漏网由 uk_order + DuplicateKeyException 兜底
                .flatMap(order -> reportRepository.findByOrderId(orderId)
                        .flatMap(existing -> Mono.<EffectReport>error(new BizException(
                                "该指令已上报过效果，一条指令只能报一份：" + order.getOrderNo())))
                        .switchIfEmpty(Mono.defer(() -> {
                            // 工厂集中校验非负、至少一项等不变量
                            EffectReport report = EffectReport.report(
                                    orderId, reportTime, rainfallMm, hailSizeMm, areaKm2, remark);
                            return reportRepository.insert(report);
                        }))
                        .onErrorResume(DuplicateKeyException.class, e ->
                                Mono.error(new BizException(
                                        "该指令已上报过效果，一条指令只能报一份：" + order.getOrderNo()))));
    }

    private Mono<FireOrder> ensureDone(FireOrder order) {
        if (order.getStatus() != FireOrderStatus.DONE) {
            return Mono.error(new BizException(
                    "指令尚未完成，不能上报效果：" + order.getOrderNo()
                            + " 当前状态 " + order.getStatus()));
        }
        return Mono.just(order);
    }

    public Mono<EffectReport> getById(Long id) {
        return reportRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("效果报告不存在：id=" + id)));
    }

    /** 按作业指令 id 查这条指令的效果报告。 */
    public Mono<EffectReport> getByOrderId(Long orderId) {
        return reportRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new BizException(
                        "该指令还没有效果报告：orderId=" + orderId)));
    }
}
