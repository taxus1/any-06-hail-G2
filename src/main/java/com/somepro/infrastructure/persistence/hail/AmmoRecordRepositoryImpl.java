package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.repository.AmmoRecordRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.AmmoRecordPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 弹药出入库流水仓储适配器（基础设施层）。
 * 流水只追加；翻看按时间 / id 倒序，最新变动在前。JDBC 全部走 blocking(...)。
 */
@Repository
public class AmmoRecordRepositoryImpl extends BlockingRepositorySupport implements AmmoRecordRepository {

    private final AmmoRecordMapper mapper;

    public AmmoRecordRepositoryImpl(AmmoRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<AmmoRecord> insert(AmmoRecord record) {
        return blocking(() -> {
            AmmoRecordPO po = AmmoRecordPoConverter.toPo(record);
            po.setId(IdUtil.getSnowflakeNextId());
            mapper.insert(po);
            return AmmoRecordPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<AmmoRecord> findById(Long id) {
        return blocking(() -> {
            AmmoRecordPO po = mapper.selectById(id);
            return po == null ? null : AmmoRecordPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<AmmoRecord>> page(int pageNum, int pageSize, Long siteId, String ammoType,
                                             String batchNo, String bizType, String refNo) {
        return this.<PageResult<AmmoRecord>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<AmmoRecordPO> wrapper = Wrappers.<AmmoRecordPO>lambdaQuery()
                        .eq(siteId != null, AmmoRecordPO::getSiteId, siteId)
                        .eq(ammoType != null && !ammoType.isBlank(),
                                AmmoRecordPO::getAmmoType, ammoType == null ? null : ammoType.trim())
                        .like(batchNo != null && !batchNo.isBlank(),
                                AmmoRecordPO::getBatchNo, batchNo == null ? null : batchNo.trim())
                        .eq(bizType != null && !bizType.isBlank(),
                                AmmoRecordPO::getBizType, bizType == null ? null : bizType.trim())
                        .eq(refNo != null && !refNo.isBlank(),
                                AmmoRecordPO::getRefNo, refNo == null ? null : refNo.trim())
                        .orderByDesc(AmmoRecordPO::getId);
                List<AmmoRecordPO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<AmmoRecord> content = rows.stream()
                        .map(AmmoRecordPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
