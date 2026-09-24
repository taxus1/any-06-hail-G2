package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.repository.AirspaceApplyRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.AirspaceApplyPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AirspaceApplyPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import com.somepro.infrastructure.persistence.support.DocNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 空域申请仓储适配器（基础设施层）。
 *
 * JDBC 全部走 blocking(...)。编号 KQ-yyyy-#### 在 {@link #insert} 里按拟作业开始年份取号，
 * 并发下两个请求算出同号由 uk_apply_no 抛 {@link DuplicateKeyException}，重新取号重试若干次。
 */
@Repository
public class AirspaceApplyRepositoryImpl extends BlockingRepositorySupport
        implements AirspaceApplyRepository {

    private static final String NO_PREFIX = "KQ-";
    private static final int MAX_RETRY = 3;

    private final AirspaceApplyMapper mapper;

    public AirspaceApplyRepositoryImpl(AirspaceApplyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<AirspaceApply> insert(AirspaceApply apply) {
        return blocking(() -> doInsert(apply));
    }

    private AirspaceApply doInsert(AirspaceApply apply) {
        int year = apply.getPlanStart().getYear();
        DuplicateKeyException last = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            String maxNo = mapper.selectMaxNoByPrefix(NO_PREFIX + year + "-%");
            String no = DocNoGenerator.next(NO_PREFIX, year, maxNo);
            AirspaceApplyPO po = AirspaceApplyPoConverter.toPo(apply);
            po.setId(IdUtil.getSnowflakeNextId());
            po.setApplyNo(no);
            try {
                mapper.insert(po);
                return AirspaceApplyPoConverter.toDomain(po);
            } catch (DuplicateKeyException e) {
                // 并发撞号：换一个序号再来
                last = e;
            }
        }
        throw last;
    }

    @Override
    public Mono<AirspaceApply> updateById(AirspaceApply apply) {
        return blocking(() -> {
            AirspaceApplyPO po = AirspaceApplyPoConverter.toPo(apply);
            mapper.updateById(po);
            AirspaceApplyPO fresh = mapper.selectById(apply.getId());
            return AirspaceApplyPoConverter.toDomain(fresh);
        });
    }

    @Override
    public Mono<AirspaceApply> findById(Long id) {
        return blocking(() -> {
            AirspaceApplyPO po = mapper.selectById(id);
            return po == null ? null : AirspaceApplyPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<AirspaceApply> findByNo(String applyNo) {
        return blocking(() -> {
            LambdaQueryWrapper<AirspaceApplyPO> wrapper =
                    Wrappers.<AirspaceApplyPO>lambdaQuery()
                            .eq(AirspaceApplyPO::getApplyNo, applyNo)
                            .last("LIMIT 1");
            AirspaceApplyPO po = mapper.selectOne(wrapper);
            return po == null ? null : AirspaceApplyPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<AirspaceApply>> page(int pageNum, int pageSize, Long siteId, String status) {
        return this.<PageResult<AirspaceApply>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<AirspaceApplyPO> wrapper =
                        Wrappers.<AirspaceApplyPO>lambdaQuery()
                                .eq(siteId != null, AirspaceApplyPO::getSiteId, siteId)
                                .eq(status != null && !status.isBlank(),
                                        AirspaceApplyPO::getStatus,
                                        status == null ? null : status.trim())
                                .orderByDesc(AirspaceApplyPO::getPlanStart)
                                .orderByDesc(AirspaceApplyPO::getId);
                List<AirspaceApplyPO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<AirspaceApply> content = rows.stream()
                        .map(AirspaceApplyPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
