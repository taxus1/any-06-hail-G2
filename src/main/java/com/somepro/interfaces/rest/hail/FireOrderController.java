package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.FireOrderAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.FireOrderIssueRequest;
import com.somepro.interfaces.rest.hail.vo.FireOrderReportRequest;
import com.somepro.interfaces.rest.hail.vo.FireOrderVO;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 作业指令接口（用户接口层）。
 *
 * - POST /api/hail/orders            开作业指令（须空域已批；开单即划弹 + 记 OUT 领用）
 * - POST /api/hail/orders/{id}/report 打完回报实际发数（没打完自动退回 + 记 RETURN）
 * - GET  /api/hail/orders/{id}        看单条
 * - GET  /api/hail/orders             分页翻看，可按作业点 / 状态 / 空域申请过滤
 */
@RestController
@RequestMapping("/api/hail/orders")
public class FireOrderController {

    private final FireOrderAppService appService;

    public FireOrderController(FireOrderAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Mono<Result<FireOrderVO>> issue(@Valid @RequestBody FireOrderIssueRequest req) {
        return appService.issue(req.applyId(), req.launcherId(), req.ammoType(),
                        req.batchNo(), req.planRounds())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @PostMapping("/{id}/report")
    public Mono<Result<FireOrderVO>> report(@PathVariable Long id,
                                            @Valid @RequestBody FireOrderReportRequest req) {
        return appService.report(id, req.usedRounds(), req.startTime(), req.endTime())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<FireOrderVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping
    public Mono<Result<HailPageVO<FireOrderVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long applyId) {
        return appService.page(pageNum, pageSize, siteId, status, applyId)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
