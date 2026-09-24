package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.FireOrderStatus;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.repository.AirspaceApplyRepository;
import com.somepro.domain.hail.repository.FireOrderRepository;
import com.somepro.domain.hail.repository.LauncherRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 作业指令应用服务：编排「开单、回报、翻看」用例（应用层）。
 *
 * 开单跨聚合前置条件（都查库，含早先数据）：
 * - 空域申请必须存在且为 APPROVED —— 没批下来的空域不能开作业指令；
 * - 执行装备必须存在，且挂在空域申请的同一个作业点上（不能拿别点的炮打这点的弹）。
 * 扣结存 + 记 OUT 领用以「点 + 弹型 + 批次」为准，落在仓储事务里；批次不进指令表。
 *
 * 回报实际发数后，没打完的由仓储事务退回结存并记 RETURN。
 */
@Service
public class FireOrderAppService {

    private final FireOrderRepository orderRepository;
    private final AirspaceApplyRepository applyRepository;
    private final LauncherRepository launcherRepository;

    public FireOrderAppService(FireOrderRepository orderRepository,
                               AirspaceApplyRepository applyRepository,
                               LauncherRepository launcherRepository) {
        this.orderRepository = orderRepository;
        this.applyRepository = applyRepository;
        this.launcherRepository = launcherRepository;
    }

    /**
     * 下达作业指令。
     *
     * @param applyId    已获批空域申请 id
     * @param launcherId 执行装备 id（必须归属申请的作业点）
     * @param ammoType   弹型
     * @param batchNo    出弹批次
     * @param planRounds 计划打几发（开单即从该批次结存划走）
     */
    public Mono<FireOrder> issue(Long applyId, Long launcherId, String ammoType,
                                 String batchNo, int planRounds) {
        if (batchNo == null || batchNo.isBlank()) {
            return Mono.error(new BizException("开单必须指定出弹批次 batchNo"));
        }
        String batch = batchNo.trim();
        // 1) 空域必须存在且已批
        return applyRepository.findById(applyId)
                .switchIfEmpty(Mono.error(new BizException("空域申请不存在：applyId=" + applyId)))
                .flatMap(this::ensureApproved)
                // 2) 装备必须存在且归属同一作业点
                .flatMap(apply -> launcherRepository.findById(launcherId)
                        .switchIfEmpty(Mono.error(new BizException("执行装备不存在：launcherId=" + launcherId)))
                        .flatMap(launcher -> ensureSameSite(apply, launcher))
                        .thenReturn(apply))
                // 3) 组装指令并在仓储事务里扣结存 + 记领用（编号 ZY-yyyy-#### 由仓储生成）
                .flatMap(apply -> {
                    FireOrder order = FireOrder.issue(
                            apply.getId(), apply.getSiteId(), launcherId, ammoType, planRounds);
                    return orderRepository.issueWithStock(order, batch);
                });
    }

    private Mono<AirspaceApply> ensureApproved(AirspaceApply apply) {
        if (!apply.isApproved()) {
            return Mono.error(new BizException(
                    "空域未获批，不能开作业指令：申请 " + apply.getApplyNo()
                            + " 当前状态 " + apply.getStatus()));
        }
        return Mono.just(apply);
    }

    private Mono<Launcher> ensureSameSite(AirspaceApply apply, Launcher launcher) {
        if (!launcher.getSiteId().equals(apply.getSiteId())) {
            return Mono.error(new BizException(
                    "装备 " + launcher.getLauncherCode() + " 不属于作业点 id=" + apply.getSiteId()
                            + "（装备归属作业点 id=" + launcher.getSiteId() + "）"));
        }
        return Mono.just(launcher);
    }

    /**
     * 打完回报实际发数，指令收尾；没打完的退回结存（仓储事务 + RETURN 流水）。
     *
     * @param usedRounds 实际打了几发，0 表示一发未打（全退）
     */
    public Mono<FireOrder> report(Long id, int usedRounds,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：id=" + id)))
                .flatMap(order -> {
                    if (order.getStatus() == FireOrderStatus.DONE
                            || order.getStatus() == FireOrderStatus.VOID) {
                        return Mono.error(new BizException(
                                "指令已 " + order.getStatus() + "，不能重复回报：" + order.getOrderNo()));
                    }
                    return orderRepository.reportResult(order, usedRounds, startTime, endTime);
                });
    }

    public Mono<FireOrder> getById(Long id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：id=" + id)));
    }

    /** 分页翻看作业指令，可按作业点、状态、空域申请过滤；状态给了就必须是合法枚举。 */
    public Mono<PageResult<FireOrder>> page(int pageNum, int pageSize,
                                            Long siteId, String status, Long applyId) {
        String normalized = null;
        if (status != null && !status.isBlank()) {
            normalized = FireOrderStatus.of(status).name();
        }
        return orderRepository.page(pageNum, pageSize, siteId, normalized, applyId);
    }
}
