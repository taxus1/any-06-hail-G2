package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.AmmoRecordAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.AmmoRecordVO;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 弹药出入库流水接口（用户接口层，只读翻看）。
 * 流水由入库（IN）/ 开单领用（OUT）/ 回报退回（RETURN）用例在同事务里自动记账，这里不手工新增。
 *
 * - GET /api/hail/ammo-records/{id}  看单条流水
 * - GET /api/hail/ammo-records       分页翻看（最新在前），可按作业点 / 弹型 / 批次 / 类型 / 单据号过滤
 */
@RestController
@RequestMapping("/api/hail/ammo-records")
public class AmmoRecordController {

    private final AmmoRecordAppService appService;

    public AmmoRecordController(AmmoRecordAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/{id}")
    public Mono<Result<AmmoRecordVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping
    public Mono<Result<HailPageVO<AmmoRecordVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String ammoType,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String refNo) {
        return appService.page(pageNum, pageSize, siteId, ammoType, batchNo, bizType, refNo)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
