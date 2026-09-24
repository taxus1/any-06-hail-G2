package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 空域驳回请求体：驳回必须写明原因。
 */
public record AirspaceRejectRequest(
        @NotBlank(message = "驳回必须写明原因")
        @Size(max = 255, message = "驳回原因最长 255 位")
        String reason) implements Serializable {
}
