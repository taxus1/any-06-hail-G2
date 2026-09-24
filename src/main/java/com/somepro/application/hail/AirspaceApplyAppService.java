package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AirspaceStatus;
import com.somepro.domain.hail.repository.AirspaceApplyRepository;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 空域申请应用服务：编排「申报、批复、翻看」用例（应用层）。
 *
 * - 申报：作业点必须真实存在（含早先数据，以库为准），编号由仓储按 KQ-yyyy-#### 生成，新单 PENDING。
 * - 批复：只有待批单可处理；通过 → APPROVED（之后才允许开作业指令），驳回 → REJECTED 且必须写原因。
 *
 * 出入参都是领域对象 / 基本类型，不认识 PO、VO。
 */
@Service
public class AirspaceApplyAppService {

    private final AirspaceApplyRepository applyRepository;
    private final OperationSiteRepository siteRepository;

    public AirspaceApplyAppService(AirspaceApplyRepository applyRepository,
                                   OperationSiteRepository siteRepository) {
        this.applyRepository = applyRepository;
        this.siteRepository = siteRepository;
    }

    /** 新报一条空域申请：先确认作业点真实存在，编号由仓储生成，初始 PENDING。 */
    public Mono<AirspaceApply> submit(Long siteId, String purpose, LocalDateTime planStart,
                                      LocalDateTime planEnd, Integer maxAltitude) {
        // 工厂集中校验必填 / 时段 / 高度等不变量；编号 KQ-yyyy-#### 归仓储取号
        AirspaceApply apply = AirspaceApply.submit(
                siteId, purpose, planStart, planEnd, maxAltitude);
        return siteRepository.findById(apply.getSiteId())
                .switchIfEmpty(Mono.error(new BizException(
                        "作业点不存在：siteId=" + apply.getSiteId())))
                .flatMap(site -> applyRepository.insert(apply));
    }

    /** 批复通过：只有待批空域可批。 */
    public Mono<AirspaceApply> approve(Long id) {
        return applyRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("空域申请不存在：id=" + id)))
                .flatMap(apply -> {
                    apply.approve(LocalDateTime.now());
                    return applyRepository.updateById(apply);
                });
    }

    /** 驳回：只有待批空域可驳，且必须写明原因。 */
    public Mono<AirspaceApply> reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new BizException("驳回空域必须写明驳回原因"));
        }
        return applyRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("空域申请不存在：id=" + id)))
                .flatMap(apply -> {
                    apply.reject(reason, LocalDateTime.now());
                    return applyRepository.updateById(apply);
                });
    }

    public Mono<AirspaceApply> getById(Long id) {
        return applyRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("空域申请不存在：id=" + id)));
    }

    /** 分页翻看空域申请，可按作业点、状态过滤；状态给了就必须是合法枚举。 */
    public Mono<PageResult<AirspaceApply>> page(int pageNum, int pageSize, Long siteId, String status) {
        String normalized = null;
        if (status != null && !status.isBlank()) {
            normalized = AirspaceStatus.of(status).name();
        }
        return applyRepository.page(pageNum, pageSize, siteId, normalized);
    }
}
