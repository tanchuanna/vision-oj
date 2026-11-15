package com.spingbootinit.controller;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.bailian.AiModelConfigRequest;
import com.spingbootinit.model.dto.vision.ImageGenerateRequest;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.vision.ImageGenerationVO;
import com.spingbootinit.security.UserPermission;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.ImageModelSettingsService;
import com.spingbootinit.service.ImageProviderCatalog;
import com.spingbootinit.service.UserService;
import com.spingbootinit.service.impl.ImageGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/image-models")
public class ImageModelSettingsController {
    private final ImageModelSettingsService settings;
    private final ImageGenerationService generation;
    private final UserService users;
    private final UserRolePolicy rolePolicy;

    @GetMapping("/catalog")
    public Result<List<ImageProviderCatalog.ImageProvider>> catalog() {
        requireAuthor();
        return Result.success(ImageProviderCatalog.providers());
    }

    @GetMapping("/mine")
    public Result<List<ImageModelSettingsService.SavedImageConfig>> mine() {
        return Result.success(settings.list(requireAuthor()));
    }

    @PutMapping("/mine/{providerCode}")
    public Result<Void> save(@PathVariable String providerCode, @Valid @RequestBody AiModelConfigRequest body) {
        settings.save(requireAuthor(), providerCode, body.getModelId(), body.getApiKey());
        return Result.success(null);
    }

    @DeleteMapping("/mine/{providerCode}")
    public Result<Void> delete(@PathVariable String providerCode) {
        settings.delete(requireAuthor(), providerCode);
        return Result.success(null);
    }

    /** This is a real, potentially billed generation call; the UI must ask for explicit confirmation. */
    @PostMapping("/mine/{providerCode}/test")
    public Result<ImageGenerationVO> test(@PathVariable String providerCode, @Valid @RequestBody AiModelConfigRequest body) {
        long userId = requireAuthor();
        ImageProviderCatalog.requireModel(providerCode, body.getModelId());
        ImageGenerateRequest request = new ImageGenerateRequest();
        request.setSource(providerCode);
        request.setModelId(body.getModelId());
        request.setPrompt("A clear realistic photograph of one red apple on a plain white background, no text");
        return Result.success(generation.generate(request, userId));
    }

    private long requireAuthor() {
        Long userId = UserContext.getCurrentUserId();
        User user = userId == null ? null : users.getById(userId);
        if (user == null) throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        rolePolicy.requirePermission(user.getUserRole(), UserPermission.QUESTION_AUTHOR);
        return userId;
    }
}
