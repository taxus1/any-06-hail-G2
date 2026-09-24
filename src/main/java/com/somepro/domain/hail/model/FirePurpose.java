package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;

/**
 * 作业目的（领域枚举）：HAIL 防雹 / RAIN 增雨。
 *
 * 空域申请与作业指令都按这个口径区分用途，库里存的是枚举名字符串（见建表 SQL 注释）。
 */
public enum FirePurpose {

    HAIL,
    RAIN;

    /** 把接口传入的字符串解析成枚举；非法值给明确业务报错，不静默默认。 */
    public static FirePurpose of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("作业目的不能为空：HAIL 防雹 / RAIN 增雨");
        }
        try {
            return FirePurpose.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法作业目的：" + raw + "，只允许 HAIL 防雹 / RAIN 增雨");
        }
    }
}
