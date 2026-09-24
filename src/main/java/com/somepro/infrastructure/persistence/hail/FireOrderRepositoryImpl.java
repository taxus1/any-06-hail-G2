package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AmmoBizType;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.repository.FireOrderRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.AmmoRecordPoConverter;
import com.somepro.infrastructure.persistence.hail.converter.FireOrderPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO;
import com.somepro.infrastructure.persistence.hail.po.AmmoStockPO;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import com.somepro.infrastructure.persistence.support.DocNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 作业指令仓储适配器（基础设施层）。
 *
 * 开单 / 回报都同时动「指令 + 库存 + 流水」三处，必须在<b>同一数据库事务</b>里完成，
 * 任一步失败整体回滚，绝不能出现「指令开了但弹没划走」或「退了弹但单没收尾」。
 * 事务边界刻意放在 {@code blocking(...)} 的 Callable 内部（boundedElastic 线程上），
 * 不能放在响应式方法上 —— 注解 / TransactionTemplate 都依赖 ThreadLocal 绑定的连接。
 *
 * 扣减不用「先查后写」：{@code AmmoStockMapper.deductQuantity} 带 {@code quantity >= ?} 条件，
 * 一条原子 UPDATE 兜住并发超发，受影响行数为 0 即结存不足，抛异常回滚。
 */
@Repository
public class FireOrderRepositoryImpl extends BlockingRepositorySupport implements FireOrderRepository {

    private static final String NO_PREFIX = "ZY-";
    private static final int MAX_RETRY = 3;

    private final FireOrderMapper orderMapper;
    private final AmmoStockMapper stockMapper;
    private final AmmoRecordMapper recordMapper;
    private final TransactionTemplate txTemplate;

    public FireOrderRepositoryImpl(FireOrderMapper orderMapper,
                                   AmmoStockMapper stockMapper,
                                   AmmoRecordMapper recordMapper,
                                   PlatformTransactionManager txManager) {
        this.orderMapper = orderMapper;
        this.stockMapper = stockMapper;
        this.recordMapper = recordMapper;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public Mono<FireOrder> findById(Long id) {
        return blocking(() -> {
            FireOrderPO po = orderMapper.selectById(id);
            return po == null ? null : FireOrderPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<FireOrder> findByNo(String orderNo) {
        return blocking(() -> {
            FireOrderPO po = orderMapper.selectByNo(orderNo);
            return po == null ? null : FireOrderPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<FireOrder> issueWithStock(FireOrder order, String batchNo) {
        return blocking(() -> {
            // 编号撞 uk_order_no 时整笔事务回滚，换序号重试
            DuplicateKeyException last = null;
            for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
                try {
                    return txTemplate.execute(status -> doIssue(order, batchNo));
                } catch (DuplicateKeyException e) {
                    last = e;
                }
            }
            throw last;
        });
    }

    private FireOrder doIssue(FireOrder order, String batchNo) {
        int year = LocalDateTime.now().getYear();
        String maxNo = orderMapper.selectMaxNoByPrefix(NO_PREFIX + year + "-%");
        String no = DocNoGenerator.next(NO_PREFIX, year, maxNo);
        order.changeNo(no);

        // 1) 定位出弹库存行（点 + 弹型 + 批次）
        AmmoStockPO stock = stockMapper.selectUnique(
                order.getSiteId(), order.getAmmoType(), batchNo);
        if (stock == null) {
            throw new BizException("该作业点没有弹型 " + order.getAmmoType()
                    + " 批次 " + batchNo + " 的库存，无法开单");
        }
        // 2) 结存足够才原子扣减；不足受影响行数为 0，整笔回滚
        int affected = stockMapper.deductQuantity(stock.getId(), order.getPlanRounds());
        if (affected == 0) {
            throw new BizException("弹型 " + order.getAmmoType() + " 批次 " + batchNo
                    + " 结存不足，计划 " + order.getPlanRounds() + " 发");
        }
        // 3) 落指令
        FireOrderPO po = FireOrderPoConverter.toPo(order);
        po.setId(IdUtil.getSnowflakeNextId());
        orderMapper.insert(po);
        // 4) 账上记一笔领用（负数出库，ref_no 关联指令编号；批次落在流水上）
        AmmoRecord out = AmmoRecord.log(order.getSiteId(), order.getAmmoType(), batchNo,
                AmmoBizType.OUT, order.getPlanRounds(), no);
        insertRecord(out);
        order.setId(po.getId());
        return FireOrderPoConverter.toDomain(po);
    }

    @Override
    public Mono<FireOrder> reportResult(FireOrder order, int usedRounds,
                                        LocalDateTime startTime, LocalDateTime endTime) {
        return blocking(() -> txTemplate.execute(status ->
                doReport(order, usedRounds, startTime, endTime)));
    }

    private FireOrder doReport(FireOrder order, int usedRounds,
                               LocalDateTime startTime, LocalDateTime endTime) {
        // 1) 领域收尾：校验实际发数合法，置 DONE，算出退回发数
        int returnQty = order.reportResult(usedRounds, startTime, endTime);
        // 2) 指令落库
        FireOrderPO po = FireOrderPoConverter.toPo(order);
        orderMapper.updateById(po);
        // 3) 没打完的退回结存并记一笔退回（正数入库）；全打完则不产生退回
        if (returnQty > 0) {
            // 退回批次从本单开单时那笔 OUT 领用流水反查，调用方不传批次
            AmmoRecordPO outRecord = recordMapper.selectOutByRefNo(order.getOrderNo());
            if (outRecord == null) {
                throw new BizException("找不到指令 " + order.getOrderNo()
                        + " 的领用流水，无法确定退回批次，请人工核对");
            }
            String batchNo = outRecord.getBatchNo();
            AmmoStockPO stock = stockMapper.selectUnique(
                    order.getSiteId(), order.getAmmoType(), batchNo);
            if (stock == null) {
                // 开单时那条库存被删除等极端情况：不静默吞，回滚由人工对账
                throw new BizException("退回失败：弹型 " + order.getAmmoType() + " 批次 "
                        + batchNo + " 的库存行不存在，请核对流水");
            }
            int affected = stockMapper.addQuantity(stock.getId(), returnQty, null, null);
            if (affected == 0) {
                throw new BizException("退回失败：库存行已失效，请重新核对后再回报");
            }
            AmmoRecord returned = AmmoRecord.log(order.getSiteId(), order.getAmmoType(), batchNo,
                    AmmoBizType.RETURN, returnQty, order.getOrderNo());
            insertRecord(returned);
        }
        FireOrderPO fresh = orderMapper.selectById(order.getId());
        return FireOrderPoConverter.toDomain(fresh);
    }

    private void insertRecord(AmmoRecord record) {
        AmmoRecordPO po = AmmoRecordPoConverter.toPo(record);
        po.setId(IdUtil.getSnowflakeNextId());
        recordMapper.insert(po);
        record.setId(po.getId());
    }

    @Override
    public Mono<PageResult<FireOrder>> page(int pageNum, int pageSize,
                                            Long siteId, String status, Long applyId) {
        return this.<PageResult<FireOrder>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<FireOrderPO> wrapper = Wrappers.<FireOrderPO>lambdaQuery()
                        .eq(siteId != null, FireOrderPO::getSiteId, siteId)
                        .eq(applyId != null, FireOrderPO::getApplyId, applyId)
                        .eq(status != null && !status.isBlank(),
                                FireOrderPO::getStatus, status == null ? null : status.trim())
                        .orderByDesc(FireOrderPO::getId);
                List<FireOrderPO> rows = orderMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<FireOrder> content = rows.stream()
                        .map(FireOrderPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
