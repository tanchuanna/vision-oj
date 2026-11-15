package com.spingbootinit.model.vo.bailian;

import java.util.List;

public record CodeEditVO(String summary, List<Edit> edits) {
    public record Edit(int startOffset, int endOffset, String oldText, String newText, String reason) {}
}
