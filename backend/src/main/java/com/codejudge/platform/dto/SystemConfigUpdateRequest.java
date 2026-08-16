package com.codejudge.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 系统配置更新请求。
 *
 * <p>AI API Key 为空表示保持原值，不执行覆盖。</p>
 */
public record SystemConfigUpdateRequest(
        @NotNull(message = "评测配置不能为空")
        @Valid JudgeConfigUpdateRequest judge,
        @NotNull(message = "AI配置不能为空")
        @Valid AiConfigUpdateRequest ai,
        @NotNull(message = "限流配置不能为空")
        @Valid LimitConfigUpdateRequest limits) {

    public record JudgeConfigUpdateRequest(
            @NotNull(message = "评测超时不能为空")
            @Min(value = 1000, message = "评测超时不能小于 1000 毫秒")
            @Max(value = 10000, message = "评测超时不能大于 10000 毫秒")
            Integer timeoutMs,
            @NotNull(message = "评测内存不能为空")
            @Min(value = 64, message = "评测内存不能小于 64MB")
            @Max(value = 1024, message = "评测内存不能大于 1024MB")
            Integer memoryMb,
            @NotNull(message = "评测并发数不能为空")
            @Min(value = 1, message = "评测并发数不能小于 1")
            @Max(value = 50, message = "评测并发数不能大于 50")
            Integer maxConcurrent) {
    }

    public record AiConfigUpdateRequest(
            @NotBlank(message = "AI服务商不能为空")
            @Size(max = 50, message = "AI服务商不能超过 50 个字符")
            String provider,
            @NotBlank(message = "AI模型不能为空")
            @Size(max = 100, message = "AI模型不能超过 100 个字符")
            String model,
            @NotBlank(message = "AI接口地址不能为空")
            @Size(max = 512, message = "AI接口地址不能超过 512 个字符")
            String baseUrl,
            @Size(max = 512, message = "AI Key 不能超过 512 个字符")
            String apiKey,
            @NotNull(message = "是否清除 AI Key 不能为空")
            Boolean clearApiKey) {
    }

    public record LimitConfigUpdateRequest(
            @NotNull(message = "登录全局限流不能为空")
            @Min(value = 1, message = "登录全局限流不能小于 1")
            @Max(value = 10000, message = "登录全局限流不能大于 10000")
            Integer loginGlobal,
            @NotNull(message = "登录单用户限流不能为空")
            @Min(value = 1, message = "登录单用户限流不能小于 1")
            @Max(value = 100, message = "登录单用户限流不能大于 100")
            Integer loginPerUser,
            @NotNull(message = "登录单 IP 限流不能为空")
            @Min(value = 1, message = "登录单 IP 限流不能小于 1")
            @Max(value = 500, message = "登录单 IP 限流不能大于 500")
            Integer loginPerIp,
            @NotNull(message = "AI全局限流不能为空")
            @Min(value = 1, message = "AI全局限流不能小于 1")
            @Max(value = 10000, message = "AI全局限流不能大于 10000")
            Integer aiGlobal,
            @NotNull(message = "AI单用户限流不能为空")
            @Min(value = 1, message = "AI单用户限流不能小于 1")
            @Max(value = 500, message = "AI单用户限流不能大于 500")
            Integer aiPerUser,
            @NotNull(message = "AI单 IP 限流不能为空")
            @Min(value = 1, message = "AI单 IP 限流不能小于 1")
            @Max(value = 1000, message = "AI单 IP 限流不能大于 1000")
            Integer aiPerIp,
            @NotNull(message = "提交全局限流不能为空")
            @Min(value = 1, message = "提交全局限流不能小于 1")
            @Max(value = 10000, message = "提交全局限流不能大于 10000")
            Integer submitGlobal,
            @NotNull(message = "提交单用户限流不能为空")
            @Min(value = 1, message = "提交单用户限流不能小于 1")
            @Max(value = 1000, message = "提交单用户限流不能大于 1000")
            Integer submitPerUser,
            @NotNull(message = "提交单 IP 限流不能为空")
            @Min(value = 1, message = "提交单 IP 限流不能小于 1")
            @Max(value = 2000, message = "提交单 IP 限流不能大于 2000")
            Integer submitPerIp) {
    }
}
