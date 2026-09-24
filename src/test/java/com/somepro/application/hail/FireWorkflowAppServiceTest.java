package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AirspaceStatus;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.FireOrderStatus;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.hail.repository.AirspaceApplyRepository;
import com.somepro.domain.hail.repository.AmmoRecordRepository;
import com.somepro.domain.hail.repository.EffectReportRepository;
import com.somepro.domain.hail.repository.FireOrderRepository;
import com.somepro.domain.hail.repository.LauncherRepository;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「空域 → 指令 → 回报 → 效果」作业主线的应用层编排单测：
 * 不连库、不起 Spring，用 Mockito 顶掉仓储端口，专测跨聚合规则与状态流转。
 */
class FireWorkflowAppServiceTest {

    private final OperationSiteRepository siteRepo = mock(OperationSiteRepository.class);
    private final AirspaceApplyRepository applyRepo = mock(AirspaceApplyRepository.class);
    private final LauncherRepository launcherRepo = mock(LauncherRepository.class);
    private final FireOrderRepository orderRepo = mock(FireOrderRepository.class);
    private final AmmoRecordRepository recordRepo = mock(AmmoRecordRepository.class);
    private final EffectReportRepository effectRepo = mock(EffectReportRepository.class);

    private final AirspaceApplyAppService airspaceApp =
            new AirspaceApplyAppService(applyRepo, siteRepo);
    private final FireOrderAppService orderApp =
            new FireOrderAppService(orderRepo, applyRepo, launcherRepo);
    private final AmmoRecordAppService recordApp = new AmmoRecordAppService(recordRepo);
    private final EffectReportAppService effectApp =
            new EffectReportAppService(effectRepo, orderRepo);

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 7, 10, 14, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 7, 10, 16, 0);

    private OperationSite site(long id) {
        OperationSite s = OperationSite.register("YY-013", "南山西点", "海林县",
                820, "张三", "138", "ACTIVE");
        s.setId(id);
        return s;
    }

    private AirspaceApply apply(long id, AirspaceStatus status) {
        AirspaceApply a = AirspaceApply.submit(1L, "HAIL", T1, T2, 6000);
        a.setId(id);
        a.setApplyNo("KQ-2026-0101");
        a.setStatus(status);
        return a;
    }

    // ---------- 空域申请 ----------

    @Test
    void submitAirspace_rejectsUnknownSite() {
        when(siteRepo.findById(404L)).thenReturn(Mono.empty());
        StepVerifier.create(airspaceApp.submit(404L, "HAIL", T1, T2, 6000))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("作业点不存在"))
                .verify();
        verify(applyRepo, never()).insert(any());
    }

    @Test
    void submitAirspace_rejectsIllegalPurpose() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                airspaceApp.submit(1L, "SNOW", T1, T2, 6000));
    }

    @Test
    void submitAirspace_rejectsInvertedWindow() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                airspaceApp.submit(1L, "RAIN", T2, T1, null));
    }

    @Test
    void submitAirspace_success_startsPending() {
        when(siteRepo.findById(1L)).thenReturn(Mono.just(site(1L)));
        when(applyRepo.insert(any(AirspaceApply.class))).thenAnswer(inv -> {
            AirspaceApply a = inv.getArgument(0);
            a.setId(10L);
            return Mono.just(a);
        });
        StepVerifier.create(airspaceApp.submit(1L, "rain", T1, T2, 6000))
                .assertNext(a -> {
                    assert a.getStatus() == AirspaceStatus.PENDING;
                    // 小写目的被枚举归一化
                    assert a.getPurpose().name().equals("RAIN");
                })
                .verifyComplete();
    }

    @Test
    void approve_setsApproved_andRejectRequiresReason() {
        when(applyRepo.findById(10L)).thenReturn(Mono.just(apply(10L, AirspaceStatus.PENDING)));
        when(applyRepo.updateById(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        StepVerifier.create(airspaceApp.approve(10L))
                .assertNext(a -> {
                    assert a.getStatus() == AirspaceStatus.APPROVED;
                    assert a.getApproveTime() != null;
                })
                .verifyComplete();

        // 驳回不给原因直接拒
        StepVerifier.create(airspaceApp.reject(10L, "  "))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("驳回原因"))
                .verify();
    }

    @Test
    void approve_twiceIsRejected() {
        // 已批过的不能再批
        when(applyRepo.findById(10L)).thenReturn(Mono.just(apply(10L, AirspaceStatus.APPROVED)));
        StepVerifier.create(airspaceApp.approve(10L))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("只有待批"))
                .verify();
        verify(applyRepo, never()).updateById(any());
    }

    // ---------- 作业指令 ----------

    @Test
    void issue_rejectsNonApprovedAirspace() {
        when(applyRepo.findById(10L)).thenReturn(Mono.just(apply(10L, AirspaceStatus.PENDING)));
        StepVerifier.create(orderApp.issue(10L, 7L, "BL-1A", "B1", 5))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("空域未获批"))
                .verify();
        verify(orderRepo, never()).issueWithStock(any(), any());
    }

    @Test
    void issue_rejectsLauncherFromAnotherSite() {
        AirspaceApply a = apply(10L, AirspaceStatus.APPROVED); // siteId=1
        Launcher launcher = Launcher.register("ZB-9", 2L, "QF", 12, "READY", null);
        launcher.setId(7L);
        when(applyRepo.findById(10L)).thenReturn(Mono.just(a));
        when(launcherRepo.findById(7L)).thenReturn(Mono.just(launcher));
        StepVerifier.create(orderApp.issue(10L, 7L, "BL-1A", "B1", 5))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("不属于作业点"))
                .verify();
        verify(orderRepo, never()).issueWithStock(any(), any());
    }

    @Test
    void issue_rejectsBlankBatch() {
        StepVerifier.create(orderApp.issue(10L, 7L, "BL-1A", " ", 5))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("出弹批次"))
                .verify();
        verify(orderRepo, never()).issueWithStock(any(), any());
    }

    @Test
    void issue_success_delegatesTransactionalStockDeduction() {
        AirspaceApply a = apply(10L, AirspaceStatus.APPROVED); // siteId=1
        Launcher launcher = Launcher.register("ZB-7", 1L, "QF", 12, "READY", null);
        launcher.setId(7L);
        when(applyRepo.findById(10L)).thenReturn(Mono.just(a));
        when(launcherRepo.findById(7L)).thenReturn(Mono.just(launcher));
        when(orderRepo.issueWithStock(any(FireOrder.class), eq("B1"))).thenAnswer(inv -> {
            FireOrder o = inv.getArgument(0);
            o.setId(100L);
            o.changeNo("ZY-2026-0101");
            return Mono.just(o);
        });
        StepVerifier.create(orderApp.issue(10L, 7L, "BL-1A", "B1", 8))
                .assertNext(o -> {
                    assert o.getId() == 100L;
                    assert o.getOrderNo().equals("ZY-2026-0101");
                    assert o.getPlanRounds() == 8;
                    assert o.getStatus() == FireOrderStatus.ISSUED;
                })
                .verifyComplete();
    }

    // ---------- 作业回报 ----------

    private FireOrder issuedOrder(long id, int plan) {
        FireOrder o = FireOrder.issue(10L, 1L, 7L, "BL-1A", plan);
        o.setId(id);
        o.changeNo("ZY-2026-0101");
        return o;
    }

    @Test
    void report_usedMoreThanPlanRejected() {
        // 实际不能超过计划的不变量在领域回报方法里收口（仓储事务内调用）
        FireOrder o = issuedOrder(100L, 8);
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                o.reportResult(9, T1, T2));
        verify(orderRepo, never()).reportResult(any(), anyInt(), any(), any());
    }

    @Test
    void report_returnsLeftover_andMarksDone() {
        FireOrder o = issuedOrder(100L, 8);
        when(orderRepo.findById(100L)).thenReturn(Mono.just(o));
        // 仓储模拟回报后：打了 6，退 2，置 DONE
        when(orderRepo.reportResult(any(FireOrder.class), eq(6), any(), any())).thenAnswer(inv -> {
            FireOrder arg = inv.getArgument(0);
            int left = arg.reportResult(6, T1, T2);
            assert left == 2 : "应退回 2 发";
            return Mono.just(arg);
        });
        StepVerifier.create(orderApp.report(100L, 6, T1, T2))
                .assertNext(done -> {
                    assert done.getStatus() == FireOrderStatus.DONE;
                    assert done.getUsedRounds() == 6;
                })
                .verifyComplete();
    }

    @Test
    void report_zeroUsedFullyReturns() {
        FireOrder o = issuedOrder(100L, 8);
        when(orderRepo.findById(100L)).thenReturn(Mono.just(o));
        when(orderRepo.reportResult(any(FireOrder.class), eq(0), any(), any())).thenAnswer(inv -> {
            FireOrder arg = inv.getArgument(0);
            int left = arg.reportResult(0, null, T2);
            assert left == 8 : "一发未打应全退 8 发";
            return Mono.just(arg);
        });
        StepVerifier.create(orderApp.report(100L, 0, null, T2))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void report_doneOrderCannotReportAgain() {
        FireOrder o = issuedOrder(100L, 8);
        o.reportResult(8, T1, T2); // 已 DONE
        when(orderRepo.findById(100L)).thenReturn(Mono.just(o));
        StepVerifier.create(orderApp.report(100L, 1, null, null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("不能重复回报"))
                .verify();
    }

    // ---------- 效果上报 ----------

    private FireOrder doneOrder(long id) {
        FireOrder o = issuedOrder(id, 8);
        o.reportResult(6, T1, T2);
        return o;
    }

    @Test
    void effect_rejectedBeforeOrderDone() {
        FireOrder o = issuedOrder(100L, 8); // ISSUED
        when(orderRepo.findById(100L)).thenReturn(Mono.just(o));
        StepVerifier.create(effectApp.report(100L, null,
                        new BigDecimal("12.5"), null, new BigDecimal("30"), null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("尚未完成"))
                .verify();
        verify(effectRepo, never()).insert(any());
    }

    @Test
    void effect_rejectedWhenAllMetricsBlank() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                EffectReport.report(100L, null, null, null, null, null));
    }

    @Test
    void effect_rejectedOnNegativeMetric() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                EffectReport.report(100L, null, new BigDecimal("-1"), null, null, null));
    }

    @Test
    void effect_duplicateReportRejected() {
        when(orderRepo.findById(100L)).thenReturn(Mono.just(doneOrder(100L)));
        when(effectRepo.findByOrderId(100L)).thenReturn(Mono.just(
                EffectReport.report(100L, null, new BigDecimal("1"), null, null, null)));
        StepVerifier.create(effectApp.report(100L, null,
                        new BigDecimal("9"), null, null, null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("只能报一份"))
                .verify();
        verify(effectRepo, never()).insert(any());
    }

    @Test
    void effect_success_withUnits() {
        when(orderRepo.findById(100L)).thenReturn(Mono.just(doneOrder(100L)));
        when(effectRepo.findByOrderId(100L)).thenReturn(Mono.empty());
        when(effectRepo.insert(any(EffectReport.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        StepVerifier.create(effectApp.report(100L, null,
                        new BigDecimal("12.50"), new BigDecimal("8.00"), new BigDecimal("42.00"), "增雨明显"))
                .assertNext(r -> {
                    assert r.getRainfallMm().compareTo(new BigDecimal("12.50")) == 0;
                    assert r.getHailSizeMm().compareTo(new BigDecimal("8.00")) == 0;
                    assert r.getAreaKm2().compareTo(new BigDecimal("42.00")) == 0;
                    assert r.getReportTime() != null;
                })
                .verifyComplete();
    }

    // ---------- 流水枚举过滤 ----------

    @Test
    void recordPage_rejectsIllegalBizType() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                recordApp.page(1, 20, null, null, null, "MOVE", null));
        verify(recordRepo, never()).page(anyInt(), anyInt(), any(), any(), any(), any(), any());
    }
}
