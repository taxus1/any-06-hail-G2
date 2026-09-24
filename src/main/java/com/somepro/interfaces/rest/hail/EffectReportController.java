package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.EffectReportAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.EffectReportRequest;
import com.somepro.interfaces.rest.hail.vo.EffectReportVO;
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
 * 作业效果上报接口（用户接口层）。一条指令一份，指令打完（DONE）才能报。
 *
 * - POST /api/hail/effects            上报效果（降雨量 / 冰雹粒径毫米，影响面积平方公里）
 * - GET  /api/hail/effects/{id}       按报告 id 看
 * - GET  /api/hail/effects/by-order?orderId=  按作业指令查其效果报告
 */
@RestController
@RequestMapping("/api/hail/effects")
public class EffectReportController {

    private final EffectReportAppService appService;

    public EffectReportController(EffectReportAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Mono<Result<EffectReportVO>> report(@Valid @RequestBody EffectReportRequest req) {
        return appService.report(req.orderId(), req.reportTime(), req.rainfallMm(),
                        req.hailSizeMm(), req.areaKm2(), req.remark())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<EffectReportVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/by-order")
    public Mono<Result<EffectReportVO>> getByOrder(@RequestParam Long orderId) {
        return appService.getByOrderId(orderId)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }
}
