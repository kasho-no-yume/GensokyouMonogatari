package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Optional;

public record CompileResult(Optional<ProgramDefinition> definition, List<CompileDiagnostic> diagnostics) {
    public CompileResult { diagnostics = List.copyOf(diagnostics); }
    public boolean success() { return definition.isPresent() && diagnostics.stream().noneMatch(d -> d.severity() == CompileDiagnostic.Severity.ERROR); }
}
