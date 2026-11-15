package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;

import java.util.List;

/** Only models with an implemented request/response adapter are advertised. */
public final class ImageProviderCatalog {
    public record ImageModel(String providerCode, String id, String name, String description, String apiKind) {}
    public record ImageProvider(String code, String name, String shortName, String description, List<ImageModel> models) {}

    private static final List<ImageProvider> PROVIDERS = List.of(
            new ImageProvider("DASHSCOPE", "阿里云百炼", "阿里", "千问图像 · 擅长文字与复杂场景", List.of(
                    new ImageModel("DASHSCOPE", "qwen-image-2.0-pro", "千问图像 2.0 Pro", "高质量图文生成", "ALI_NATIVE"),
                    new ImageModel("DASHSCOPE", "qwen-image-2.0", "千问图像 2.0", "均衡速度与画质", "ALI_NATIVE"))),
            new ImageProvider("VOLCENGINE", "火山方舟", "火山", "豆包 Seedream · 写实与多风格", List.of(
                    new ImageModel("VOLCENGINE", "doubao-seedream-5-0-pro-260628", "Seedream 5.0 Pro", "高质量写实题图", "VOLC_IMAGE"),
                    new ImageModel("VOLCENGINE", "doubao-seedream-5-0-flash-260915", "Seedream 5.0 Flash", "更快的题图生成", "VOLC_IMAGE"))),
            new ImageProvider("BAIDU_QIANFAN", "百度千帆", "百度", "千帆图像 · 中文提示词友好", List.of(
                    new ImageModel("BAIDU_QIANFAN", "qwen-image", "千帆 Qwen-Image", "通用文生图", "BAIDU_GENERIC"),
                    new ImageModel("BAIDU_QIANFAN", "ernie-image-turbo", "文心图像 Turbo", "快速生成题图", "BAIDU_ERNIE"))),
            new ImageProvider("SILICONFLOW", "硅基流动", "硅基", "聚合平台 · 可使用国内文生图模型", List.of(
                    new ImageModel("SILICONFLOW", "Kwai-Kolors/Kolors", "可图 Kolors", "通用文生图", "SILICON_IMAGE"),
                    new ImageModel("SILICONFLOW", "Qwen/Qwen-Image", "千问 Qwen-Image", "中文图文生成", "SILICON_IMAGE")))
    );

    private ImageProviderCatalog() {}

    public static List<ImageProvider> providers() { return PROVIDERS; }

    public static ImageProvider requireProvider(String code) {
        return PROVIDERS.stream().filter(p -> p.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "不支持的图片模型厂商"));
    }

    public static ImageModel requireModel(String providerCode, String modelId) {
        return requireProvider(providerCode).models().stream().filter(m -> m.id().equals(modelId)).findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "该厂商尚未接通此图片模型"));
    }
}
