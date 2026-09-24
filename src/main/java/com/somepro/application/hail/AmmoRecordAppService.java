package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AmmoBizType;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.repository.AmmoRecordRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 弹药出入库流水应用服务：编排「翻看」用例（应用层）。
 *
 * 流水本身不在此手动新增：IN 由入库用例、OUT / RETURN 由作业指令开单 / 回报用例
 * 在各自仓储事务里写入，保证流水与结存同生共死。这里只提供查询。
 */
@Service
public class AmmoRecordAppService {

    private final AmmoRecordRepository recordRepository;

    public AmmoRecordAppService(AmmoRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public Mono<AmmoRecord> getById(Long id) {
        return recordRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("弹药流水不存在：id=" + id)));
    }

    /**
     * 分页翻看流水（最新在前），可按作业点 / 弹型 / 批次 / 业务类型 / 关联单据号过滤。
     * bizType 给了就必须是合法枚举（IN / OUT / RETURN / SCRAP）。
     */
    public Mono<PageResult<AmmoRecord>> page(int pageNum, int pageSize, Long siteId,
                                             String ammoType, String batchNo,
                                             String bizType, String refNo) {
        String normalized = null;
        if (bizType != null && !bizType.isBlank()) {
            normalized = AmmoBizType.of(bizType).name();
        }
        return recordRepository.page(pageNum, pageSize, siteId, ammoType, batchNo, normalized, refNo);
    }
}
